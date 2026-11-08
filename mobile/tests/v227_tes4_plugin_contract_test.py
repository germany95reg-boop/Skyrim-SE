"""v2.27: bounded TES4 header reader and read-only SAF integration."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
src=read("app/src/main/java/dev/skyrimsemobile/launcher/Tes4PluginHeaderReader.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
checks={
 "TES4 magic":'"TES4".equals(tag(header,0))' in src,
 "bounded header":'MAX_HEADER_BYTES=4*1024*1024' in src,
 "master references":'"MAST".equals(type)' in src,
 "HEDR checked":'"HEDR".equals(type)' in src,
 "extended length":'"XXXX".equals(type)' in src,
 "Android SAF selector":'selectPlugin()' in ui,
 "UI parser output":'Tes4PluginHeaderReader.inspect(in)' in ui,
 "JVM coverage":'Tes4PluginHeaderReader' in jvm,
 "signed update":"versionCode 55" in gradle and "versionName '2.32.0'" in gradle,
 "CI":'v227_tes4_plugin_contract_test.py' in workflow,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS TES4 v2.27 read-only integration:",len(checks))
