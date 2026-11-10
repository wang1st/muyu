package com.minis.muyu;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** GLB 解析（单 mesh、POSITION + COLOR_0 + 索引，运行时算法线），进程内缓存一份。 */
public final class GlbMesh {

    public FloatBuffer verts;   // x,y,z, nx,ny,nz, r,g,b
    public IntBuffer indices;
    public int indexCount;
    public float centerX, centerY, centerZ, radius;

    private static GlbMesh CACHE;

    public static synchronized GlbMesh get(Context c) {
        if (CACHE == null) CACHE = load(c, "models/woodfish_jade_mobile.glb");
        return CACHE;
    }

    public static GlbMesh load(Context c, String assetPath) {
        try {
            byte[] data = readAll(c.getAssets().open(assetPath));
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.getInt() != 0x46546C67) return null;   // "glTF"
            bb.getInt();
            bb.getInt();

            JSONObject js = null;
            ByteBuffer bin = null;
            while (bb.remaining() >= 8) {
                int len = bb.getInt();
                int type = bb.getInt();
                if (len < 0 || len > bb.remaining()) break;
                ByteBuffer chunk = (ByteBuffer) bb.slice().order(ByteOrder.LITTLE_ENDIAN);
                chunk.limit(len);
                if (type == 0x4E4F534A) {
                    js = new JSONObject(new String(chunk.array(),
                            chunk.arrayOffset() + chunk.position(), len, "UTF-8"));
                } else if (type == 0x004E4942) {
                    bin = chunk;
                }
                bb.position(bb.position() + len);
            }
            if (js == null || bin == null) return null;

            JSONObject prim = js.getJSONArray("meshes").getJSONObject(0)
                    .getJSONArray("primitives").getJSONObject(0);
            JSONObject attrs = prim.getJSONObject("attributes");

            float[] pos = readFloats(js, bin, attrs.getInt("POSITION"), 3);
            int n = pos.length / 3;
            float[] col = readColors(js, bin, attrs.optInt("COLOR_0", -1), n);
            int[] idx = readIndices(js, bin, prim.getInt("indices"));
            float[] nrm = computeNormals(pos, n, idx);

            GlbMesh m = new GlbMesh();
            FloatBuffer vb = ByteBuffer.allocateDirect(n * 9 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float x = pos[i*3], y = pos[i*3+1], z = pos[i*3+2];
                vb.put(x).put(y).put(z);
                vb.put(nrm[i*3]).put(nrm[i*3+1]).put(nrm[i*3+2]);
                vb.put(col[i*3]).put(col[i*3+1]).put(col[i*3+2]);
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
            }
            vb.position(0);
            IntBuffer ib = ByteBuffer.allocateDirect(idx.length * 4)
                    .order(ByteOrder.nativeOrder()).asIntBuffer();
            ib.put(idx).position(0);

            m.verts = vb;
            m.indices = ib;
            m.indexCount = idx.length;
            m.centerX = (minX + maxX) / 2f;
            m.centerY = (minY + maxY) / 2f;
            m.centerZ = (minZ + maxZ) / 2f;
            float ext = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            m.radius = ext > 0 ? 1f / ext : 1f;
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        in.close();
        return out.toByteArray();
    }

    private static ByteBuffer view(JSONObject js, ByteBuffer bin, int accIdx) throws Exception {
        JSONObject acc = js.getJSONArray("accessors").getJSONObject(accIdx);
        JSONObject bv = js.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"));
        int off = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0);
        int len = acc.getInt("count") * compSize(acc.getInt("componentType"))
                * comps(acc.getString("type"));
        ByteBuffer s = (ByteBuffer) bin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        s.position(off);
        s.limit(Math.min(bin.limit(), off + Math.max(len, 1)));
        return s.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int comps(String t) {
        if ("VEC4".equals(t)) return 4;
        if ("VEC3".equals(t)) return 3;
        if ("VEC2".equals(t)) return 2;
        return 1;
    }

    private static int compSize(int ct) {
        switch (ct) {
            case 5126: return 4;
            case 5123: return 2;
            case 5125: return 4;
            default: return 1;
        }
    }

    private static float[] readFloats(JSONObject js, ByteBuffer bin, int accIdx, int comp) throws Exception {
        JSONObject acc = js.getJSONArray("accessors").getJSONObject(accIdx);
        int count = acc.getInt("count");
        ByteBuffer v = view(js, bin, accIdx);
        float[] out = new float[count * comp];
        for (int i = 0; i < out.length; i++) out[i] = v.getFloat();
        return out;
    }

    private static float[] readColors(JSONObject js, ByteBuffer bin, int accIdx, int n) throws Exception {
        float[] out = new float[n * 3];
        if (accIdx < 0) { java.util.Arrays.fill(out, 1f); return out; }
        JSONObject acc = js.getJSONArray("accessors").getJSONObject(accIdx);
        ByteBuffer v = view(js, bin, accIdx);
        int comp = comps(acc.getString("type"));
        int ct = acc.getInt("componentType");
        boolean norm = acc.optBoolean("normalized", false);
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < Math.min(comp, 3); c++) {
                float val;
                if (ct == 5126) val = v.getFloat();
                else if (ct == 5121) val = (v.get() & 0xFF) / (norm ? 255f : 1f);
                else val = 1f;
                out[i * 3 + c] = val;
            }
            if (comp == 4) { if (ct == 5126) v.getFloat(); else if (ct == 5121) v.get(); }
        }
        return out;
    }

    private static int[] readIndices(JSONObject js, ByteBuffer bin, int accIdx) throws Exception {
        JSONObject acc = js.getJSONArray("accessors").getJSONObject(accIdx);
        int count = acc.getInt("count");
        int ct = acc.getInt("componentType");
        ByteBuffer v = view(js, bin, accIdx);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            if (ct == 5125) out[i] = v.getInt();
            else if (ct == 5123) out[i] = v.getShort() & 0xFFFF;
            else out[i] = v.get() & 0xFF;
        }
        return out;
    }

    private static float[] computeNormals(float[] pos, int n, int[] idx) {
        float[] nrm = new float[n * 3];
        for (int i = 0; i < idx.length; i += 3) {
            int a = idx[i], b = idx[i+1], c = idx[i+2];
            float ax = pos[a*3], ay = pos[a*3+1], az = pos[a*3+2];
            float e1x = pos[b*3] - ax, e1y = pos[b*3+1] - ay, e1z = pos[b*3+2] - az;
            float e2x = pos[c*3] - ax, e2y = pos[c*3+1] - ay, e2z = pos[c*3+2] - az;
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            nrm[a*3] += nx; nrm[a*3+1] += ny; nrm[a*3+2] += nz;
            nrm[b*3] += nx; nrm[b*3+1] += ny; nrm[b*3+2] += nz;
            nrm[c*3] += nx; nrm[c*3+1] += ny; nrm[c*3+2] += nz;
        }
        for (int i = 0; i < n; i++) {
            double l = Math.sqrt(nrm[i*3]*nrm[i*3] + nrm[i*3+1]*nrm[i*3+1] + nrm[i*3+2]*nrm[i*3+2]);
            if (l > 1e-8) { nrm[i*3] /= l; nrm[i*3+1] /= l; nrm[i*3+2] /= l; }
            else nrm[i*3+1] = 1;
        }
        return nrm;
    }
}
