from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
scene=read('app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java')
frame=read('app/src/main/java/dev/skyrimsemobile/launcher/SceneFraming.java')
cpp=read('app/src/main/cpp/vulkan_scene_test.cpp')
gradle=read('app/build.gradle')
checks={
 'v2.10.1 heading':'Vulkan 3D · v2.10.1' in scene,
 'compact by default':'advancedControls.setVisibility(View.GONE)' in scene,
 'advanced row contains OBJ':'advancedControls.addView(meshControls)' in scene,
 'advanced row contains lighting':'advancedControls.addView(lightingControls)' in scene,
 'advanced row contains materials':'advancedControls.addView(materialControls)' in scene,
 'advanced button':'controlButton("Параметры ▾")' in scene,
 'expanded label':'Скрыть параметры ▴' in scene,
 'advanced button toggles':'advancedControls.setVisibility(expand?View.VISIBLE:View.GONE)' in scene,
 'advanced triggers fit':'panel.post(this::refreshFraming)' in scene,
 'return button safe':'back.setText("← Назад")' in scene,
 'old long label gone':'back.setText("Вернуться в лаунчер")' not in scene,
 'zoom button':'controlButton("Крупнее")' in scene,
 'zoom mathematical step':'SceneFraming.closer(zoom)' in scene,
 'zoom exits auto spin':'autoSpin=false;' in scene,
 'zoom switches to manual':'fitFollowsLayout=false;' in scene,
 'fit button preserved':'controlButton("Вписать")' in scene,
 'frame bigger but padded':'float safety=0.90f' in frame,
 'safe zoom clamp':'distance*0.82f' in frame and 'Math.max(2.7f' in frame,
 'draw mode intact':'nativeSetSolidMode(!solidMode)' in scene,
 'dds intact':'nativeSetUseTexture(useTexture)' in scene,
 'normal map intact':'nativeSetUseNormalMap(useNormalMap)' in scene,
 'light intact':'nativeSetLightMode(lightMode)' in scene,
 'relief intact':'nativeSetNormalStrength(PreviewLighting.reliefStrength(reliefPreset))' in scene,
 'gloss intact':'nativeSetSpecularStrength(PreviewMaterial.specularStrength(specularPreset))' in scene,
 'shader intact':'texRange.size=52' in cpp,
 'same identity':"applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 'version upgraded':"versionCode 33" in gradle and "versionName '2.10.1'" in gradle,
}
assert all(checks.values()),[k for k,ok in checks.items() if not ok]
print('PASS v2.10.1: compact panel, model zoom and stable rendering:',len(checks),'checks')
