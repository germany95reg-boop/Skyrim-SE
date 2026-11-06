from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
helper=read("app/src/main/java/dev/skyrimsemobile/launcher/NifCatalogSelection.java")
gradle=read("app/build.gradle")
checks={
  "tap paths":"new ClickableSpan()" in ui and "chooseNif(entry)" in ui,
  "clickable text":"LinkMovementMethod.getInstance()" in ui,
  "chooser dialog":"Выбрать NIF из списка · v2.10" in ui and ".setItems(labels,(dialog,index)->chooseNif(options.get(index)))" in ui,
  "NIF only chooser":"filter.getText().toString(),true,60" in ui,
  "remember exact choice":"selectedNifPath=entry.path;selectedNifArchive=currentUri" in ui,
  "archive scope":"selectedNifArchive.equals(currentUri)" in ui,
  "same loaded catalog":"catalog.entries.contains(entry)" in ui,
  "manual edit resets":"selectedNifPath=\"\";selectedNifArchive=null;" in ui,
  "DDS cache cleared":"clearMaterialSelection();" in ui,
  "cache actually clears":"private void clearMaterialSelection(){\n        materialDdsPaths=Collections.emptyList();" in ui,
  "no recursive clear":"private void clearMaterialSelection(){\n        clearMaterialSelection();" not in ui,
  "all NIF operations use selection":"selectedNifMatches()" in ui,
  "reject stale background NIF":"isCurrentNif(uri,entry)" in ui,
  "legacy search":"return catalog.find(query,true,1);" in helper,
  "stale selection no fallback":"return Collections.emptyList();" in helper,
  "only NIFs selectable":"entry.nif()&&selectedPath.equals(entry.path)" in helper,
  "old part picker":"selectNifPart()" in ui,
  "old composite":"previewCombinedNif()" in ui,
  "old DDS":"previewTexturedNif()" in ui,
  "same app":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
  "v2.10":"versionCode 32" in gradle and "versionName '2.10.0'" in gradle
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.10 exact NIF selection and invalidation:",len(checks),"checks")
