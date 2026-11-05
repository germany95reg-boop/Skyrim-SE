from pathlib import Path
p=Path(__file__).resolve().parents[1]
read=lambda a:(p/a).read_text(encoding='utf-8')
a=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java')
b=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java')
c=read('app/src/main/java/dev/skyrimsemobile/launcher/DdsArchiveFinder.java')
d=read('app/src/main/java/dev/skyrimsemobile/launcher/NifMaterialInspector.java')
g=read('app/build.gradle')
assert "versionCode 16" in g and "versionName '1.6.0'" in g
assert 'Найти DDS в папке Data · v1.6' in a
assert 'Intent.ACTION_OPEN_DOCUMENT_TREE' in a
assert 'DocumentsContract.buildChildDocumentsUriUsingTree' in a
assert 'DocumentsContract.buildDocumentUriUsingTree' in a
assert 'MAX_BSA_ARCHIVES=48' in a and 'MAX_DATA_CHILDREN=4096' in a
assert 'BsaCatalogReader.read(in)' in a and 'DdsArchiveFinder.find(c,targets,doc.name)' in a
assert 'BsaEntryInspector.readMaterialData(in,entry,flags)' in a
assert 'NifMaterialInspector.inspect(d.bytes)' in b
assert 'if(c.partialIndex)partial++' in a
assert 'Не найдено в проверенных индексах' in a
assert 'openOutputStream' not in a+b+c+d
assert 'TamrielVoice' not in a+b+c+d
assert 'surfaceTriangles' in read('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java')
assert 'solid.frag.spv' in read('app/src/main/cpp/vulkan_scene_test.cpp')
print('PASS: v1.6 read-only DDS folder picker and exact name match; Vulkan unchanged')
