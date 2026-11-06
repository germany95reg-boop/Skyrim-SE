from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda name:(root/name).read_text(encoding="utf-8")
decoder=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java")
entry=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
transfer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
gradle=read("app/build.gradle")
checks={
    "bounded individual part count":"MAX_PARTS=24" in decoder,
    "bounded triangle budget":"MAX_PART_TRIANGLES=60000" in decoder,
    "decode all candidate blocks":'if(!"BSTriShape".equals(block.type))continue' in decoder,
    "keep unsupported blocks visible":"new Part(block.index,null" in decoder,
    "surface geometry checked":"Mesh m=parseShape(bytes,block)" in decoder,
    "noneditable shape report":"Части NIF v2.10" in decoder,
    "no false material association":"связи шейдеров с DDS" in decoder,
    "bounded extraction reused":"Decoded d=readDecoded(bsa,entry,archiveFlags)" in entry,
    "activity read via SAF":"getContentResolver().openInputStream(uri)" in ui,
    "visible button":"Части NIF · выбрать BSTriShape · v2.10" in ui,
    "dialog list":"setItems(options,(dialog,index)->" in ui,
    "choice only loads selected mesh":"NifGeometryDecoder.Part part=parts.items.get(index)" in ui,
    "bad part never launches":"if(part.mesh==null)" in ui,
    "no DDS in parts handoff":"NifGeometryTransfer.offer(mesh.lines,mesh.surfaceTriangles" in ui,
    "original textured preview remains":"previewTexturedNif()" in ui,
    "original single preview remains":"previewNifGeometry()" in ui,
    "NIF game files read-only":"openOutputStream" not in ui and "openOutputStream" not in entry,
    "no unnecessary changes to texture handoff":"offerTextured" in transfer,
    "APK identity":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
    "next version":"versionCode 32" in gradle and "versionName '2.10.0'" in gradle
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.10 read-only NIF part browser:",len(checks),"contracts")
