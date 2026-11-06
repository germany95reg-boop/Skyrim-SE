"""v2.18 on-phone diffuse+normal DDS preview must remain an opt-in fallback-safe step."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
packer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeNormalAtlas.java")
diffuse=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
handoff=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
runner=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestRunner.java")
activity=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
shader=read("app/src/main/shaders/textured.frag")
safe=read("app/src/main/shaders/textured_safe.frag")
normal_safe=read("app/src/main/shaders/textured_normal_safe.frag")
shaders=read("ci/compile_shaders.sh")
apk=read("ci/verify_apk.sh")
gradle=read("app/build.gradle")
jvm=read("ci/run_jvm_suite.sh")
ci=read("../.github/workflows/android-quality-gates.yml")
checks={
"normal maps keep slot identity":"diffuse.uvAndSlot[vertexOffset*3+2]" in packer,
"shared diffuse conflict detected":"slotNormals[slot]!=normal" in packer,
"one Vulkan texture sampler":"stackedArgb" in packer and "new int[width*height]" in packer,
"safe neutral normal map":"0xff8080ff" in packer,
"normal atlas maximum 2048":"width>2048||height>2048" in packer,
"count actual normal maps":"uniqueNormals" in packer and "normalParts" in packer,
"fallback keeps diffuse atlas":"NifGeometryTransfer.offerAtlas(sample.scene.lines" in runner,
"read actual shader link normal":"link.normal" in runner,
"same bounded pixel cache":"decodedPixels+nPixels>MAX_SOURCE_PIXELS" in runner,
"normal atlas GPU handoff":"NifGeometryTransfer.offerAtlasNormal" in runner,
"preserve NIF per-shape diffuse mapping":"perShape.get(part.blockIndex)" in diffuse,
"one-shot combined geometry transfer":"static synchronized void offerAtlasNormal" in handoff,
"dedicated JNI composite atlas":"nativeSetDiffuseNormalAtlas" in activity and
    "nativeSetDiffuseNormalAtlas" in native,
"new atlas push constant mode":"diffuseAtlasGrid>0?3:1" in native,
"full GLSL atlas mode 3":"options.atlasMode==3" in shader,
"diffuse-only safe fallback supports mode 3":"options.atlasMode==3" in safe,
"new driver compatibility shader":"flat in float materialSlot" in normal_safe,
"new shader reads normal maps":"dFdx(viewPosition)" in normal_safe,
"safe shader fallback retained":"textureMode=\"atlas-safe\"" in native,
"report GPU-applied map accurately":"normalMapApplied" in native and
    'stats.optBoolean("normalMapApplied",false)' in report,
"no fake PASS on GPU fallback":'report.warn("Normal DDS GPU"' in report,
"new shader compiled and validated":"textured_normal_safe.frag:frag" in shaders,
"new shader packaged":"assets/textured_normal_safe.frag.spv" in apk,
"test actual Java fixtures":"NifCompositeNormalAtlas" in jvm,
"new source contracts invoked":"v218_normal_atlas_contract_test.py" in ci,
"stable signed update":"versionCode 42" in gradle and
    "versionName '2.19.0'" in gradle,
"old physical GPU FPS tests retained":"FpsAutotestSequence" in jvm,
"no emulator job":"android-emulator-ui:" not in ci,
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.18 composite normal DDS / driver-safe native atlas contracts:",len(checks))
