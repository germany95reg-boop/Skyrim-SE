from pathlib import Path
p=Path(__file__).resolve().parents[1]
def t(s):return (p/s).read_text(encoding='utf-8')
a=t('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java')
b=t('app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java')
c=t('app/src/main/java/dev/skyrimsemobile/launcher/NifMaterialInspector.java')
g=t('app/build.gradle')
assert "versionCode 15" in g and "versionName '1.5.0'" in g
assert 'Пути текстур NIF · v1.5' in a and 'inspectNifMaterials' in a
assert 'BsaEntryInspector.inspectMaterials(in,entry,flags)' in a
assert 'NifMaterialInspector.inspect(d.bytes)' in b
assert 'MAX_TEXTURES_PER_SET=32' in c and 'MAX_PATH_BYTES=1024' in c
assert 'NifBlockInspector.inspect(nif)' in c
assert 'readDecoded(bsa,entry,archiveFlags)' in b
assert 'openOutputStream' not in a+b+c
assert 'TamrielVoice' not in a+b+c
assert 'surfaceTriangles' in t('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java')
assert 'nativeSetSolidMode' in t('app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java')
assert 'solid.frag.spv' in t('app/src/main/cpp/vulkan_scene_test.cpp')
print('PASS: v1.5 NIF read-only material inspection preserves solid Vulkan preview')
