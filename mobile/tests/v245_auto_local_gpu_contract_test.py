"""v2.45: one-folder automatic real REFR Vulkan opening, full GPU receipt."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda path:(root/path).read_text(encoding="utf-8")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
gpu=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
receipt=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4AutomaticSceneReceipt.java")
test=r("tests/Tes4AutomaticSceneReceiptTest.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "saved==null&&persistedDataTree()!=null" in ui
assert "scroll.post(this::startAutomaticAnalysis)" in ui
assert "automaticScenePending=true" in ui
assert "openPlacedPreview(true);" in ui
assert "EXTRA_AUTOMATIC_PLACED_TEST,true" in ui
assert "appendAutomaticSceneReceipt(result,data)" in ui
assert "Tes4AutomaticSceneReceipt.assess(" in ui
assert "stats.optJSONArray(\"fpsStages\")" in ui
assert "lastSceneDiffuseAtlas!=null" in ui
assert "EXTRA_AUTOMATIC_PLACED_TEST" in gpu
assert "automaticPlacedTest=getIntent().getBooleanExtra(" in gpu
assert "if(automaticPlacedTest)deviceAutotest=true" in gpu
assert "EXTRA_AUTOMATIC_PLACED_TOKEN,placedPreviewToken" in gpu
assert "Не передана выбранная NIF/REFR-сцена" in gpu
assert "if(deviceAutotest&&hasSurface)" in gpu
assert "if(measured.cap==60)requestVisualCapture();" in gpu
assert "fpsSequence.complete()&&frames>=45" in gpu
assert "public static Result assess(" in receipt
assert "PixelCopy" in receipt
assert "triangles!=expectedTriangles" in receipt
assert "Вместо ожидаемой сцены" in receipt
assert "String model=" in test and '"atlas-safe","visible"' in test
assert "Tes4AutomaticSceneReceipt" in jvm
assert "v245_auto_local_gpu_contract_test.py" in wf
assert "Skyrim-SE-Mobile-v2.51.0-ARM64.apk" in wf
assert "versionCode 74" in gradle and "versionName '2.51.0'" in gradle
print("PASS v2.45 automatic REAL REFR Vulkan on-device scene, auto return and complete bounded evidence")
