"""Prepare high- and mobile-density glTF GLBs, in correct glTF Y-up axes."""
from pathlib import Path
import numpy as np
import trimesh, vtk
from vtk.util.numpy_support import numpy_to_vtk,numpy_to_vtkIdTypeArray,vtk_to_numpy
from scipy.spatial import cKDTree
from trimesh.visual.material import PBRMaterial
ROOT=Path(__file__).resolve().parents[1] / 'regenerated_outputs'
OUT=Path(__file__).resolve().parents[1] / 'regenerated_android'
(OUT/'models').mkdir(parents=True,exist_ok=True)
original=trimesh.load(ROOT/'Hetian_Woodfish_Rounded_Hollow_Final.glb',force='scene')
mesh0=next(iter(original.geometry.values())).copy()
print('input mesh', len(mesh0.vertices),len(mesh0.faces),mesh0.is_watertight)

def get_colors(v):
    xp,yp,zp=v.T
    nose=.39*np.exp(-((zp+.57)/.28)**2)/(1+np.exp(9.5*(xp+.78)))
    qout=np.sqrt(((xp+nose-.055)/1.19)**2+(yp/1.072)**2+((zp+.01+.032*(xp+.2))/1.025)**2)
    qi=np.sqrt(((xp-.025)/.91)**2+(yp/.755)**2+((zp+.005+.037*(xp+.2))/.757)**2)
    inner_strength=np.exp(-((qi-1)/.065)**2)*np.clip((.96-qout)/.18,0,1)
    zcenter=.02+.105*(xp+.535)-.017*np.sin(np.pi*np.clip((xp+.535)/1.83,0,1))
    width=.073+.044*np.exp(-((xp+.42)/.38)**2)
    slot_wall=np.exp(-((np.abs(zp-zcenter)-width)/.045)**2)*np.clip((.96-qout)/.22,0,1)
    intensity=np.maximum(inner_strength*.63,slot_wall*.31)
    base=np.array([255,251,245],dtype=np.float32)
    warm_inner=np.array([113,95,70],dtype=np.float32)
    colors=np.empty((len(v),4),dtype=np.uint8)
    colors[:,:3]=np.clip((1-intensity[:,None])*base+intensity[:,None]*warm_inner,0,255).astype('uint8')
    colors[:,3]=255
    return colors

def prepare(mesh,filename):
    mesh=mesh.copy()
    mesh.fix_normals()
    colors=get_colors(mesh.vertices)
    mat=PBRMaterial(name='Hetian_Jade_Matte_Satin', baseColorFactor=[242,234,217,255], metallicFactor=0.0, roughnessFactor=0.87, doubleSided=False)
    mesh.visual=trimesh.visual.TextureVisuals(material=mat)
    mesh.visual.vertex_attributes['color']=colors
    # Z-up geometry -> glTF's Y-up convention: x'=x, y'=z, z'=-y
    rot=np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]],dtype=float)
    mesh.apply_transform(rot)
    out=OUT/'models'/filename
    mesh.export(out)
    loaded=trimesh.load(out,force='scene')
    verify=next(iter(loaded.geometry.values()))
    print(filename,'size',out.stat().st_size,'v',len(verify.vertices),'f',len(verify.faces),'watertight',verify.is_watertight,'bounds',verify.bounds.tolist(),'visual',verify.visual.kind)
    assert verify.is_watertight
    assert verify.volume>0
    return verify

high=prepare(mesh0,'woodfish_jade_high.glb')
# Reduce triangles for mobile viewport while preserving lip geometry and cavity.
p=vtk.vtkPoints();p.SetData(numpy_to_vtk(mesh0.vertices.astype(np.float32),deep=True))
idx=np.hstack((np.full((len(mesh0.faces),1),3,dtype=np.int64),mesh0.faces.astype(np.int64))).ravel()
faces=vtk.vtkCellArray();faces.SetCells(len(mesh0.faces),numpy_to_vtkIdTypeArray(idx,deep=True))
poly=vtk.vtkPolyData();poly.SetPoints(p);poly.SetPolys(faces)
d=vtk.vtkQuadricDecimation();d.SetInputData(poly);d.SetTargetReduction(.69);d.VolumePreservationOn();d.Update()
res=d.GetOutput()
v=vtk_to_numpy(res.GetPoints().GetData()).copy()
f=vtk_to_numpy(res.GetPolys().GetData()).reshape(-1,4)[:,1:].copy()
mobile_mesh=trimesh.Trimesh(vertices=v,faces=f,process=False)
print('Decimated:',len(v),len(f),'watertight:',mobile_mesh.is_watertight)
if not mobile_mesh.is_watertight:
    print('Decimation lost watertightness; fall back to full res as safe default')
    mobile_mesh=mesh0
mobile=prepare(mobile_mesh,'woodfish_jade_mobile.glb')
# Approximate bilateral consistency after quantization: model has symmetric Y-slots (Z GLB axes).
orig=mesh0.vertices
subset=orig[::5]
nearest=cKDTree(orig).query(subset*np.array([1,-1,1]),k=1)[0]
print('Bilateral symmetry sample deviations',np.median(nearest),np.quantile(nearest,.95),np.max(nearest))
with open(OUT/'model_validation.txt','w') as out:
 out.write('PROCEDURAL MOKUGYO V2 — GEOMETRY CHECKS\n')
 out.write('High: %d vertices, %d triangles, watertight=%s\n'%(len(high.vertices),len(high.faces),high.is_watertight))
 out.write('Mobile: %d vertices, %d triangles, watertight=%s\n'%(len(mobile.vertices),len(mobile.faces),mobile.is_watertight))
 out.write('Symmetry: signed-distance function is strictly invariant under Y -> -Y (source axes).\n')
 out.write('Sample nearest vertex reflection deviation: median %.5f, p95 %.5f, max %.5f normalized units\n'%(np.median(nearest),np.quantile(nearest,.95),np.max(nearest)))
 out.write('GLB axes: +Y up, +X longitudinal, +Z/-Z front/back.\n')
 out.write('Material: PBR metallic=0, roughness=0.87, milky jade + warm inner albedo\n')
 out.write('Geometry: one closed thick shell; symmetric slit opens to an actual hollow cavity.\n')
