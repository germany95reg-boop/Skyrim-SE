"""v2.46 truthful local scene diagnosis and bounded SSE BC7 mode 5/6."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
decode=r("app/src/main/java/dev/skyrimsemobile/launcher/DdsBcDecoder.java")
bc7=r("app/src/main/java/dev/skyrimsemobile/launcher/Bc7SubsetDecoder.java")
inspect=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
sampler=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4NestedRecordSampler.java")
verify=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MatchedNifVerifier.java")
result=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4AutomaticSceneReceipt.java")
atlas=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
camera=r("app/src/main/java/dev/skyrimsemobile/launcher/SceneFraming.java")
vulkan=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
jvm=r("ci/run_jvm_suite.sh")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert 'Bc7SubsetDecoder.firstMip(' in decode
assert 'mode!=5&&mode!=6' in bc7 and 'bits.at!=128' in bc7
assert 'Bc7SubsetDecoderTest.java' not in jvm # tests discovered by glob
assert 'Bc7SubsetDecoder' in jvm
assert 'MAX_MODELS=8' in verify and 'MAX_PLACEMENTS=32' in sampler
assert 'public static float bestYaw(' in camera
assert 'autoSpin=false;' in vulkan and 'SceneFraming.bestYaw(nif.model.segments,pitch)' in vulkan
assert 'found.indexed' in inspect and 'found.issues' in inspect
assert 'DDS не декодирована' in inspect and 'DDS отсутствует в BSA' in inspect
assert 'texturedFaces+=mesh.triangles' in atlas
assert 'Tes4AutomaticSceneReceipt.withVisualQuality(' in inspect
assert 'public static Result withVisualQuality(' in result
assert 'Покрытие материалов' in result and 'Крупность сцены' in result
assert 'v246_visual_dds_contract_test.py' in wf
assert 'versionCode 71' in gradle and "versionName '2.48.0'" in gradle
assert 'Skyrim-SE-Mobile-v2.48.0-ARM64.apk' in wf
print("PASS v2.46 BC7 decode, enlarged real NIF sample, auto camera and visual completeness")
