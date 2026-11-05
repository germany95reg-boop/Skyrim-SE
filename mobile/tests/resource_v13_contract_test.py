from pathlib import Path
p=Path(__file__).resolve().parents[1]
j=p/'app/src/main/java/dev/skyrimsemobile/launcher'
s=(j/'VulkanSceneActivity.java').read_text(); cpp=(p/'app/src/main/cpp/vulkan_scene_test.cpp').read_text(); sh=(p/'app/src/main/shaders/cube.vert').read_text(); g=(p/'app/build.gradle').read_text()
assert 'versionCode 13' in g and "versionName '1.3.0'" in g
assert 'nativeSetViewportOffset' in s and 'Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetViewportOffset' in cpp
assert 'float focusOffsetY;' in sh and 'focusOffsetY*z' in sh and 'range.size = 28' in cpp
assert 'sizeof(Parameters) == 28' in cpp
assert 'nativeSetView(yaw, pitch, zoom, autoSpin)' in s and '40.0f' in s and '40.0f' in cpp
assert 'SceneFraming.compute' in s and 'SceneFraming.radius(model.segments)' in s
assert 'scenePanel.getHeight()' in s and 'sceneTitle.getHeight()' in s
assert 'setOnClickListener(v -> { autoSpin=false; fitFollowsLayout=true' in s
assert 'left,top,right,bottom' not in s, 'Layout listener parameter shadows top camera button'
assert 'fitFollowsLayout=false' in s and 'root.addOnLayoutChangeListener' in s and 'panel.addOnLayoutChangeListener' in s
assert 'NifGeometryTransfer.take()' in s
assert 'openOutputStream' not in s and 'TamrielVoice' not in s
print('PASS: v1.3 visible viewport native-shader contract and legacy NIF flow')
