"""v2.41 multiple nearby REFR in common scale without extra file selection."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
world=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
sampler=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4NestedRecordSampler.java")
matcher=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4MultiBsaModelMatcher.java")
resolver=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4BaseFormResolver.java")
jvm=r("ci/run_jvm_suite.sh")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert "MAX_INSTANCES=8" in world and "MAX_DISTANCE=8192.0" in world
assert "NifSceneComposer.MAX_TRIANGLES" in world
assert "Tes4PlacedNifScene.worldVertex(" in world
assert "new Tes4LocalWorldScene.Builder()" in ui
assert "Tes4PlacedNifScene.findVisibleAll(" in ui
assert "localScene.build()" in ui
assert "Найти MODL во всех BSA" in ui
assert "previewButton.setVisibility(View.GONE)" in ui
assert "MAX_PLACEMENTS=32" in sampler
assert "MAX_TARGET_IDS=32" in resolver
assert "MAX_BASES=32" in matcher
assert "Tes4LocalWorldScene" in jvm
assert "v241_local_scene_contract_test.py" in wf
assert "versionCode 69" in gradle and "versionName '2.46.0'" in gradle
print("PASS v2.41 multiple real REFR local scene, capped distances and one-click pipeline")
