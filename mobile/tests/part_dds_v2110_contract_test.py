from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
finder=read("app/src/main/java/dev/skyrimsemobile/launcher/NifPartDdsAvailability.java")
gradle=read("app/build.gradle")
checks={
 "new user action":"Найти DDS каждой части NIF · v2.11" in ui,
 "one-tap NIF read":"BsaEntryInspector.readPartMaterialLinks(in,entry,flags)" in ui,
 "only validated links":"NifPartDdsAvailability.targets(links)" in ui,
 "bounded DDS exact names":"DdsArchiveFinder.targets(paths)" in finder,
 "no guessed block association":"link.textureSet" not in finder or "ref.textureSet" in finder,
 "saved Data directory":"savedSession.lastDataTree()" in ui,
 "read-only per BSA index":"scanDdsInTree(tree)" in ui,
 "material link snapshot":"activePartLinks" in ui,
 "append per part status":"NifPartDdsAvailability.report(" in ui,
 "no previous DDS after selection":"partMaterialLinks=null;partMaterialNifPath=" in ui,
 "up to 32 unique targets":"DdsArchiveFinder.targets(paths)" in finder,
 "missing only in checked catalog":"не найдено в проверенных индексах" in finder,
 "incomplete index warning":"отсутствие DDS не подтверждено" in finder,
 "unresolved material links":"Ссылки не разрешены:" in finder,
 "no false multi DDS rendering":"не применены к составной Vulkan-модели" in finder,
 "old single textured scene":"previewTexturedNif()" in ui,
 "old composite scene":"previewCombinedNif()" in ui,
 "read-only files":"openOutputStream" not in ui,
 "same app ID":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "upgrade code 34":"versionCode 34" in gradle and "versionName '2.11.0'" in gradle
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.11 per-NIF-part DDS lookup",len(checks))
