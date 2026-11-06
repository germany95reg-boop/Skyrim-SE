from pathlib import Path
p=Path(__file__).resolve().parents[1]
r=lambda name:(p/name).read_text(encoding="utf-8")
link=r("app/src/main/java/dev/skyrimsemobile/launcher/NifMaterialLinkInspector.java")
act=r("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
entry=r("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
gradle=r("app/build.gradle")
checks={
"validated block table":"NifBlockInspector.inspect(data)" in link,
"strict SSE version":"index.streamVersion!=100" in link,
"bounded parts":"MAX_PARTS=24" in link,
"explicit shape shader reference":"ref(data,shape,92+4*extra)" in link,
"shader type validated":'expect(blocks,shaderId,"BSLightingShaderProperty")' in link,
"explicit shader set reference":"ref(data,shader,36+4*shaderExtras)" in link,
"set type validated":'expect(blocks,setId,"BSShaderTextureSet")' in link,
"diffuse & normal":"slot.index==0" in link and "slot.index==1" in link,
"unresolved refs shown":"Не разрешено:" in link,
"not claiming texture mapping":"пока открывается без текстур" in link,
"uses bounded BSA":"Decoded d=readDecoded(bsa,entry,archiveFlags)" in entry,
"new action":"Связи материалов частей NIF · v2.10.1" in act,
"read asynchronously":"readPartMaterialLinks(in,entry,flags)" in act,
"no stale result":"if(!isCurrentNif(uri,entry))return;" in act,
"old composite":"previewCombinedNif()" in act,
"old single DDS":"previewTexturedNif()" in act,
"read only":"openOutputStream" not in act and "openOutputStream" not in entry,
"same appId":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
"version increase":"versionCode 33" in gradle and "versionName '2.10.1'" in gradle}
assert all(checks.values()),[n for n,v in checks.items() if not v]
print("PASS v2.10.1 material reference contracts",len(checks))
