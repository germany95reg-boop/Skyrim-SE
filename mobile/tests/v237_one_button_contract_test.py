"""v2.37 one-folder one-button automatic Skyrim resource analysis."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
java=r("app/src/main/java/dev/skyrimsemobile/launcher/SkyrimAutoDiscovery.java")
test=r("tests/SkyrimAutoDiscoveryTest.java")
jvm=r("ci/run_jvm_suite.sh")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert "SkyrimAutoDiscovery.mainMaster" in ui
assert "SkyrimAutoDiscovery.dataDirectory" in ui
assert "startAutomaticAnalysis()" in ui
assert "inspectNested(esmUri,\"WRLD\",tree,chosenDataId" in ui
assert "scanAllModelArchives(autoTree,autoDataId)" in ui
assert "BsaSessionStore(this).lastDataTree()" in ui
assert "Проверить Skyrim автоматически" in ui
assert "Выбрать папку Skyrim" in ui
assert "Для разработчика" in ui
assert "advanced.setVisibility(View.GONE)" in ui
assert "takePersistableUriPermission" in r("app/src/main/java/dev/skyrimsemobile/launcher/BsaSessionStore.java")
assert "ACTION_OPEN_DOCUMENT_TREE" in ui
assert "readAllBytes" not in ui
assert "SkyrimAutoDiscovery" in jvm
assert "mainMaster(" in java and "static void main(String[] args)" in test
assert "v237_one_button_contract_test.py" in wf
assert "versionCode 69" in gradle and "versionName '2.46.0'" in gradle
print("PASS v2.37 one-folder auto scan, advanced UI and upgrade identity")
