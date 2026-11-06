from pathlib import Path
import xml.etree.ElementTree as ET
base=Path(__file__).resolve().parents[1]
res=base/'app/src/main/res'
nd='{'+'http://schemas.android.com/apk/res/android'+'}'
fg=res/'drawable-nodpi/ic_skyrim_foreground.xml'
back=res/'drawable-nodpi/ic_skyrim_backplate.xml'
layer=ET.parse(fg).getroot()
assert layer.tag=='layer-list'
assert len(layer)==1 and layer[0].tag=='item'
for edge in ('left','top','right','bottom'):
    assert layer[0].attrib[nd+edge]=='18dp',edge
bitmap=layer[0].find('bitmap')
assert bitmap is not None
assert bitmap.attrib[nd+'src']=='@drawable/ic_skyrim_background'
assert bitmap.attrib[nd+'gravity']=='fill'
assert ET.parse(back).getroot().tag=='shape'
for name in ('ic_launcher.xml','ic_launcher_round.xml'):
    icon=ET.parse(res/'mipmap-anydpi-v26'/name).getroot()
    assert icon.tag=='adaptive-icon'
    assert icon.find('background').attrib[nd+'drawable']=='@drawable/ic_skyrim_backplate'
    assert icon.find('foreground').attrib[nd+'drawable']=='@drawable/ic_skyrim_foreground'
for density in ('mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi'):
    for name in ('ic_launcher.png','ic_launcher_round.png'):
        assert (res/('mipmap-'+density)/name).is_file()
scene=(base/'app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java').read_text()
assert 'Vulkan 3D · v2.11' in scene
assert 'Vulkan 3D · v1.6' not in scene
for n in ('BsaCatalogActivity.java','ResourceInspectorActivity.java'):
    assert 'v2.11' in (base/'app/src/main/java/dev/skyrimsemobile/launcher'/n).read_text()
html=(base/'app/src/main/assets/index.html').read_text()
assert 'Ресурсы v2.11' in html
assert '3D Vulkan · v2.11' in html
gradle=(base/'app/build.gradle').read_text()
assert 'versionCode 34' in gradle and "versionName '2.11.0'" in gradle
print('PASS: v2.11 foreground within 72dp adaptive mask, both round/square icons and UI versions')
