from pathlib import Path
import struct
p=Path(__file__).resolve().parents[1]
manifest=(p/'app/src/main/AndroidManifest.xml').read_text()
gradle=(p/'app/build.gradle').read_text()
assert 'android:icon="@mipmap/ic_launcher"' in manifest
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
assert 'versionCode 35' in gradle and "versionName '2.12.0'" in gradle
assert "signingConfig signingConfigs.stableDebug" in gradle
assert "rootProject.file('../.ci/skyrimse-mobile-test.jks')" in gradle
assert "applicationId 'dev.skyrimsemobile.launcher'" in gradle
assert (p/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').exists()
for name in ('mipmap-mdpi','mipmap-hdpi','mipmap-xhdpi','mipmap-xxhdpi','mipmap-xxxhdpi'):
    for img in ('ic_launcher.png','ic_launcher_round.png'):
        path=p/'app/src/main/res'/name/img
        assert path.exists(),path
        raw=path.read_bytes()
        assert raw[:8]==b'\x89PNG\r\n\x1a\n'
        width,height=struct.unpack('>II',raw[16:24]);assert width>0 and height>0
print('PASS: user icon, stable signature, same package, incremented versionCode')
