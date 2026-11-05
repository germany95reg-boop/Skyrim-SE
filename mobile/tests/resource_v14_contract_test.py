from pathlib import Path
r=Path(__file__).resolve().parents[1]
def read(path):return (r/path).read_text()
java=read('app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java')
native=read('app/src/main/cpp/vulkan_scene_test.cpp')
decoder=read('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryDecoder.java')
transfer=read('app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java')
activity=read('app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java')
vertex=read('app/src/main/shaders/cube.vert')
frag=read('app/src/main/shaders/solid.frag')
assert "versionName '1.4.0'" in read('app/build.gradle')
assert 'mesh.surfaceTriangles' in activity and 'NifGeometryTransfer.offer' in activity
assert 'MAX_TRIANGLES = 30000' in decoder
assert 'indices[t*3]=a' in decoder and 'surfaceTriangles[i*3+k]' in decoder
assert 'surfaceTriangles' in transfer
assert 'nativeSetSurfaceTriangles' in java and 'nativeSetSurfaceTriangles' in native
assert 'nativeSetSolidMode' in java and 'nativeSetSolidMode' in native
assert 'hasSurface=triangles!=null' in java and 'updateSurfaceButton()' in java
assert 'VK_PRIMITIVE_TOPOLOGY_LINE_LIST' in native and 'VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST' in native
assert 'VK_COMPARE_OP_LESS_OR_EQUAL' in native and 'VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT' in native
assert 'vkCmdBindVertexBuffers' in native and 'solidPipeline' in native
assert 'viewPosition' in vertex and 'dFdx(viewPosition)' in frag
assert 'solid.frag.spv' in native
assert 'openOutputStream' not in java + activity + decoder + native
assert 'TamrielVoice' not in java + activity + decoder + native
print('PASS: v1.4 bounded NIF triangle payload, Vulkan depth pipeline, live style switch')
