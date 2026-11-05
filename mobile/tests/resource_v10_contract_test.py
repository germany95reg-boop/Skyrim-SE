from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java').read_text()
e=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java').read_text()
n=(p/'app/src/main/java/dev/skyrimsemobile/launcher/NifBlockInspector.java').read_text()
g=(p/'app/build.gradle').read_text()
assert "this::inspectNifBlocks" in a
assert "NifBlockInspector.inspect" in e
assert "readDecoded" in e and "getContentResolver().openInputStream(uri)" in a
assert "MAX_BYTES=16*1024*1024" in n
assert "MAX_BLOCKS=8192" in n
assert "Версия NIF в бинарном поле" in n
assert "НЕ декодировались" in n
assert "openOutputStream" not in a and "ACTION_CREATE_DOCUMENT" not in a
assert "versionCode 13" in g and "versionName '1.3.0'" in g
assert "BSFadeNode" in n or "BSTriShape" in n
assert 'TamrielVoice' not in a+e+n
print('PASS: v1.0 read-only NIF block inventory contracts')
