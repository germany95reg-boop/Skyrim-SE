from pathlib import Path
p=Path(__file__).resolve().parents[1]
read=lambda name:(p/name).read_text(encoding='utf-8')
a=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java')
b=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaDdsInspector.java')
c=read('app/src/main/java/dev/skyrimsemobile/launcher/DdsHeaderInspector.java')
d=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogReader.java')
g=read('app/build.gradle')
assert 'versionCode 22' in g and "versionName '2.1.1'" in g
assert 'Заголовки найденных DDS · v2.1' in a
assert 'BsaDdsInspector.inspect(in,entry,item.flags)' in a
assert 'lastDdsSearchReport' in a and 'foundDds=Collections.unmodifiableList' in a
assert 'MAX_BSA_ARCHIVES=48' in a and 'MAX_DATA_CHILDREN=4096' in a
assert 'DocumentsContract.buildDocumentUriUsingTree' in a
assert 'DdsArchiveFinder.find(c,targets,doc.name)' in a
assert 'Длина таблицы имён не соответствует заголовку:' in d
assert 'if(prefix.length<header||decodedLength<header)' in c
assert 'DX10_BYTES = 148' in c and 'BASE_BYTES = 128' in c
assert 'MAX_PACKED=8L*1024*1024,MAX_DECODED=16L*1024*1024' in b
assert 'new Bounded(in,remain)' in b
assert 'openOutputStream' not in a+b+c+d
assert 'TamrielVoice' not in a+b+c+d
assert 'surfaceTriangles' in read('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java')
assert 'solid.frag.spv' in read('app/src/main/cpp/vulkan_scene_test.cpp')
print('PASS: DDS header inspector and SAFE BSA read-only Vulkan v2.1 contracts')
