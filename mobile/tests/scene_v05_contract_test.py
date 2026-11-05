from pathlib import Path
r = Path(__file__).resolve().parents[1]
java=(r/'app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java').read_text()
cpp=(r/'app/src/main/cpp/vulkan_scene_test.cpp').read_text()
vert=(r/'app/src/main/shaders/cube.vert').read_text()
ui=(r/'app/src/main/assets/index.html').read_text()
assert 'nativeSetView' in java and 'nativeSetView' in cpp
assert 'nativeSetFpsCap' in java and 'nativeSetFpsCap' in cpp
assert 'MotionEvent.ACTION_POINTER_DOWN' in java
assert 'MotionEvent.ACTION_POINTER_UP' in java
assert 'restartPending' in java
assert 'autoRotate' in cpp and 'cameraPitch' in cpp and 'cameraDistance' in cpp
assert 'range.size = 24' in cpp
assert 'float pitch;' in vert and 'float distance;' in vert
assert 'app/src/main/shaders/cube.frag' .replace('app/src/main/shaders/','')
assert '3D-тест Vulkan · OBJ-модели и камера' in ui
print('PASS v0.5 source contracts')
