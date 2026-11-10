"""v2.39: actual REFR pose -> NIF preview pipeline, bounded, no new manual categories."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
geo=read("app/src/main/java/dev/skyrimsemobile/launcher/Tes4PlacedNifScene.java")
pose=read("app/src/main/java/dev/skyrimsemobile/launcher/Tes4PlacementSubrecordReader.java")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
wf=read("../.github/workflows/android-quality-gates.yml")
assert "Tes4PlacedNifScene.compose(" in ui
assert "Tes4PlacedNifScene.findVisible(" in ui
assert "NifGeometryTransfer.offer(" in ui
assert "VulkanSceneActivity.class" in ui
assert "Просмотреть найденный объект в 3D" in ui
assert "previewButton.setVisibility(View.GONE)" in ui
assert "Найти MODL во всех BSA" in ui
assert "Мировые координаты XYZ" in geo
assert "NifSceneComposer.MAX_EDGES" in geo
assert "NifSceneComposer.MAX_TRIANGLES" in geo
assert "Math.cos(p.rz)" in geo
assert '"XSCL".equals(key)' in pose
assert "Tes4PlacedNifScene" in jvm
assert "v239_placed_preview_contract_test.py" in wf
assert "versionCode 67" in gradle and "versionName '2.44.0'" in gradle
print("PASS v2.39 linked placed NIF Vulkan preview, one-folder UX and upgrade identity")
