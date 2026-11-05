from pathlib import Path
p=Path(__file__).resolve().parents[1]
r=lambda path:(p/path).read_text(encoding='utf-8')
ui=r('app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java')
helper=r('app/src/main/java/dev/skyrimsemobile/launcher/PreviewLighting.java')
cpp=r('app/src/main/cpp/vulkan_scene_test.cpp')
frag=r('app/src/main/shaders/textured.frag')
vert=r('app/src/main/shaders/textured.vert')
gradle=r('app/build.gradle')
checks={
    'ui native light setter':'nativeSetLightMode(int mode)' in ui,
    'ui native relief setter':'nativeSetNormalStrength(float strength)' in ui,
    'live light cycle':'lightMode=PreviewLighting.nextLight(lightMode)' in ui,
    'live normal cycle':'reliefPreset=PreviewLighting.nextRelief(reliefPreset)' in ui,
    'native light updates':'nativeSetLightMode(lightMode)' in ui,
    'native relief updates':'nativeSetNormalStrength(PreviewLighting.reliefStrength(reliefPreset))' in ui,
    'light disabled without DDS':'lightButton.setEnabled(hasTexture)' in ui,
    'relief disabled without normals':'reliefButton.setEnabled(hasNormalMap)' in ui,
    'UI labels':'Свет: ' in helper and 'Рельеф: ' in helper,
    'four lighting modes':'Сбоку","Спереди","Слева","Сверху' in helper,
    'shader 4 directions':'options.lightMode==1' in frag and 'options.lightMode==2' in frag and 'options.lightMode==3' in frag,
    'shader strength':'options.reliefStrength,0.5,1.5' in frag,
    'shader binary interface mode':'layout(offset=36) int lightMode' in frag,
    'shader binary interface strength':'layout(offset=40) float reliefStrength' in frag,
    'vk push constant new range':'texRange.size=52' in cpp,
    'vk push constants compile size':'sizeof(TexturedParameters)==52' in cpp,
    'vk preserved shader layout':'layout(offset=28) int atlasMode' in frag and 'layout(offset=32) int normalEnabled' in frag,
    'vk pipeline bounded input':'(mode>=0 && mode<=3)' in cpp and 'std::isfinite(strength)' in cpp,
    'vk camera still 28':'sizeof(Parameters) == 28' in cpp and 'float angle;' in vert,
    'vk relights without rebuilding':'previewLightMode.store' in cpp and 'previewReliefStrength.store' in cpp,
    'unchanged selected texture atlas':'selectedNormalAtlas' in cpp,
    'package unchanged':"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
    'v2.4 version':"versionCode 26" in gradle and "versionName '2.4.0'" in gradle
}
assert all(checks.values()),[name for name,ok in checks.items() if not ok]
print('PASS v2.4 Vulkan preview light and relief contracts:',len(checks),'checks')
