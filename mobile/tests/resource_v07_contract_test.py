from pathlib import Path
p=Path(__file__).resolve().parents[1]
main=(p/'app/src/main/java/dev/skyrimsemobile/launcher/MainActivity.java').read_text()
manifest=(p/'app/src/main/AndroidManifest.xml').read_text()
activity=(p/'app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java').read_text()
html=(p/'app/src/main/assets/index.html').read_text()
assert 'openResourceInspector()' in html and 'openResourceInspector()' in main
assert 'ResourceInspectorActivity' in manifest and 'ResourceInspectorActivity' in main
assert 'ACTION_OPEN_DOCUMENT_TREE' in activity and 'ACTION_OPEN_DOCUMENT' in activity
assert 'FLAG_GRANT_READ_URI_PERMISSION' in activity
assert 'MAX_HEADER=256' in (p/'app/src/main/java/dev/skyrimsemobile/launcher/ResourceHeaderInspector.java').read_text()
assert 'SkyrimSE.exe' not in activity
print('PASS: v0.7 resource inspector static contract')
