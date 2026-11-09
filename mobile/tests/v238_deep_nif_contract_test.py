"""v2.38: one-click deep NIF block/geometry readiness after LZ4."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
bsa=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
ver=read("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MatchedNifVerifier.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
wf=read("../.github/workflows/android-quality-gates.yml")
assert "public static Profile inspectProfile" in bsa
assert "NifBlockInspector.inspect(d.bytes)" in bsa
assert "NifGeometryDecoder.scan(d.bytes)" in bsa
assert "readDecoded(bsa,entry,archiveFlags)" in bsa
assert "BsaEntryInspector.inspectProfile(in,entry,archiveFlags)" in ver
assert "Таблица NIF:" in ver
assert "Статическая геометрия BSTriShape" in ver
assert "Геометрия ещё не помещена в игровой мир или Vulkan." in ver
assert "NifGeometryDecoder.scan" not in ui
assert "Содержимое NIF: распознано" in ui
assert "Tes4NifStructureProfileTest" in read("tests/Tes4NifStructureProfileTest.java")
assert "v238_deep_nif_contract_test.py" in wf
assert "versionCode 62" in gradle and "versionName '2.39.0'" in gradle
print("PASS v2.38 automatic bounded NIF block inventory and static shape readiness")
