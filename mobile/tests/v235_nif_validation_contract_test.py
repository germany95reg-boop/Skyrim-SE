"""v2.35: in-memory NIF header validation for located TES4 MODL resources."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
verify=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MatchedNifVerifier.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "BsaEntryInspector.inspectProfile(in,entry,archiveFlags)" in verify
assert "MAX_MODELS=8" in verify
assert '!"NIF".equals(header.format)' in r("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
assert "BsaEntryInspector.inspectProfile(" in ui
assert "candidate.flags)" in ui and "candidate.archiveName,profile)" in ui
assert "Повторно проверить содержимое NIF" in ui
assert "readAllBytes" not in ui
assert "Tes4MatchedNifVerifier" in jvm
assert "v235_nif_validation_contract_test.py" in wf
assert "versionCode 70" in gradle and "versionName '2.47.0'" in gradle
print("PASS v2.35 bounded existing BSA-NIF decoder integrated")
