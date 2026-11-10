"""v2.24 physical GPU test uses minimal, accurate viewport without breaking controls.

The v2.23 Samsung SM-S948B on-device report showed 22 PASS, but only 35% free
height. This contract avoids a fake gain: the same compact-HUD height must be
used in BOTH actual camera calculation and copyable real-device telemetry.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda path:(root/path).read_text(encoding="utf-8")
activity=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
camera=read("app/src/main/java/dev/skyrimsemobile/launcher/SceneFraming.java")
jvm=read("tests/SceneFramingTest.java")
gradle=read("app/build.gradle")
manifest=read("app/src/main/AndroidManifest.xml")
workflow=read("../.github/workflows/android-quality-gates.yml")
checks={
 "hide controls only during automated test":'if(deviceAutotest){\n            // The v2.23 hardware report' in activity,
 "actual manual controls created unchanged":'root.addView(controlsScroller,' in activity and 'controlsScroller.addView(panel);' in activity,
 "normal 3D viewer keeps panel":'controlsScroller.setVisibility(View.GONE);' in activity and activity.count('controlsScroller.setVisibility(View.GONE);')==1,
 "visible compact status for test":'diagnosticHud=new TextView(this)' in activity,
 "one line prevents HUD growth":'diagnosticHud.setSingleLine(true)' in activity,
 "live FPS stage visible":'diagnosticHud.setText("Автотест · этап "' in activity,
 "UI controlled by deviceAutotest flag":'deviceAutotest=getIntent().getBooleanExtra(' in activity,
 "small HUD included in actual camera":'w,h,sceneTitle.getHeight(),framingBottomInset()' in activity,
 "Vulkan offset camera preserved":'nativeSetViewportOffset(f.ndcYOffset)' in activity,
 "view framing measures actual overlay":'private int framingBottomInset()' in activity,
 "hidden controls excluded from inset":'controlsScroller.getVisibility()==View.VISIBLE' in activity,
 "device telemetry uses SAME inset":'framingBottomInset())/sceneRoot.getHeight()' in activity,
 "report identifies compact HUD":'output.put("framingViewportMode"' in activity,
 "reported actual lower inset":'output.put("framingBottomInsetPx",framingBottomInset())' in activity,
 "device report keeps camera distance":'report.pass("Авто-вписывание камеры NIF"' in report,
 "device report shows compact overlay":'экран: %s · нижняя панель: %d px' in report,
 "one-touch FPS stage switch intact":'fpsSequence.sample(' in activity and
     'if(measured.cap==60)requestVisualCapture()' in activity,
 "PixelCopy still captures surface":'PixelCopy.request(frameSurface,pixels' in activity,
 "normal-safe renderer intact":'nativeSetDiffuseNormalAtlas' in activity,
 "manual pinch remains":'fitFollowsLayout=false;' in activity,
 "avoids diagnostic preference write":'if (frames > 0 && !error && !deviceAutotest)' in activity,
 "mathematics uses real vertices":'computeGeometry(float[] segments' in camera,
 "compact portrait fixture and projection":'fitsProjected(slim,0f,0.42f,true,1440,2915,240,104)' in jvm,
 "compact landscape fixture":"fitsProjected(slim,0.3f,0.85f,true,2915,1440,130,85)" in jvm,
 "larger free viewport test":'hudFit.visibleFraction>panelFit.visibleFraction+0.25f' in jvm,
 "safe closer fit test":'hudFit.distance<panelFit.distance' in jvm,
 "Android version upgrade":'versionCode 66' in gradle and "versionName '2.43.0'" in gradle,
 "same stable package":'applicationId \'dev.skyrimsemobile.launcher\'' in gradle,
 "read-only no network":'android.permission.INTERNET' not in manifest,
 "CI must run new source guard":'v224_compact_viewport_contract_test.py' in workflow,
}
assert all(checks.values()),[name for name,ok in checks.items() if not ok]
print("PASS v2.24 compact hardware GPU viewport and truthful framing contracts:",len(checks))
