from pathlib import Path
root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java').read_text()
reader=(root/'app/src/main/java/dev/skyrimsemobile/launcher/BsaEntryInspector.java').read_text()
bsa=(root/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogReader.java').read_text()
gradle=(root/'app/build.gradle').read_text()
assert 'this::inspectFirstNif' in activity
assert 'getContentResolver().openInputStream(uri)' in activity
assert 'nifOnly' in activity
assert 'MAX_PACKED = 8 * 1024 * 1024' in reader
assert 'MAX_DECODED = 16 * 1024 * 1024' in reader
assert 'LZ4FrameInputStream' in reader
assert 'fastestJavaInstance()' in reader
assert 'entry.compressed' in reader
assert 'ResourceHeaderInspector.inspect' in reader
assert 'MAX_INDEXED = 60000' in bsa
assert "lz4-java:1.8.0" in gradle
assert any('versionCode '+str(v) in gradle for v in (9,10,11,12,13))
assert any("versionName '"+v+"'" in gradle for v in ('0.9.0','1.0.0','1.1.0','1.2.0','1.3.0'))
for s in (activity,reader,bsa):
    assert 'TamrielVoice' not in s
print('PASS: v0.9 source contracts, memory limits and read-only I/O')
