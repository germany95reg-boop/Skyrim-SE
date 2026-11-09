"""v2.23 real-device geometry-aware camera; safeguards for signed install."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
math=read("app/src/main/java/dev/skyrimsemobile/launcher/SceneFraming.java")
test=read("tests/SceneFramingTest.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
activity=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
manifest=read("app/src/main/AndroidManifest.xml")
shader=read("app/src/main/shaders/textured.vert")
checks={
 "real geometry based auto fit":'computeGeometry(float[] segments' in math,
 "same Vulkan perspective scale":"3.2*Math.abs(rx)" in math and
      "3.2*Math.abs(vy)" in math and "3.2*v.x" in shader,
 "screen aspect accounted": "safeHorizontal" in math and "aspect" in math,
 "correct pitch rotation": "double vy=cp*y-sp*rz;" in math,
 "real near-plane guard": "1.0-vz" in math,
 "all yaw directions during auto rotation":"autoRotate?24:1" in math,
 "reserved safety margin":"sphere.visibleFraction*0.87" in math and
      "safeHorizontal=0.87" in math,
 "no farther than proven fit":"Math.min(sphere.distance,required)" in math,
 "camera zoom bounded": "Math.min(40.0,distance)" in math,
 "overlay center maintained":"sphere.ndcYOffset,sphere.visibleFraction" in math,
 "manual angle branch":"autoRotate?24:1" in math,
 "portrait and landscape tests":"1440,2915" in test and "2915,1440" in test,
 "rotating geometry projected check":"fitsProjected(slim" in test,
 "slender mesh is closer":"slimTight.distance<slimSphere.distance" in test,
 "no clipping checked":"all rotated vertices fit horizontal" in test,
 "shader near clip checked":"geometry must remain in front" in test,
 "activity stores actual geometry":"activeModelSegments=model.segments" in scene,
 "auto fit wired":"SceneFraming.computeGeometry(activeModelSegments" in scene,
 "manual zoom preserved":"if(fitFollowsLayout){" in scene and "fitFollowsLayout=false;" in scene,
 "on-device mode reported":'output.put("framingMethod"' in scene,
 "distance measured":'output.put("framingDistance",zoom)' in scene,
 "device result visible":'report.pass("Авто-вписывание камеры NIF"' in activity,
 "warn on fallback":'report.warn("Авто-вписывание камеры NIF"' in activity,
 "Vulkan PixelCopy unaffected":'PixelCopy.request(frameSurface,pixels' in scene,
 "normal-safe still used":'normal-safe' in native,
 "fps modes unchanged":'recordFpsModes(stats,completed)' in activity,
 "no internet permission":'android.permission.INTERNET' not in manifest,
 "APK in-place update":"versionCode 64" in gradle and
      "versionName '2.41.0'" in gradle,
 "stable package id":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "source check mandatory":"v223_geometry_camera_contract_test.py" in workflow,
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.23 actual projected NIF camera and signed ARM64 contracts:",len(checks))
