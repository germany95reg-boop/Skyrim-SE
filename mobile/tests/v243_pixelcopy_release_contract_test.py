"""v2.43: PixelCopy only for real placed Vulkan, and private APK release bypasses artifact quota."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
scene=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
receipt=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanPlacedPreviewReceipt.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
tests=r("tests/VulkanPlacedPreviewReceiptTest.java")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
verify=r("ci/verify_apk.sh")
assert "if(!error&&matched&&!visualRequested)" in scene
assert 'proof.put("frameCaptureStatus",visualStatus)' in scene
assert 'proof.put("frameNonBackground",visualResult.nonBackground)' in scene
assert "requestVisualCapture();" in scene
assert "assessWithPixelCopy(" in receipt and "assessWithPixelCopy(" in ui
assert '"blank".equals(status)' in receipt
assert "var blank=VulkanPlacedPreviewReceipt.assessWithPixelCopy(" in tests
assert "var picture=VulkanPlacedPreviewReceipt.assessWithPixelCopy(" in tests
assert "gh release create" in wf and "gh release upload" in wf
assert "actions/upload-artifact@v4" not in wf
assert "if: github.event_name != 'pull_request'" in wf
assert "versionCode 67" in gradle and "versionName '2.44.0'" in gradle
assert "versionCode='67'" in verify
assert "v243_pixelcopy_release_contract_test.py" in wf
print("PASS v2.43 on-device PixelCopy evidence + private GitHub Release APK publishing")
