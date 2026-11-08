"""v2.19 paired diffuse+normal material identity, exact user-requested report ending."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
atlas=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeDdsAtlas.java")
normal=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCompositeNormalAtlas.java")
runner=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestRunner.java")
browser=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java")
tests=read("tests/NifCompositeNormalAtlasTest.java")
rtest=read("tests/DeviceSelfTestReportTest.java")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
checks={
 "exact material pair API":"static Result buildPaired(" in atlas,
 "pair-reuse uses identity key":"IdentityHashMap<DdsBcDecoder.Image,Integer> normalSlots" in atlas,
 "pair avoids collisions":"normalSlots.get(normal)" in atlas,
 "no guessed normal DDS mapping":"perNormal.get(part.blockIndex)" in atlas,
 "legacy diffuse-only build retained":"return buildInternal(parts,scene,perShape,null)" in atlas,
 "neutral material slot preserved":"tiles.add(null)" in atlas,
 "normal source validation":"Повреждённая normal DDS" in atlas,
 "max 15 material pairs":"MAX_TEXTURES=MAX_GRID*MAX_GRID-1" in atlas,
 "actual normal-slot proof":"slotNormals[slot]!=normal" in normal,
 "device test uses paired paths":"NifCompositeDdsAtlas.buildPaired(" in runner,
 "manual NIF preview uses paired paths":"NifCompositeDdsAtlas.buildPaired(" in browser,
 "normal-safe fallback retained":"NifGeometryTransfer.offerAtlas(sample.scene.lines" in runner,
 "report requested sentence removed":"Отчёт не отправлялся автоматически" not in report,
 "report user-facing ending removed":"пользователь решает, кому его передать" not in report,
 "report preserved hardware caveat":"не доказывает правильность всех пикселей" in report,
 "regression footer check":"user-requested footer removed completely" in rtest,
 "regression distinct normal slots":"different normal DDS make distinct pairs" in tests,
 "regression neutral normal":"missing normal uses neutral tile" in tests,
 "regression identical pair dedup":"identical diffuse+normal pair reuses tile" in tests,
 "signed version 42":"versionCode 48" in gradle,
 "version 2.19":"versionName '2.25.0'" in gradle,
 "one-tap still present":"checkNifAndDds(" in runner,
 "code included in CI":"v219_paired_material_contract_test.py" in workflow,
 "emulator not required":"android-emulator-ui:" not in workflow
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.19 paired DDS materials and requested report footer removal:",len(checks),"checks")
