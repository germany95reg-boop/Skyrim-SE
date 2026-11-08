"""v2.22: distinguish narrow elongated NIF geometry from a truly small GPU projection.

The user real-device v2.21 report captured 175 / 49,152 non-clear pixels on
Adreno 840, but 21 PASS in all other checks. Pixel occupancy alone cannot
differentiate long thin ropes from a far-away camera. This release records the
projected foreground bounding box without saving/screensharing images.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
get=lambda p:(root/p).read_text(encoding="utf-8")
helper=get("app/src/main/java/dev/skyrimsemobile/launcher/VulkanFrameSanity.java")
test=get("tests/VulkanFrameSanityTest.java")
scene=get("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
activity=get("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
gradle=get("app/build.gradle")
workflow=get("../.github/workflows/android-quality-gates.yml")
manifest=get("app/src/main/AndroidManifest.xml")
report=get("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java")
checks={
 "actual spatial analysis": "int x=i%width,y=i/width" in helper,
 "foreground bounding x and y": "minX=Math.min(minX,x)" in helper and
      "minY=Math.min(minY,y)" in helper,
 "pixel dimensions bounded": "width>512||height>512" in helper,
 "does not treat thin ropes as tiny": "(long)boundWidth*100<=(long)width*18" in helper and
      "(long)boundHeight*100<=(long)height*18" in helper,
 "tiny requires actual visible geometry": "boolean tiny=evidence &&" in helper,
 "clear surface still no evidence": "nonBackground>=minForeground" in helper,
 "real report includes coverage percentage": '%.2f%% кадра' in helper,
 "real report includes pixel bounding dimensions": "границы изображения %d×%d" in helper,
 "synthetic tiny mesh test": "tiny.smallProjectedFootprint" in test,
 "synthetic full-height rope test": "!rope.smallProjectedFootprint" in test,
 "synthetic blank framebuffer test": "clear.foregroundWidth==0" in test,
 "native PixelCopy path preserved": "PixelCopy.request(frameSurface,pixels" in scene,
 "no automatic internet upload": "android.permission.INTERNET" not in manifest,
 "no screenshot storage": "openOutputStream" not in scene,
 "no bitmap leaked": "pixels.recycle()" in scene,
 "small result sent to on-phone report": '"small"' in scene and
      '"small".equals(frameStatus)' in activity,
 "WARN only for compact footprint": 'report.warn("Изображение Vulkan PixelCopy"' in activity,
 "other real FPS modes still present": "recordFpsModes(stats,completed)" in activity,
 "normal GPU retained": 'report.pass("Normal DDS GPU"' in activity,
 "previous user requested footer remains gone": "Отчёт не отправлялся автоматически" not in report,
 "package identity": "applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "stable update code": "versionCode 55" in gradle and "versionName '2.32.0'" in gradle,
 "workflow source check mandatory": "v222_pixel_footprint_contract_test.py" in workflow,
 "prior alpha test mandatory": "v221_dds_alpha_contract_test.py" in workflow,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.22 PixelCopy framing/footprint source contracts:",len(checks))
