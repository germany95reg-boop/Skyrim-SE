"""v2.28: UI must display PackageManager-installed APK version, not legacy feature titles."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
r=lambda path:(p/path).read_text(encoding="utf-8")
helper=r("app/src/main/java/dev/skyrimsemobile/launcher/InstalledAppVersion.java")
main=r("app/src/main/java/dev/skyrimsemobile/launcher/MainActivity.java")
html=r("app/src/main/assets/index.html")
bsa=r("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
gpu=r("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
resources=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
gradle=r("app/build.gradle")
workflow=r("../.github/workflows/android-quality-gates.yml")
tests={
"actual package manager": "getPackageManager().getPackageInfo(context.getPackageName(), 0)" in helper,
"Android P long version": "getLongVersionCode()" in helper,
"fallback older Android": "info.versionCode" in helper,
"launcher passes actual version": 'json.put("installedVersion", InstalledAppVersion.label(this));' in main,
"launcher renders actual version": 'data.installedVersion' in html and 'id="apkVersion"' in html,
"NIF viewer displays installed version": 'InstalledAppVersion.label(this)' in bsa,
"Vulkan viewer displays installed version": 'InstalledAppVersion.label(this)' in gpu,
"inspector displays installed version": 'InstalledAppVersion.label(this)' in resources,
"not stale BSA label": '"Skyrim SE · NIF v2.26"' not in bsa,
"not stale GPU label": '"SKYRIM SE MOBILE · 3D v2.26"' not in gpu,
"ESM navigation from NIF": "openPluginInspector" in bsa and '"open_tes4"' in bsa,
"ESM picker is offered": '"open_tes4"' in resources and 'selectPlugin()' in resources,
"launcher shortcut": "openPluginInspector()" in html and "openPluginInspector()" in main,
"same package and signed upgrade": "versionCode 52" in gradle and "versionName '2.29.0'" in gradle and "applicationId 'dev.skyrimsemobile.launcher'" in gradle,
"contract required by CI": "v228_runtime_version_contract_test.py" in workflow,
}
assert all(tests.values()),[k for k,v in tests.items() if not v]
print("PASS v2.28 installed APK version and direct ESM navigation:",len(tests),"checks")
