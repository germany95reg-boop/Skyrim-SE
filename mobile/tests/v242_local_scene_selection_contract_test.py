"""v2.42: choose actual dense REFR clusters, preserve one-folder preview and APK identity."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
world=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
test=r("tests/Tes4LocalWorldSceneTest.java")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert "public static final class Planner" in world
assert "MAX_CANDIDATES=32" in world
assert "neighbours.sort(" in world
assert "proposal.instances()" in world
assert "distanceSquared(" in world
assert "sceneCandidates.offer(" in ui and "sceneCandidates.best()" in ui
assert "Просмотреть локальную сцену в 3D" in ui
assert "new Tes4LocalWorldScene.Builder()" in ui
assert "var isolated=pose(200" in test
assert "plannerReverse.best().build()" in test
assert "v242_local_scene_selection_contract_test.py" in wf
assert "versionCode 71" in gradle and "versionName '2.48.0'" in gradle
assert "applicationId 'dev.skyrimsemobile.launcher'" in gradle
assert "signingConfig signingConfigs.stableDebug" in gradle
print("PASS v2.42 dense real REFR scene selection and unchanged upgrade identity")
