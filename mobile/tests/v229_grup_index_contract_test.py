"""Skyrim SE v2.29: bounded top-level TES4 GRUP index, without gameplay claims."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
source=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4TopLevelGroupReader.java")
screen=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
test=r("tests/Tes4TopLevelGroupReaderTest.java")
ci=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
workflow=r("../.github/workflows/android-quality-gates.yml")
assert "Tes4PluginHeaderReader.inspect(in)" in source
assert "MAX_TOP_GROUPS=512" in source
assert "GROUP_HEADER_BYTES=24" in source
assert "bytes<GROUP_HEADER_BYTES" in source
assert "groupType!=0" in source
assert "GRUP выходит за границы файла" in source
assert "skipExactly(in,bytes-GROUP_HEADER_BYTES)" in source
assert "scan(InputStream in,long fileBytes)" in source
assert "readAllBytes(" not in source
assert "PICK_GROUPS=710" in screen and "selectGroupFile()" in screen
assert "Tes4TopLevelGroupReader.scan(in,fileSize)" in screen
assert "getContentResolver().openInputStream(uri)" in screen
assert "static void main(String[] args)" in test
assert "Tes4TopLevelGroupReader" in ci
assert "versionCode 56" in gradle and "versionName '2.33.0'" in gradle
assert "v229_grup_index_contract_test.py" in workflow
print("PASS v2.29 top-level GRUP index and read-only SAF integration")
