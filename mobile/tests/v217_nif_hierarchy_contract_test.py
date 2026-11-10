"""v2.17 real Skyrim SE hierarchy transformation integration guards."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
graph=read("app/src/main/java/dev/skyrimsemobile/launcher/NifHierarchyTransforms.java")
table=read("app/src/main/java/dev/skyrimsemobile/launcher/NifBlockInspector.java")
decoder=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java")
composer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifSceneComposer.java")
atlas=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
runner=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestRunner.java")
jni=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
suite=read("ci/run_jvm_suite.sh")
checks={
"retain actual NIF roots":"rootIndices=rootIds.clone()" in table,
"NiNode inheritance documented":"NiObjectNET -> NiAVObject -> NiNode" in graph,
"supported root BSFadeNode":'"BSFadeNode".equals(type)' in graph,
"supported child NiNode":'"NiNode".equals(type)' in graph,
"safe child count":"MAX_CHILDREN=1024" in graph,
"safe tree depth":"MAX_DEPTH=32" in graph,
"safe edges":"MAX_EDGES=8192" in graph,
"guard invalid refs":"target>=blockCount" in graph,
"guard duplicate child refs":"!unique.add(target)" in graph,
"guard parent cycles":"!visiting.add(block)" in graph,
"guard dual parents":"outputs.containsKey(block)" in graph,
"NiAVObject transform starts after extras":"int local=start+16+more;" in graph,
"read full 3x3 parent rotation":"m[k]=scale*f32" in graph,
"validated matrix composition":"matrix[row*3+col]+=m[row*3+k]*child.m[k*3+col]" in graph,
"link from binary NIF scanner":"NifHierarchyTransforms.read(bytes,nif)" in decoder,
"malformed graph explicit fallback":'"ПРЕДУПРЕЖДЕНИЕ: иерархия NIF' in decoder,
"parent transforms stored with parts":"parentTransforms=Collections.unmodifiableMap" in decoder,
"composite geometry receives parent":"parts.parentTransforms.get(part.blockIndex)" in composer,
"full xyz transform":"return parent.apply(x,y,z)" in composer,
"no silent DDS reassignment":"part.mesh==null" in atlas and "perShape.get(part.blockIndex)" in atlas,
"visible on-device hierarchy test":'report.pass("NiNode/BSFadeNode"' in runner,
"hierarchy warns on malformed":"report.warn(\"Иерархия NIF\"" in runner,
"existing FPS caps retained":"fpsSequence.sample(" in jni,
"ABI ARM64 signed upgrade":"versionCode 71" in gradle and "versionName '2.48.0'" in gradle,
"JVM synthetic fixtures":"NifHierarchyTransforms" in suite,
"CI source gate":"v217_nif_hierarchy_contract_test.py" in workflow,
"no emulator":"android-emulator-ui:" not in workflow,
}
assert all(checks.values()), [label for label,passed in checks.items() if not passed]
print("PASS v2.17 real SSE node/shape hierarchy contracts:",len(checks))
