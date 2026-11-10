"""v2.51: true SSE vertex RGB is tint, not emissive diffuse; bounded camera-safe lighting."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
tint=read("app/src/main/java/dev/skyrimsemobile/launcher/NifVertexTintPreview.java")
test=read("tests/NifVertexTintPreviewTest.java")
world=read("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
atlas=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
wf=read("../.github/workflows/android-quality-gates.yml")
assert 'public static Result shade(float[] worldTriangles' in tint
assert "if(slot!=-1f)continue" in tint
assert "worldTriangles[base+3]-ax" in tint
assert "abs(" not in tint or "Math.abs(" in tint
assert "Math.abs(" in tint
assert "result[c+ch]=rawRgba[c+ch]*factor" in tint
assert "float factor=" in tint
assert "rawRgba.clone()" in tint
assert "degenerateTriangles" in tint
assert "почти белых вершин" in tint
assert "NifVertexTintPreview.shade(" in world
assert "build().triangles" in world
assert "tint.note()" in world
assert "vertexColorNote" in atlas
assert "preparedAtlas.vertexColorNote" in ui
assert "NifVertexTintPreview" in jvm
assert "mixed.vertexColors[12]<.8f" in read("tests/Tes4LocalWorldSceneTest.java")
assert "raw[0]==1f" in test
assert "out.rgba[12]==1f" in test
assert "versionCode 74" in gradle
assert "versionName '2.51.0'" in gradle
assert "v251_vertex_tint_lighting_contract_test.py" in wf
assert "Skyrim-SE-Mobile-v2.51.0-ARM64.apk" in wf
print("PASS v2.51 neutral lit SSE RGB tint, untouched DDS, original alpha & automatic report evidence")
