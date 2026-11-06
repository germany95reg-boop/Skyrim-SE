from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda path:(root/path).read_text(encoding="utf-8")
comp=read("app/src/main/java/dev/skyrimsemobile/launcher/NifSceneComposer.java")
geom=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
entry=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
gradle=read("app/build.gradle")
checks={
 "single scene common frame":"double[] lo=" in comp and "double[] hi=" in comp,
 "relative shape transforms":"localMin[axis]" in comp and "m.localSpan" in comp,
 "global normalize":"normal(world(m,m.surfaceTriangles[i]" in comp,
 "bounded Vulkan edges":"MAX_EDGES=30000" in comp,
 "bounded Vulkan surfaces":"MAX_TRIANGLES=30000" in comp,
 "skip broken shapes visibly":"if(part.mesh==null){skipped++;continue;}" in comp,
 "no all-broken preview":"if(meshes.isEmpty())throw new IOException" in comp,
 "no automatic DDS mapping":"диагностический просмотр БЕЗ DDS" in comp,
 "parent graph not falsely supported":"NiNode/BSFadeNode" in comp,
 "original min and span retained":"this.localMin=localMin.clone();this.localSpan=localSpan" in geom,
 "bounded single BSA read":"BsaEntryInspector.inspectNifParts" in ui,
 "composite control in Android":"Показать все части NIF в Vulkan · v2.9" in ui,
 "UI composite actually used":"NifSceneComposer.combine(parts)" in ui,
 "surface data handed off":"NifGeometryTransfer.offer(scene.lines,scene.triangles" in ui,
 "original single part picker preserved":"selectNifPart()" in ui,
 "diffuse preview preserved":"previewTexturedNif()" in ui,
 "shader controls still present":"nativeSetSpecularStrength" in scene,
 "BSA read only":"openOutputStream" not in ui and "openOutputStream" not in entry,
 "versionCode increment":"versionCode 31" in gradle,
 "versionName increment":"versionName '2.9.0'" in gradle,
 "same Android applicationId":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
}
assert all(checks.values()),[k for k,ok in checks.items() if not ok]
print("PASS v2.9 composite NIF preview:",len(checks),"contract checks")
