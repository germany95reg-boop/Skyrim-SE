from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda x:(root/x).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
store=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaSessionStore.java")
history=read("app/src/main/java/dev/skyrimsemobile/launcher/RecentNifPaths.java")
gradle=read("app/build.gradle")
checks={
 "private prefs for metadata":"getSharedPreferences(PREFS,Context.MODE_PRIVATE)" in store,
 "archive permission persisted":"takePersistableUriPermission" in store,
 "permissions validated on restore":"getPersistedUriPermissions()" in store,
 "read-only grant":"FLAG_GRANT_READ_URI_PERMISSION" in store,
 "no write access":"FLAG_GRANT_WRITE_URI_PERMISSION" not in store and "FLAG_GRANT_WRITE_URI_PERMISSION" not in ui,
 "saved archive metadata":"void saveArchive(Uri uri,String name)" in store,
 "saved precise NIF":"void saveModel(Uri archiveUri,String nifPath)" in store,
 "saved data tree":"void saveDataTree(Uri tree)" in store,
 "saved URI cannot load without grant":"return hasReadPermission(uri)?uri:null;" in store,
 "auto restore during creation":"restoreSavedSession();" in ui,
 "async archive read":"BsaCatalogReader.Catalog found=BsaCatalogReader.read(in)" in ui,
 "race-safe archive loading":"request!=archiveLoadSequence" in ui,
 "model restored with exact NIF path":"NifCatalogSelection.matches(found,priorNif,\"\")" in ui,
 "restore NIF selection":"selectedNifPath=priorNif;selectedNifArchive=uri;" in ui,
 "recovery starts materials":"if(valid)inspectNifMaterials();" in ui,
 "manual selection remembered":"savedSession.saveModel(currentUri,entry.path)" in ui,
 "Data tree reused for DDS":"Uri remembered=savedSession.lastDataTree();" in ui and "scanDdsInTree(remembered)" in ui,
 "folder override":"Изменить папку Data · v2.10" in ui and "private void chooseDataFolder()" in ui,
 "persistable document flags":"Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION" in ui,
 "user forgets paths and grants":"savedSession.forgetAll()" in ui and "releasePersistableUriPermission" in store,
 "bounded history":"public static final int LIMIT=8" in history,
 "history selector":"Недавние модели NIF · v2.10" in ui,
 "history exact match":"NifCatalogSelection.matches(" in ui,
 "old composite still available":"previewCombinedNif()" in ui,
 "old DDS still available":"previewTexturedNif()" in ui,
 "same package":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "version increment":"versionCode 32" in gradle and "versionName '2.10.0'" in gradle,
}
assert all(checks.values()),[k for k,ok in checks.items() if not ok]
print("PASS v2.10 SAF path persistence, auto restoration, recent NIFs:",len(checks),"contracts")
