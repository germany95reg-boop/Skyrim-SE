from pathlib import Path
p=Path(__file__).resolve().parents[1]
get=lambda name:(p/name).read_text(encoding='utf-8')
geom=get('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java')
transfer=get('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java')
ui=get('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java')
scene=get('app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java')
native=get('app/src/main/cpp/vulkan_scene_test.cpp')
vert=get('app/src/main/shaders/textured.vert')
frag=get('app/src/main/shaders/textured.frag')
assert 'float[] surfaceUVs' in geom and 'static float halfFloat' in geom
assert 'uvOffset' in geom and 'hasUv' in geom and 'Float.isFinite(u)' in geom
assert 'offerTextured' in transfer and 'image.argb.clone()' in transfer
assert 'Показать NIF с DDS в Vulkan · v2.4' in ui
assert 'materials.textureSets.size()!=1||materials.shapeCount!=1' in ui
assert 'slot.index==0' in ui
assert 'nativeSetTexture(float[] uv,int[] argb,int width,int height)' in scene
assert 'nativeSetUseTexture' in scene
assert 'VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL' in native
assert 'VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER' in native
assert 'vkCmdCopyBufferToImage' in native
assert 'vkCmdBindDescriptorSets' in native
assert 'vkCmdPushConstants' in native
assert 'VK_FORMAT_R8G8B8A8_UNORM' in native
assert 'layout(location=1) in vec2 inUV' in vert
assert 'layout(set=0,binding=0) uniform sampler2D' in frag
assert 'openOutputStream' not in ui
print('PASS v2.4: UV → JNI → Vulkan sampled DDS contract')
