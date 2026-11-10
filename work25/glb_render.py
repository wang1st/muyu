#!/usr/bin/env python3
"""GLB → 软件光栅化(法线/深度/明暗) → 分层矢量化 → SVG → PNG
用我们自己的 3D 模型推出 2.5D 画稿（口内深度由真实几何决定）。"""
import json, struct, numpy as np
from PIL import Image

GLB = '/var/minis/workspace/woodfish/app/src/main/assets/models/woodfish_jade_mobile.glb'
SIZE = 1024
YAW, PITCH = -20.0, 12.0
FOV = 38.0

raw = open(GLB, 'rb').read()
magic, ver, length = struct.unpack('<4sII', raw[:12])
off = 12
chunks = {}
while off < length:
    clen, ctype = struct.unpack('<II', raw[off:off+8])
    data = raw[off+8:off+8+clen]
    chunks[ctype] = data
    off += 8 + clen
gltf = json.loads(chunks[0x4E4F534A].decode('utf-8'))
BIN = chunks.get(0x004E4942)

CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def acc(i):
    a = gltf['accessors'][i]
    bv = gltf['bufferViews'][a['bufferView']]
    start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    n = a['count'] * NC[a['type']]
    dt = np.dtype(CT[a['componentType']])
    stride = bv.get('byteStride', 0)
    if stride and stride != dt.itemsize * NC[a['type']]:
        rows = []
        for k in range(a['count']):
            o = start + k * stride
            rows.append(np.frombuffer(BIN, dt, NC[a['type']], o))
        arr = np.stack(rows)
    else:
        arr = np.frombuffer(BIN, dt, n, start)
    return arr.reshape(a['count'], NC[a['type']]) if NC[a['type']] > 1 else arr


def node_matrix(n):
    if 'matrix' in n:
        return np.array(n['matrix'], np.float32).reshape(4, 4).T
    M = np.eye(4, dtype=np.float32)
    if 'scale' in n:
        M = M * np.array([*n['scale'], 1], np.float32)
    if 'rotation' in n:
        x, y, z, w = n['rotation']
        R = np.array([
            [1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w), 0],
            [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w), 0],
            [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y), 0],
            [0, 0, 0, 1]], np.float32)
        M = R @ M
    if 'translation' in n:
        T = np.eye(4, dtype=np.float32); T[:3, 3] = n['translation']
        M = T @ M
    return M


tris, norms = [], []
scene = gltf.get('scenes', [{}])[gltf.get('scene', 0)]
def walk(idx, M):
    n = gltf['nodes'][idx]
    M = M @ node_matrix(n)
    if 'mesh' in n:
        for p in gltf['meshes'][n['mesh']]['primitives']:
            P = acc(p['attributes']['POSITION']).astype(np.float32)
            P = np.hstack([P, np.ones((len(P), 1), np.float32)]) @ M.T
            pos = P[:, :3]
            idxs = acc(p['indices']).reshape(-1, 3)
            # 面积加权顶点法线
            v0, v1, v2 = pos[idxs[:, 0]], pos[idxs[:, 1]], pos[idxs[:, 2]]
            fn = np.cross(v1 - v0, v2 - v0)
            vn = np.zeros_like(pos)
            for c in range(3):
                np.add.at(vn, idxs[:, c], fn)
            ln = np.linalg.norm(vn, axis=1, keepdims=True); ln[ln < 1e-9] = 1
            vn = vn / ln
            tris.append(idxs)
            norms.append(vn)
            globals().setdefault('pos', pos)
    for c in n.get('children', []):
        walk(c, M)

for r in scene.get('nodes', []):
    walk(r, np.eye(4, dtype=np.float32))

pos = np.vstack([pos])
# 归一化（与 App 一致）
mn, mx = pos.min(0), pos.max(0)
ctr = (mn + mx) / 2
ext = float(np.max(mx - mn))
pos = (pos - ctr) / ext * 1.0
tris = np.vstack([t + sum(len(t) for t in tris[:i]) for i, t in enumerate(tris)]) if len(tris) > 1 else tris[0]
nrm = np.vstack(norms)
print('verts', len(pos), 'tris', len(tris))

# 旋转：模型 yaw（绕 Y），相机 pitch
cy, sy = np.cos(np.radians(YAW)), np.sin(np.radians(YAW))
Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float32)
cp, sp = np.cos(np.radians(PITCH)), np.sin(np.radians(PITCH))
Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], np.float32)
R = Rx @ Ry
P = pos @ R.T
Nn = nrm @ R.T

aspect = 1.0
tanY = np.tan(np.radians(FOV / 2))
dist = 0.62 / (tanY * 0.85)
eye = np.array([0, 0, dist], np.float32)
# 视图坐标：z 朝相机（right-handed），屏幕 y 向下
V = P - eye
z = -V[:, 2]                      # 相机前方距离（正）
ndc_x = V[:, 0] / (z * tanY * aspect)
ndc_y = V[:, 1] / (z * tanY)
sx = (ndc_x * 0.5 + 0.5) * SIZE
syy = (0.5 - ndc_y * 0.5) * SIZE

depth = np.full((SIZE, SIZE), 1e9, np.float32)
nrmap = np.zeros((SIZE, SIZE, 3), np.float32)
mask = np.zeros((SIZE, SIZE), bool)

for t in tris:
    i0, i1, i2 = t
    if z[i0] <= 0 or z[i1] <= 0 or z[i2] <= 0:
        continue
    x0, x1, x2 = sx[i0], sx[i1], sx[i2]
    y0, y1, y2 = syy[i0], syy[i1], syy[i2]
    minx, maxx = int(max(0, np.floor(min(x0, x1, x2)))), int(min(SIZE - 1, np.ceil(max(x0, x1, x2))))
    miny, maxy = int(max(0, np.floor(min(y0, y1, y2)))), int(min(SIZE - 1, np.ceil(max(y0, y1, y2))))
    if maxx < minx or maxy < miny:
        continue
    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(den) < 1e-12:
        continue
    xs = np.arange(minx, maxx + 1, dtype=np.float32) + 0.5
    ys = np.arange(miny, maxy + 1, dtype=np.float32) + 0.5
    X, Y = np.meshgrid(xs, ys)
    w0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / den
    w1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / den
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
    if not inside.any():
        continue
    zz = w0 * z[i0] + w1 * z[i1] + w2 * z[i2]
    sub = depth[miny:maxy+1, minx:maxx+1]
    upd = inside & (zz < sub)
    if not upd.any():
        continue
    sub[upd] = zz[upd]
    nsub = nrmap[miny:maxy+1, minx:maxx+1]
    nn = (w0[..., None] * Nn[i0] + w1[..., None] * Nn[i1] + w2[..., None] * Nn[i2])
    ln = np.linalg.norm(nn, axis=2, keepdims=True); ln[ln < 1e-9] = 1
    nn = nn / ln
    nsub[upd] = nn[upd]
    mask[miny:maxy+1, minx:maxx+1] |= upd

np.savez('/var/minis/workspace/woodfish/work25/render.npz', depth=depth, nrmap=nrmap, mask=mask)
print('covered px', int(mask.sum()))

# 快速预览：法线着色
prev = np.zeros((SIZE, SIZE, 3), np.uint8)
pv = (nrmap * 0.5 + 0.5) * 255
prev[mask] = pv[mask].astype(np.uint8)
Image.fromarray(prev).resize((512, 512)).save('/var/minis/workspace/woodfish/work25/normals.png')
np.save('/var/minis/workspace/woodfish/work25/depth.npy', depth)
print('saved')
