from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda x:(root/x).read_text(encoding='utf-8')
cat=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
transfer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
frag=read("app/src/main/shaders/textured.frag")
gradle=read("app/build.gradle")
checks=[
    "if(slot.index==1)normalPath=slot.path;" in cat,
    "normal DDS пропущена:" in cat,
    "candidate.width!=pixels.width||candidate.height!=pixels.height" in cat,
    "image,selectedNormal,name" in cat,
    "normal.argb.clone()" in transfer,
    "nativeSetNormalTexture(int[] argb,int width,int height)" in scene,
    "nativeSetUseNormalMap" in scene,
    "Нормали: Вкл" in scene and "Нормали: Выкл" in scene,
    "selectedNormalAtlas" in native,
    "selectedTextureHeight=static_cast<uint32_t>(height*2)" in native,
    "hasNormalAtlas" in native,
    "texRange.size=52" in native,
    "VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT" in native,
    "Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetNormalTexture" in native,
    "atlasMode" in frag and "normalEnabled" in frag,
    "cross(dpdx,dpdy)" in frag and "dFdx(uv)" in frag,
    "sqrt(max(0.0,1.0-dot(xy,xy)))" in frag,
    "layout(offset=28) int atlasMode" in frag,
    "versionCode 29" in gradle and "versionName '2.7.0'" in gradle,
    "openOutputStream" not in cat
]
assert all(checks),[i for i,c in enumerate(checks) if not c]
print("PASS v2.7 normal map handoff and Vulkan GLSL:",len(checks),"checks")
