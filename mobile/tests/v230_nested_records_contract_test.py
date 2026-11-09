"""Regression contract: v2.30 bounded nested TES4 header sampling."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
src=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4NestedRecordSampler.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
assert "MAX_HEADERS=1000" in src
assert "MAX_SECTION_BYTES=16L*1024*1024" in src
assert "MAX_DEPTH=24" in src
assert "Tes4PluginHeaderReader.inspect(in)" in src
assert 'if(!"CELL".equals(section)&&!"WRLD".equals(section))' in src
assert "if(bytes>parents.peek()-pos)" in src
assert "recordTypes" in src and "samples" in src
assert "readAllBytes" not in src and "Inflater" not in src
assert "PICK_NESTED_CELL=711" in ui and "PICK_NESTED_WRLD=712" in ui
assert "Tes4NestedRecordSampler.scan(in,section,fileSize)" in ui
assert "Tes4NestedRecordSampler" in jvm
assert "versionCode 60" in gradle and "versionName '2.37.0'" in gradle
assert "v230_nested_records_contract_test.py" in wf
print("PASS v2.30 bounded nested record sample integration")
