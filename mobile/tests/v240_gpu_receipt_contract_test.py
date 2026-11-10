"""v2.40: report GPU frame counters for exact placed Skyrim NIF after preview returns."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
vulkan=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
receipt=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanPlacedPreviewReceipt.java")
jvm=r("ci/run_jvm_suite.sh")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert 'VulkanSceneActivity.EXTRA_PLACED_PREVIEW_TOKEN' in ui
assert 'VulkanSceneActivity.PREF_PLACED_RESULT' in ui
assert 'VulkanPlacedPreviewReceipt.assessWithPixelCopy(' in ui
assert 'public static Result assess(' in receipt
assert 'startActivityForResult(i,PICK_VULKAN_PREVIEW)' in ui
assert 'if(req==PICK_VULKAN_PREVIEW)' in ui
assert 'VulkanPlacedPreviewReceipt' in jvm
assert 'placedPreviewToken' in vulkan
assert 'getSharedPreferences("launcher_state",MODE_PRIVATE)' in vulkan
assert 'PREF_PLACED_RESULT' in vulkan
assert 'frames>0||error' in vulkan
assert 'nifDimensions=nif.details' in vulkan
assert 'счётчики Vulkan' in receipt
assert 'VulkanPlacedPreviewReceiptTest' in r("tests/VulkanPlacedPreviewReceiptTest.java")
assert "v240_gpu_receipt_contract_test.py" in wf
assert "versionCode 74" in gradle and "versionName '2.51.0'" in gradle
print("PASS v2.40 model-matched real Vulkan frame receipt without pixel claims")
