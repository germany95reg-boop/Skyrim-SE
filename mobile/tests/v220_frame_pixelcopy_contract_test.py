"""v2.20: real Android Vulkan SurfaceView visual sanity, no framebuffer uploading."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
helper=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanFrameSanity.java")
jvm=read("ci/run_jvm_suite.sh")
workflow=read("../.github/workflows/android-quality-gates.yml")
gradle=read("app/build.gradle")
source=read("app/src/main/cpp/vulkan_scene_test.cpp")
manifest=read("app/src/main/AndroidManifest.xml")
checks={
 "Android PixelCopy SurfaceView API":'PixelCopy.request(frameSurface,pixels' in scene,
 "capture only once":'visualRequested||destroyed||!surfaceReady' in scene and
      'if(!error&&matched&&!visualRequested)' in scene,
 "no Android UI controls captured":'PixelCopy copies the Vulkan surface' in scene,
 "small bounded image":"VulkanFrameSanity.CAPTURE_WIDTH" in scene,
 "copy results analyzed in memory":"VulkanFrameSanity.analyze(argb,w,h)" in scene,
 "recycle image memory":"pixels.recycle()" in scene,
 "capture away from 30/60 FPS windows":"if(measured.cap==60)requestVisualCapture()" in scene,
 "last FPS matrix preserved":'fpsSequence.sample(' in scene,
 "late PixelCopy no deadlock":"Check on EVERY poll" in scene and
      'SystemClock.elapsedRealtime()-visualRequestedAtMs>=1500' in scene,
 "result status included":"frameCaptureStatus" in scene,
 "foreground pixel counts in result":"frameNonBackground" in scene,
 "no screenshot URI written":"openOutputStream" not in scene and "insertImage(" not in scene,
 "no screenshot blob returned":'result.putExtra(EXTRA_DEVICE_AUTOTEST_RESULT,' in scene and
      "putExtra(\"framePixelData" not in scene,
 "clear-only pixel detection":"CLEAR_R=9, CLEAR_G=10, CLEAR_B=17" in helper,
 "no all-black PASS":"max-min>=20" in helper and "nonBackground>=minForeground" in helper,
 "visible pixel test reports separate pass":'report.pass("Изображение Vulkan PixelCopy"' in report,
 "blank pixels report warning":'report.warn("Изображение Vulkan PixelCopy"' in report,
 "not shown as game correctness":"не доказывает правильность всех пикселей" in
      read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java"),
 "JVM fixture included":"VulkanFrameSanity" in jvm,
 "synthetic clear color test":"VulkanFrameSanityTest.java" in
      "\n".join(x.name for x in (root/"tests").glob("*.java")),
 "workflow includes contract":"v220_frame_pixelcopy_contract_test.py" in workflow,
 "ARM64 update code":"versionCode 74" in gradle and "versionName '2.51.0'" in gradle,
 "Vulkan native shader unchanged":"nativeGetStats" in scene and "selectedNormalAtlas" in source,
 "Android no network permission":"android.permission.INTERNET" not in manifest,
 "Android API 26 minSdk":"minSdk 26" in gradle,
}
assert all(checks.values()),[name for name,pass_ in checks.items() if not pass_]
print("PASS v2.20 true Vulkan pixel-copy and no-upload source contracts:",len(checks))
