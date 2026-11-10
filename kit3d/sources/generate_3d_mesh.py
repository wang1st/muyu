#!/usr/bin/env python3
"""Rebuild a watertight, rounded, hollow mokugyo (woodfish) based on its
previous symmetric model. The side slots are identical on front and back.
This is an editable procedural source, not a Blender-dependent render.
"""
import os,time
import numpy as np
from scipy.ndimage import gaussian_filter
from skimage.measure import marching_cubes
import trimesh
import vtk
from vtk.util.numpy_support import numpy_to_vtk, numpy_to_vtkIdTypeArray, vtk_to_numpy
from PIL import Image,ImageDraw,ImageFilter

O=str(__import__('pathlib').Path(__file__).resolve().parents[1] / 'regenerated_outputs')
os.makedirs(O,exist_ok=True)
t0=time.monotonic()
# Geometry is expressed in normalized design units, with Y left/right symmetry.
step=0.0195
x=np.arange(-1.57,1.46,step,dtype=np.float32)
y=np.arange(-1.26,1.27,step,dtype=np.float32)
z=np.arange(-1.22,1.22,step,dtype=np.float32)
X,Y,Z=np.meshgrid(x,y,z,indexing='ij',sparse=False)

def smooth_max(a,b,k):
    h=np.clip(.5+.5*(a-b)/k,0,1)
    return b+(a-b)*h+k*h*(1-h)
def smooth_min(a,b,k):
    return -smooth_max(-a,-b,k)

# Main body deformed smoothly to produce the elongated, low left tip
# of the illustrated reference without attaching an obvious extra sphere.
nose_push=.39*np.exp(-((Z+.57)/.28)**2) / (1+np.exp(9.5*(X+.78)))
outer_q=((X+nose_push-.055)/1.19)**2+(Y/1.072)**2+((Z+.01+.032*(X+.2))/1.025)**2
outer=(1-np.sqrt(outer_q))*1.05
del outer_q,nose_push

# True hollow chamber; substantial wall thickness, including the back side.
inner_q=((X-.025)/.91)**2+(Y/.755)**2+((Z+.005+.037*(X+.2))/.757)**2
outside_cavity=(np.sqrt(inner_q)-1)*.82
# Slightly asymmetrically-curved slot in x/z (not in y): front/back identical.
# Rounded blind tip at x=-.54, sweeping upward toward right tail.
x0=-.535
rise=np.clip(X-x0,0,None)
center=.02+.105*(X-x0)-.017*np.sin(np.pi*np.clip((X-x0)/1.83,0,1))
# Slot is thick near the round inner tip, becoming narrow toward the rim.
width=.073 + .044*np.exp(-((X+.42)/.38)**2)
# Ensure a smoothly rounded slot termination, rather than a knife corner.
left=np.maximum(x0-X,0)
slot_void_sdf=np.sqrt((left*1.10)**2+(Z-center)**2)-width
outside_slot=slot_void_sdf
# Rounded junction between outer shell and lip cut, with no razor edges.
field=smooth_min(outer,outside_cavity,.055)
field=smooth_min(field,outside_slot,.066)
# Very slight anti-alias pre-filter for curvature continuity (no image smoothing).
field=gaussian_filter(field, sigma=1.15)
del X,Y,Z,outer,inner_q,outside_cavity,center,width,left,outside_slot,slot_void_sdf
verts,faces,_,_=marching_cubes(field,level=0,spacing=(step,step,step))
verts+=np.array([x[0],y[0],z[0]],dtype=np.float32)
print('Marching cubes:',len(verts),len(faces),'elapsed',time.monotonic()-t0,flush=True)
# Mesh smoothing only tangentially, to avoid shrinking lips or filling cavity.
p=vtk.vtkPoints(); p.SetData(numpy_to_vtk(verts.astype(np.float32),deep=True))
polys=vtk.vtkCellArray()
facebuf=np.column_stack([np.full(len(faces),3,dtype=np.int64),faces.astype(np.int64)]).ravel()
polys.SetCells(len(faces),numpy_to_vtkIdTypeArray(facebuf,deep=True))
poly=vtk.vtkPolyData(); poly.SetPoints(p); poly.SetPolys(polys)
smooth=vtk.vtkWindowedSincPolyDataFilter();smooth.SetInputData(poly)
smooth.SetNumberOfIterations(90);smooth.SetPassBand(.022)
smooth.FeatureEdgeSmoothingOff();smooth.BoundarySmoothingOff();smooth.NormalizeCoordinatesOn();smooth.Update()
clean=vtk.vtkPolyDataNormals();clean.SetInputConnection(smooth.GetOutputPort());clean.ComputePointNormalsOn();clean.ComputeCellNormalsOff();clean.AutoOrientNormalsOn();clean.ConsistencyOn();clean.SplittingOff();clean.Update()
shaded=clean.GetOutput()
v=vtk_to_numpy(shaded.GetPoints().GetData()).copy()
facearray=vtk_to_numpy(shaded.GetPolys().GetData()).reshape(-1,4)[:,1:].copy()
mesh=trimesh.Trimesh(vertices=v,faces=facearray,process=False)
if mesh.volume<0:mesh.invert()
print('Mesh:',len(mesh.vertices),len(mesh.faces),'watertight',mesh.is_watertight,'volume',mesh.volume,'bounds',mesh.bounds,flush=True)
# Rough matte Hetian white jade: GLB PBR factor, no photographic texture.
from trimesh.visual.material import PBRMaterial
mat=PBRMaterial(name='Matte_Hetian_White_Jade',baseColorFactor=[240,231,211,255],metallicFactor=0,roughnessFactor=.87,doubleSided=False)
mesh.visual=trimesh.visual.TextureVisuals(material=mat)
# Actual opposite interior wall is darker/warmer than the exterior. Use
# vertex albedo; the chamber remains geometrically hollow and inspectable.
V=v.astype(np.float32); xp,yp,zp=V.T
nose=.39*np.exp(-((zp+.57)/.28)**2)/(1+np.exp(9.5*(xp+.78)))
qout=np.sqrt(((xp+nose-.055)/1.19)**2+(yp/1.072)**2+((zp+.01+.032*(xp+.2))/1.025)**2)
qi=np.sqrt(((xp-.025)/.91)**2+(yp/.755)**2+((zp+.005+.037*(xp+.2))/.757)**2)
inner_strength=np.exp(-((qi-1)/.065)**2)*np.clip((.96-qout)/.18,0,1)
zcenter=.02+.105*(xp+.535)-.017*np.sin(np.pi*np.clip((xp+.535)/1.83,0,1))
width=.073+.044*np.exp(-((xp+.42)/.38)**2)
slot_wall=np.exp(-((np.abs(zp-zcenter)-width)/.045)**2)*np.clip((.96-qout)/.22,0,1)
intensity=np.maximum(inner_strength*.54,slot_wall*.26)
base=np.array([255,251,245],dtype=np.float32)
warm_inner=np.array([134,114,82],dtype=np.float32)
colors=np.empty((len(V),4),dtype=np.uint8)
colors[:,:3]=np.clip((1-intensity[:,None])*base+intensity[:,None]*warm_inner,0,255).astype('uint8')
colors[:,3]=255
mesh.visual.vertex_attributes['color']=colors
model_path=f'{O}/Hetian_Woodfish_Rounded_Hollow_Final.glb'
mesh.export(model_path)
mesh.export(f'{O}/Hetian_Woodfish_Rounded_Hollow_Final.obj')
print('GLB written',os.path.getsize(model_path),'bytes',flush=True)
# VTK view from geometry for actual mesh rendering.
normals=vtk.vtkPolyDataNormals();normals.SetInputData(shaded);normals.ComputePointNormalsOn();normals.ComputeCellNormalsOff();normals.SplittingOff();normals.ConsistencyOn();normals.Update()
# Remap natural interior albedo into preview, using the same vertex indices.
colsVTK=numpy_to_vtk(colors[:,:3].copy(), deep=True, array_type=vtk.VTK_UNSIGNED_CHAR)
colsVTK.SetNumberOfComponents(3);colsVTK.SetName('JadeVertexAlbedo')
shaded.GetPointData().SetScalars(colsVTK)
mapper=vtk.vtkPolyDataMapper();mapper.SetInputData(shaded);mapper.SetScalarModeToUsePointData();mapper.SetColorModeToDirectScalars();mapper.ScalarVisibilityOn()

# Render dark/warm actual 3D geometry with broad lighting; no synthesized image.
def render(filename,loc,light=(.10,.16,.14),scale=1.46,N=1050):
    ren=vtk.vtkRenderer();ren.SetBackground(*light);ren.AutomaticLightCreationOff()
    a=vtk.vtkActor();a.SetMapper(mapper)
    prop=a.GetProperty();prop.SetColor(.965,.935,.866);prop.SetAmbient(.43);prop.SetDiffuse(.56);prop.SetSpecular(.014);prop.SetSpecularPower(8);prop.SetInterpolationToPhong()
    ren.AddActor(a)
    for pos,strength in [((-3.7,-4.5,6.2),.86),((3.3,2.2,4.8),.48),((-4.6,-1.6,-3),.19)]:
       L=vtk.vtkLight();L.SetPosition(*pos);L.SetFocalPoint(0,0,0);L.SetIntensity(strength);ren.AddLight(L)
    cam=ren.GetActiveCamera();cam.SetPosition(*loc);cam.SetFocalPoint(0,0,.03);cam.SetViewUp(0,0,1);cam.ParallelProjectionOn();cam.SetParallelScale(scale)
    win=vtk.vtkRenderWindow();win.SetOffScreenRendering(True);win.SetAlphaBitPlanes(1);win.AddRenderer(ren);win.SetSize(N,N);win.SetMultiSamples(8)
    win.Render();ren.ResetCameraClippingRange();win.Render()
    wf=vtk.vtkWindowToImageFilter();wf.SetInput(win);wf.SetInputBufferTypeToRGBA();wf.ReadFrontBufferOff();wf.Update()
    writer=vtk.vtkPNGWriter();writer.SetInputConnection(wf.GetOutputPort());writer.SetFileName(f'{O}/{filename}');writer.Write();win.Finalize()
    print('view',filename,flush=True)
render('front_3d.png',(-.55,-6.5,1.75),scale=1.45)
render('oblique_3d.png',(-3.0,-5.2,2.65),scale=1.48)
render('back_3d.png',(-.55,6.5,1.75),scale=1.45)
render('top_3d.png',(-.75,-3.4,6.4),scale=1.46)
# Persist diagnostics for reproducibility.
with open(f'{O}/mesh_checks.txt','w',encoding='utf8') as f:
 f.write(f'Watertight: {mesh.is_watertight}\nNumber of shells: {len(mesh.split(only_watertight=False))}\nVertices: {len(mesh.vertices)}\nFaces: {len(mesh.faces)}\nVolume: {mesh.volume:.6f}\nBounds: {mesh.bounds.tolist()}\n')
print('ALL_DONE',time.monotonic()-t0,flush=True)
