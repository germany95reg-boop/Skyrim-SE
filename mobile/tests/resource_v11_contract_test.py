from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java').read_text()
b=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java').read_text()
g=(p/'app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java').read_text()
t=(p/'app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java').read_text()
s=(p/'app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java').read_text()
gradle=(p/'app/build.gradle').read_text()
assert 'versionCode 13' in gradle and "versionName '1.3.0'" in gradle
assert 'this::previewNifGeometry' in a and 'NifGeometryTransfer.offer' in a
assert 'getContentResolver().openInputStream(uri)' in a
assert 'BsaEntryInspector.decodeNifGeometry' in a
assert 'readDecoded(bsa,entry,archiveFlags)' in b and 'NifGeometryDecoder.decode' in b
assert 'NifBlockInspector.inspect' in g and 'BSTriShape' in g
assert 'MAX_VERTICES = 40000' in g and 'MAX_EDGES = 30000' in g
assert 'declaredSize != expected' in g and 'if(a>=vertices||c>=vertices||d>=vertices)' in g
assert 'NifGeometryTransfer.take' in s and 'nativeSetGeometry(model.segments,name)' in s
assert 'synchronized' in t and 'pending=null' in t
assert all('openOutputStream' not in x and 'ACTION_CREATE_DOCUMENT' not in x for x in (a,b,g,t))
assert all('TamrielVoice' not in x for x in (a,b,g,t,s))
print('PASS: v1.1 bounded NIF->Vulkan in-memory bridge and read-only checks')
