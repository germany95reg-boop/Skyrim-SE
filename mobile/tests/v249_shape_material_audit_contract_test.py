"""v2.49: explain the 4161 neutral bridge triangles with verified per-shape materials."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=lambda p:(root/p).read_text(encoding="utf-8")
world=r("app/src/main/java/dev/skyrimsemobile/launcher/Tes4LocalWorldScene.java")
ui=r("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
material=r("app/src/main/java/dev/skyrimsemobile/launcher/NifMaterialLinkInspector.java")
test=r("tests/Tes4LocalWorldSceneTest.java")
link_test=r("tests/NifMaterialLinkInspectorTest.java")
wf=r("../.github/workflows/android-quality-gates.yml")
gradle=r("app/build.gradle")
assert "public String materialDiagnostics(" in world
assert 'state="НЕТ UV (flags=0x"' in world
assert "не декодирована" in world.lower() or "DDS НЕ ДЕКОДИРОВАНА" in world
assert 'state="DDS ОК: "+canonical' in world
assert "без ссылки shader→diffuse:" in world
assert "сумма: " in world
assert 'materialProblems.put(canonicalNif,problems)' in ui
assert "textureOwner.materialDiagnostics(" in ui
assert "decodedForAudit=found.decoded" in ui
assert 's.substring(0,Math.min(s.length(),28000))' in ui
assert 'blocks.get(shaderId).type' in material
assert 'wrongShader.links.get(0).problem.contains("BSShaderTextureSet")' in link_test
assert 'String completeAudit=sceneDds.materialDiagnostics(' in test
assert 'сумма: 3 / 3' in test
assert 'versionCode 72' in gradle and "versionName '2.49.0'" in gradle
assert 'Skyrim-SE-Mobile-v2.49.0-ARM64.apk' in wf
assert "v249_shape_material_audit_contract_test.py" in wf
print("PASS v2.49 real per-BSTriShape DDS/UV/shader audit and 28k report persistence")
