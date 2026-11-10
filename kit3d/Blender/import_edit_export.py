"""Run in Blender's Python console or Scripting workspace.
Edit WORK_DIR, import the high-res model, inspect / edit mouth bevel,
then export Y-up GLB again. This helper was not executed in Blender here.
"""
import bpy
from pathlib import Path
WORK_DIR = Path.home() / "Downloads" / "Hetian_Woodfish_3D_Android_Kit"
MODEL = WORK_DIR / "models" / "woodfish_jade_high.glb"
OUTPUT = WORK_DIR / "woodfish_edited_in_blender.glb"
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(MODEL))
meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
for obj in meshes:
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    # Optional: edit opening locally with proportional editing / sculpt tools.
# GLB already carries Y-up coordinates; export in glTF +Y up convention.
bpy.ops.export_scene.gltf(filepath=str(OUTPUT), export_format='GLB', export_yup=True)
print('Exported:', OUTPUT)
