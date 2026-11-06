from pathlib import Path
root=Path(__file__).resolve().parents[1]
get=lambda x:(root/x).read_text(encoding="utf-8")
scene=get("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
cpp=get("app/src/main/cpp/vulkan_scene_test.cpp")
frag=get("app/src/main/shaders/textured.frag")
helper=get("app/src/main/java/dev/skyrimsemobile/launcher/PreviewMaterial.java")
gradle=get("app/build.gradle")
check={
 "new specular JNI Java":"nativeSetSpecularStrength(float strength)" in scene,
 "new width JNI Java":"nativeSetSpecularSize(int size)" in scene,
 "new specular JNI C++":"Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSpecularStrength" in cpp,
 "new size JNI C++":"Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSpecularSize" in cpp,
 "disabled without normal map":"specularButton.setEnabled(hasNormalMap)" in scene and "specularSizeButton.setEnabled(hasNormalMap)" in scene,
 "instant state update":"nativeSetSpecularStrength(PreviewMaterial.specularStrength(specularPreset))" in scene,
 "instant size update":"nativeSetSpecularSize(specularSizePreset)" in scene,
 "default zero preview":"previewSpecularStrength{0.0f}" in cpp,
 "default mode width":"previewSpecularSize{1}" in cpp,
 "clamp strength":"std::max(0.0f,std::min(1.0f" in cpp,
 "guard invalid values":"std::isfinite(strength)" in cpp,
 "guard size":"(size>=0 && size<=2)" in cpp,
 "vk pipeline range":"texRange.size=52" in cpp,
 "vk structure exact":"sizeof(TexturedParameters)==52" in cpp,
 "shader interface float":"layout(offset=44) float specularStrength" in frag,
 "shader interface int":"layout(offset=48) int specularSize" in frag,
 "normal DDS alpha mask":"texture(diffuseSampler,normalUV).a" in frag,
 "mask limited":"mix(0.25,1.0,glossMask)" in frag,
 "masked only when atlas":"options.atlasMode!=0&&options.specularStrength>0.001" in frag,
 "exponent choices":"12.0" in frag and "32.0" in frag and "64.0" in frag,
 "alpha survives":"fragColor=vec4(shaded,base.a)" in frag,
 "v2.3 camera remains":"sizeof(Parameters) == 28" in cpp,
 "v2.3 normals remain":"selectedNormalAtlas" in cpp and "normalEnabled" in frag,
 "same app id":"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "new code and name":"versionCode 28" in gradle and "versionName '2.6.0'" in gradle,
 "pure JVM helper":"android." not in helper and "static float specularStrength" in helper,
}
assert all(check.values()),[key for key,value in check.items() if not value]
print("PASS v2.6 specular UI → JNI → Vulkan alpha preview:",len(check),"contracts")
