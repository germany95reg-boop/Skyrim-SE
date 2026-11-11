from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
activity=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
resolver=read("app/src/main/java/dev/skyrimsemobile/launcher/NifPartTextureSelection.java")
transfer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
gradle=read("app/build.gradle")
checks={
    # This historical v2.12 contract must also pass for later releases.
    "versionCode >= 35": any(int(v) >= 35 for v in re.findall(r"\\bversionCode\\s+(\\d+)\\b", gradle)),
    "versionName >= 2.12": any(tuple(map(int, v.split("."))) >= (2, 12, 0) for v in re.findall(r"\\bversionName\\s+['\\\"](\\d+\\.\\d+\\.\\d+)['\\\"]", gradle)),
    "same Android appId":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
    "persisted signing config":"signingConfigs.stableDebug" in gradle,
    "new picker control":"Показать выбранную часть NIF с её DDS · v2.12" in activity,
    "part chooser":"this::previewTexturedPart" in activity and ".setItems(choices" in activity,
    "exact shape indexing":"link.shape!=shapeIndex" in resolver,
    "validated link only":"!link.resolved()" in resolver,
    "no guessed texture set":"link.diffuse" in resolver and "DdsArchiveFinder.normalizeDds(link.diffuse)" in resolver,
    "select the actual part":"NifPartTextureSelection.require(links,selected.blockIndex)" in activity,
    "read-only BSA records":"BsaDdsPixelReader.preview(in,entry,hit.flags)" in activity,
    "bounded in-memory UV handoff":"NifGeometryTransfer.offerTextured(mesh.lines,mesh.surfaceTriangles" in activity,
    "not global shape texture":"findLocatedDds(matches,link.diffuse)" in activity,
    "old textured path retained":"private void previewTexturedNif()" in activity,
    "composite path retained":"NifSceneComposer.combine(parts)" in activity,
    "no BSA writes":"openOutputStream" not in activity,
    "same renderer for textured previews":"nativeSetTexture(uv,pixels,textureW,textureH)" in read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java"),
    "valid normal optional":"catch(Exception optional)" in activity,
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.12 selected NIF part DDS contracts:",len(checks))
