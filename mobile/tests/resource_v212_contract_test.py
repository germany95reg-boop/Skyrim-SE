from pathlib import Path

import re,struct
p=Path(__file__).resolve().parents[1]
gradle=(p/'app/build.gradle').read_text()
manifest=(p/'app/src/main/AndroidManifest.xml').read_text()
java=(p/'app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java').read_text()
assert 'versionCode 23' in gradle and "versionName '2.1.2'" in gradle
assert "applicationId 'dev.skyrimsemobile.launcher'" in gradle
assert "rootProject.file('../.ci/skyrimse-mobile-test.jks')" in gradle
assert 'signingConfig signingConfigs.stableDebug' in gradle
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'Показать текстуры DDS · BC1/2/3/4/5 · v2.1.2' in java
assert 'BsaDdsPixelReader.preview(' in java
assert 'Bitmap.createBitmap(' in java
assert 'MAX_PIXELS=2048*2048' in (p/'app/src/main/java/dev/skyrimsemobile/launcher/DdsBcDecoder.java').read_text()
assert (p/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').read_text().count('ic_skyrim_backplate')==1
assert (p/'app/src/main/res/drawable-nodpi/ic_skyrim_foreground.xml').read_text().count('ic_skyrim_background')==1
bg=(p/'app/src/main/res/drawable-nodpi/ic_skyrim_background.jpg').read_bytes()
assert len(bg)>100000 and bg[:3]==b'\xff\xd8\xff', 'full bleed JPEG background'
for den,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
 raw=(p/f'app/src/main/res/mipmap-{den}/ic_launcher.png').read_bytes()
 assert raw[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',raw[16:24])==(size,size)
print('PASS v2.1.2 icon full bleed, stable signing, BCn preview, app identity')
