"""v2.44: selected REFR material links + bounded local scene DDS atlas."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
scene=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
inspector=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
transfer=r("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
test=r("tests/Tes4LocalWorldSceneTest.java")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
verify=r("ci/verify_apk.sh")
assert 'public List<String> materialPaths(' in scene
assert 'public NifCompositeDdsAtlas.Result diffuseAtlas(' in scene
assert 'DdsArchiveFinder.normalizeDds' in scene
assert 'tiles.add(null)' in scene
assert 'int[] pixels=new int[side*side]' in scene
assert 'part.mesh.surfaceUVs.length!=(long)part.mesh.triangles*6' in scene
assert 'private LocalSceneDds loadLocalSceneDds(' in inspector
assert 'DdsArchiveFinder.find(' in inspector
assert 'BsaDdsPixelReader.preview(' in inspector
assert 'totalPixels+pixels>4L*1024*1024' in inspector
assert 'checked>=24' in inspector
assert 'sceneCandidates.best()' in inspector
assert 'readPartMaterialLinks(' in inspector
assert 'NifGeometryTransfer.offerAtlas(preview.lines,preview.triangles,' in inspector
assert 'NifGeometryTransfer.offer(preview.lines,preview.triangles,' in inspector
assert 'lastSceneDiffuseAtlas=readyAtlas' in inspector
assert 'offerAtlas(ObjWireParser.Model model' in transfer
assert 'var diffuse=sceneDds.diffuseAtlas(materialPaths,pixels);' in test
assert 'diffuse.neutralParts==1' in test
assert 'sceneDds.build().triangleCount==3' in test
assert 'versionCode 74' in gradle and "versionName '2.51.0'" in gradle
assert "versionCode='74'" in verify
assert "v244_local_scene_dds_contract_test.py" in wf
assert "Skyrim-SE-Mobile-v2.51.0-ARM64.apk" in wf
print("PASS v2.44 local-scene real DDS/UV and neutral fallback contract")
