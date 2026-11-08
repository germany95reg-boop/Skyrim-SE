"""v2.15: on-phone automation must be reachable and safe, with no emulator."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
get=lambda p:(r/p).read_text(encoding="utf-8")
activity=get("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
runner=get("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestRunner.java")
report=get("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java")
launcher=get("app/src/main/java/dev/skyrimsemobile/launcher/MainActivity.java")
scene=get("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
bsa=get("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
html=get("app/src/main/assets/index.html")
manifest=get("app/src/main/AndroidManifest.xml")
gradle=get("app/build.gradle")
ci=get("../.github/workflows/android-quality-gates.yml")
checks={
"upgradeable APK versionCode 53":"versionCode 53" in gradle,
"installed versionName":"versionName '2.30.0'" in gradle,
"stable app package":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
"stable signing":"signingConfig signingConfigs.stableDebug" in gradle,
"launcher WebView bridge":'public void openDeviceSelfTest()' in launcher,
"prominent launcher button":'id="deviceSelfTestButton"' in html,
"on-phone JS navigation":'AndroidBridge.openDeviceSelfTest()' in html,
"BSA viewer one-tap button":'Запустить полный автотест на телефоне' in bsa,
"non-exported on-device test":'android:name=".DeviceSelfTestActivity" android:exported="false"' in manifest,
"auto-start once":"if(saved==null)start();" in activity,
"repeatable on-phone test":"startButton.setOnClickListener(v->start());" in activity,
"clipboard output":"ClipboardManager" in activity and "setPrimaryClip" in activity,
"explicit share action":"Intent.ACTION_SEND" in activity,
"no network upload":"android.permission.INTERNET" not in manifest and
    "HttpURLConnection" not in runner and "https://" not in runner,
"saved BSA grant":"session.lastArchive()" in runner,
"saved Data tree grant":"session.lastDataTree()" in runner,
"read-only BSA":"BsaCatalogReader.read(in)" in runner and
    "openOutputStream" not in runner,
"real BSA/NIF shape parsing":"BsaEntryInspector.inspectNifParts" in runner,
"exact shader material links":"BsaEntryInspector.readPartMaterialLinks" in runner,
"diffuse DDS decoder":"BsaDdsPixelReader.preview" in runner,
"safe composite atlas":"NifCompositeDdsAtlas.build" in runner,
"real Vulkan surface intent":"EXTRA_DEVICE_AUTOTEST" in activity and
    "EXTRA_DEVICE_AUTOTEST" in scene,
"verified actual GPU frames":"frames>=45" in scene and
    "SystemClock.elapsedRealtime()" in scene,
"GPU test timeout":"deviceAutotestTimeout,FpsAutotestSequence.MAX_TOTAL_MS" in scene,
"GPU result delivered to UI":"setResult(success?RESULT_OK:RESULT_CANCELED,result)" in scene,
"cancel on Activity destruction":"runner.cancel()" in activity,
"explicit PASS WARN FAIL SKIP":all(s in report for s in
    ["void pass(","void warn(","void fail(","void skip("]),
"source pixel bound":"MAX_SOURCE_PIXELS=8L*1024*1024" in runner,
"bounded archive count":"MAX_ARCHIVES=48" in runner,
"no automatic emulator CI":"android-emulator-ui:" not in ci,
"on-phone source contract CI":"v215_device_autotest_contract_test.py" in ci,
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.15 on-device one-tap auto-test source contracts:",len(checks))
