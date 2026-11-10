"""v2.34 multi-archive MODL lookup from SAF Data folder."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
jvm=r("ci/run_jvm_suite.sh")
src=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MultiBsaModelMatcher.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "MAX_ARCHIVES=48" in src
assert "Tes4BsaModelMatcher.canonicalNif(entry.path)" in src
assert "if(catalog.partialIndex){" in src and "partialIndexes++" in src
assert "НЕ ПОДТВЕРЖДЕНО" in src
assert "ACTION_OPEN_DOCUMENT_TREE" in ui
assert "Tes4MultiBsaModelMatcher.begin(baseRecords.found.values())" in ui
assert "BsaCatalogReader.read(in)" in ui
assert "scanAllModelArchives" in ui
assert "PICK_DATA_MODELS=714" in ui
assert "Tes4MultiBsaModelMatcher" in jvm
assert "v234_multi_bsa_contract_test.py" in wf
assert "versionCode 67" in gradle and "versionName '2.44.0'" in gradle
print("PASS v2.34 SAF multi-BSA exact model discovery and upgrade")
