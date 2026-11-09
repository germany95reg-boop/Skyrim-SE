"""v2.35: in-memory NIF header validation for located TES4 MODL resources."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
verify=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MatchedNifVerifier.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "BsaEntryInspector.inspect(in,entry,archiveFlags)" in verify
assert "MAX_MODELS=3" in verify
assert '!"NIF".equals(parsed.header.format)' in verify
assert "Tes4MatchedNifVerifier.verify(in,candidate.entry," in ui
assert "candidate.flags,candidate.archiveName)" in ui
assert "Повторно проверить содержимое NIF" in ui
assert "readAllBytes" not in ui
assert "Tes4MatchedNifVerifier" in jvm
assert "v235_nif_validation_contract_test.py" in wf
assert "versionCode 61" in gradle and "versionName '2.38.0'" in gradle
print("PASS v2.35 bounded existing BSA-NIF decoder integrated")
