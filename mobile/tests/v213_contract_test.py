from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
transfer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
atlas=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
vert=read("app/src/main/shaders/textured.vert")
frag=read("app/src/main/shaders/textured.frag")
gradle=read("app/build.gradle")
checks={
 "versionCode >=36":("versionCode 36" in gradle or "versionCode 37" in gradle or "versionCode 38" in gradle or "versionCode 39" in gradle or "versionCode 40" in gradle or "versionCode 41" in gradle or "versionCode 42" in gradle or "versionCode 43" in gradle or "versionCode 44" in gradle or "versionCode 73" in gradle),
 "versionName >=2.13":("versionName '2.13.0'" in gradle or "versionName '2.14.0'" in gradle or "versionName '2.15.0'" in gradle or "versionName '2.16.0'" in gradle or "versionName '2.17.0'" in gradle or "versionName '2.18.0'" in gradle or "versionName '2.19.0'" in gradle or "versionName '2.20.0'" in gradle or "versionName '2.21.0'" in gradle or "versionName '2.50.0'" in gradle),
 "same Android package":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "persistent signed upgrades":"signingConfig signingConfigs.stableDebug" in gradle,
 "composite DDS button":"2 · Открыть ВСЮ NIF с DDS (все части)" in ui,
 "composite scene geometry":"NifSceneComposer.combine(parts)" in ui,
 "exact shape material links":"NifPartTextureSelection.require(links,part.blockIndex)" in ui,
 "no DDS guess":"findLocatedDds(matches,link.diffuse)" in ui,
 "DDS read-only":"BsaDdsPixelReader.preview(in,entry,hit.flags)" in ui,
 "bounded decoder memory":"8L*1024*1024" in ui,
 "fallback grey tile":"NEUTRAL=0xff73777e" in atlas,
 "atlas grid <=4":"MAX_GRID=4" in atlas,
 "per-vertex material slot":"uvAndSlot" in atlas and "packed[p+2]=slot;" in atlas,
 "retained global normalization":"NifSceneComposer.combine(parts)" in ui,
 "native atlas JNI":"nativeSetDiffuseAtlas" in scene and "nativeSetDiffuseAtlas" in native,
 "native bounds guard":"selectedDiffuseAtlasGrid=grid" in native and "width%grid!=0" in native,
 "native material slots validated":"std::floor(id)!=id" in native,
 "native 6 floats vertex":"uvBinding.stride=10*sizeof(float)" in native,
 "native tile vertex attribute":"uvAttributes[2].location=2" in native,
 "push constants match":"texRange.size=56" in native and "sizeof(TexturedParameters)==56" in native,
 "GLSL push constant grid":"layout(offset=52) int diffuseAtlasGrid" in frag,
 "GLSL flat shader input":"flat in int materialSlot" in frag and "flat out int materialSlot" in vert,
 "GLSL repeats UV inside tile":("diffuseUV=mix(tileMin,tileMax,tiled)" in frag or
                                "diffuseUV=mix(tileMin+inset,tileMax-inset,tiled)" in frag),
 "normal DDS resize":"DdsNormalResampler.match(candidate,image.width,image.height)" in ui,
 "original single textured preserved":"private void previewTexturedNif()" in ui,
 "composite geometry-only preserved":"private void previewCombinedNif()" in ui,
 "strictly read-only":"openOutputStream" not in ui,
}
assert all(checks.values()),[name for name,p in checks.items() if not p]
print("PASS v2.13 multi-material Vulkan source contracts:",len(checks))
