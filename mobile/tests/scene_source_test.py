"""Offline source checks. They DO NOT verify rendering or a phone run."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
paths={n:(root/p).read_text() for n,p in {
  'shaderVert':'app/src/main/shaders/cube.vert',
  'shaderFrag':'app/src/main/shaders/cube.frag',
  'native':'app/src/main/cpp/vulkan_scene_test.cpp',
  'java':'app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java',
  'main':'app/src/main/java/dev/skyrimsemobile/launcher/MainActivity.java',
  'manifest':'app/src/main/AndroidManifest.xml',
  'cmake':'app/src/main/cpp/CMakeLists.txt',
  'ui':'app/src/main/assets/index.html',
  'preview':'launcher_preview.html',
}.items()}
assert paths['ui']==paths['preview']
assert 'vkCreateGraphicsPipelines' in paths['native']
assert 'vkCmdDraw' in paths['native']
assert 'vkAcquireNextImageKHR' in paths['native']
assert 'vkQueuePresentKHR' in paths['native']
assert 'gl_VertexIndex' in paths['shaderVert']
assert 'layout(push_constant)' in paths['shaderVert']
assert 'frameColor' not in paths['shaderFrag']
assert 'skyrimse_scene SHARED' in paths['cmake']
assert 'VulkanSceneActivity.class' in paths['main']
assert 'VulkanSceneActivity' in paths['manifest']
assert 'openVulkan3DTest' in paths['ui']
assert 'nativeStopScene' in paths['java']
assert 'engineConnected", false' in paths['main']
print('PASS: scene code contracts (15 assertions, static only)')
