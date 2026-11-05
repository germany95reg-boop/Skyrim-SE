from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java').read_text()
r=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogReader.java').read_text()
parent=(p/'app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java').read_text()
manifest=(p/'app/src/main/AndroidManifest.xml').read_text()
gradle=(p/'app/build.gradle').read_text()
assert 'BsaCatalogActivity' in manifest and 'android:exported="false"' in manifest
assert 'new Intent(this,BsaCatalogActivity.class)' in parent
assert 'Intent.ACTION_OPEN_DOCUMENT' in a and 'FLAG_GRANT_READ_URI_PERMISSION' in a
assert 'ACTION_CREATE_DOCUMENT' not in a and 'openOutputStream' not in a
assert 'readFully' in r and 'MAX_METADATA_BYTES' in r and 'MAX_INDEXED' in r
assert 'fileNameBytes' in r and 'readFully(in,fileRecord)' in r
assert 'NIF' in a and 'только' in a.lower()
assert any("versionName '" + v + "'" in gradle for v in ("0.8.0", "0.9.0", "1.0.0", "1.2.0", "1.3.0"))
assert 'openResourceInspector()' in (p/'app/src/main/assets/index.html').read_text()
print('PASS: v0.8 read-only BSA index contracts')
