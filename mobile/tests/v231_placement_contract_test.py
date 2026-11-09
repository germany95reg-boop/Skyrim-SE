"""v2.31 read-only NAME + DATA sampling from nested placed references."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
src=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4PlacementSubrecordReader.java")
nested=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4NestedRecordSampler.java")
test=r("tests/Tes4PlacementSubrecordReaderTest.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
workflow=r("../.github/workflows/android-quality-gates.yml")
checks={
"record payload capped":'MAX_BODY=65536' in src,
"only REFR/ACHR":'"REFR".equals(recordType)&&!"ACHR".equals(recordType)' in src,
"NAME 4 bytes":'"NAME".equals(key)' in src and 'bytes!=4' in src,
"DATA 24 bytes":'"DATA".equals(key)' in src and 'bytes!=24' in src,
"extended fields validated":'"XXXX".equals(key)' in src,
"finite pose":'Float.isFinite' in src,
"sample only uncompressed records":'flags&0x00040000L' in nested,
"first placement sample reported":'Первые размещения (NAME + DATA)' in nested,
"no payload rewrite":'openOutputStream' not in nested,
"test fixtures":'static void main(String[] a)' in test,
"JVM CI includes parser":'Tes4PlacementSubrecordReader' in jvm,
"upgrade identity":"versionCode 59" in gradle and "versionName '2.36.0'" in gradle,
"CI contract":'v231_placement_contract_test.py' in workflow,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.31 placed-reference read-only parser integration:",len(checks),"checks")
