"""v2.33 exact-match TES4 MODL to chosen BSA indexed NIF paths."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
src=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4BsaModelMatcher.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
tests=r("tests/Tes4BsaModelMatcherTest.java")
jvm=r("ci/run_jvm_suite.sh")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert 'canonicalNif(' in src and 'meshes/' in src
assert 'entry.nif()' in src and 'nifIndex.get(canonical)' in src
assert 'catalogue.partialIndex' in src and 'НЕ ПОДТВЕРЖДЕНО' in src
assert 'openOutputStream' not in src
assert 'PICK_MODEL_BSA=713' in ui
assert 'new BsaSessionStore(this).lastArchive()' in ui
assert 'Tes4BsaModelMatcher.match(' in ui and 'baseRecords.found.values(),catalogue,archiveName)' in ui
assert 'BsaCatalogReader.read(in)' in ui
assert 'static void main(String[] args)' in tests
assert 'Tes4BsaModelMatcher' in jvm
assert "versionCode 73" in gradle and "versionName '2.50.0'" in gradle
assert "v233_bsa_model_match_contract_test.py" in wf
print("PASS v2.33 model BSA exact match UI and upgrade contract")
