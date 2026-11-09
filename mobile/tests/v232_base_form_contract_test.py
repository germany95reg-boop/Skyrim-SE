"""v2.32 bounded base FormID linking and NIF path discovery, not playable Skyrim."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
src=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4BaseFormResolver.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=r("ci/run_jvm_suite.sh")
gradle=r("app/build.gradle")
wf=r("../.github/workflows/android-quality-gates.yml")
checks={
"target limited":'MAX_TARGET_IDS=16' in src,
"record limit":'MAX_HEADERS=60000' in src,
"byte limit":'MAX_SCANNED_BYTES=24L*1024*1024' in src,
"payload limit":'MAX_RECORD_BODY=65536' in src,
"stops before world":'if("CELL".equals(groupType)||"WRLD".equals(groupType))break;' in src,
"TES4 validation":'Tes4PluginHeaderReader.inspect(in)' in src,
"EDID subrecord":'"EDID".equals(tag)' in src,
"model path":'"MODL".equals(tag)' in src,
"compressed guard":'(flags&0x00040000L)!=0' in src,
"no writes":'openOutputStream' not in src,
"no whole game load":'readAllBytes' not in src,
"UI reopens selected plugin":'Tes4BaseFormResolver.resolve(baseIn,result.placements,fileSize)' in ui,
"JVM test":'Tes4BaseFormResolver' in jvm,
"package version":"versionCode 60" in gradle and "versionName '2.37.0'" in gradle,
"CI contract":'v232_base_form_contract_test.py' in wf,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.32 base record link:",len(checks),"checks")
