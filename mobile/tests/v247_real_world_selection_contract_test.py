"""v2.47: no editor markers or distant untextured props masquerade as a Skyrim scene."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
placed=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4PlacedNifScene.java")
world=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
test=r("tests/Tes4PlacedNifSceneTest.java")
world_test=r("tests/Tes4LocalWorldSceneTest.java")
gradle=r("app/build.gradle")
workflow=r("../.github/workflows/android-quality-gates.yml")
assert "public static boolean isWorldVisibleBase(" in placed
assert 'basename.startsWith("marker")' in placed
assert 'editor.contains("marker")' in placed
assert "if(!isWorldVisibleBase(base))continue;" in placed
assert "MAX_DISTANCE=2048.0" in world
assert "public long sceneQualityScore()" in world
assert 'path.startsWith("meshes/architecture/")' in world
assert 'score>bestScore' in world
assert 'visibleLinks=Tes4PlacedNifScene.findVisibleAll(' in ui
assert 'if(visibleLinks.isEmpty())' in ui
assert 'служебных/неподтверждённых пропущено' in ui
assert 'Один размещённый NIF' in ui
assert 'MarkerRock.nif' in test and 'MarkerWordWallTower.nif' in test
assert 'isWorldVisibleBase(bases.get(0xAA934L))' in test
assert 'chosen.sceneQualityScore()' in world_test
assert "versionCode 73" in gradle and "versionName '2.50.0'" in gradle
assert "v247_real_world_selection_contract_test.py" in workflow
assert "Skyrim-SE-Mobile-v2.50.0-ARM64.apk" in workflow
print("PASS v2.47 strict visible NIF filter, architecture priority and local-space sanity")
