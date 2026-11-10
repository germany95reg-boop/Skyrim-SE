"""v2.25 optional straight-alpha Vulkan preview: keep proven Adreno normal-safe intact.

The user tested v2.24 on real SM-S948B: 22 PASS, alpha DDS has 18,654
intermediate-alpha pixels. Adding blending must not alter the *default*
normal-safe pipeline or the original one-tap 30/60/unlimited GPU test.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
alpha=read("app/src/main/java/dev/skyrimsemobile/launcher/DdsAlphaCoverage.java")
normal=read("app/src/main/shaders/textured_normal_safe.frag")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java")
gradle=read("app/build.gradle")
manifest=read("app/src/main/AndroidManifest.xml")
workflow=read("../.github/workflows/android-quality-gates.yml")
checks={
 "separate optional Vulkan graphics pipeline":'VkPipeline textureBlendPipeline=VK_NULL_HANDLE' in native,
 "normal-safe regular pipeline unchanged":'textured?texturePipeline:(filled?solidPipeline:pipeline)' in native,
 "alpha preview atomic defaults off":'std::atomic<bool> previewAlphaBlend{false};' in native,
 "full shader creates alternate only after success":'textureMode="full";\n                tryAlphaPreview();' in native,
 "fallback creates alternate while alive":'if(result==VK_SUCCESS)tryAlphaPreview();' in native,
 "normal-safe fallback retained":'"textured_normal_safe.frag.spv"' in native,
 "straight-alpha source blend":'VK_BLEND_FACTOR_SRC_ALPHA' in native,
 "one-minus-source-alpha destination":'VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA' in native,
 "no depth writes in optional pass":'depthState.depthWriteEnable=VK_FALSE;' in native,
 "restore old depth state":'depthState.depthWriteEnable=oldDepthWrite;' in native,
 "restore regular blending disabled":'blendAttachment.blendEnable=VK_FALSE;' in native,
 "optional Vulkan failure nonfatal":'Optional DDS alpha preview unavailable VkResult=' in native,
 "valid alpha pipeline destroyed":'if (textureBlendPipeline) vkDestroyPipeline(device,textureBlendPipeline,nullptr);' in native,
 "manual toggle uses native JNI":'nativeSetAlphaBlend(boolean enabled);' in scene and
     'Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetAlphaBlend' in native,
 "user only enabled manual preview":'if(!hasTexture||deviceAutotest)return;' in scene,
 "button inside optional advanced panel":'advancedControls.addView(alphaButton,' in scene,
 "no controls hidden during normal preview":'alphaButton=controlButton("Альфа: Вырезы")' in scene,
 "normal default cutout label":'Альфа: Вырезы (проверено)' in scene,
 "blend preview clearly experimental":'Альфа: Смешивание (эксперимент)' in scene,
 "disable button if GPU lacks pipeline":'alphaButton.setEnabled(hasTexture&&supported);' in scene,
 "one-tap test always turns blend off":'if(deviceAutotest&&loadingError==null)nativeSetAlphaBlend(false);' in scene,
 "manual app lifecycle obeys test mode":'nativeSetAlphaBlend(alphaBlendPreview&&!deviceAutotest);' in scene,
 "each new NIF resets to validated mode":'alphaBlendPreview=false;\n        nativeSetAlphaBlend(false);' in scene,
 "native binding is safe atomic":'previewAlphaBlend.store(enabled==JNI_TRUE,std::memory_order_relaxed)' in native,
 "actual native active pipeline telemetry":'alphaBlendApplied' in native and 'alphaBlendSupported' in native,
 "DDS alpha cutout remains on proven shader":'if(base.a < 0.05) discard;' in normal,
 "normal-safe tangent mapping preserved":'options.normalEnabled!=0' in normal,
 "CPU detects partial alpha separately":'partialAlphaPixels' in alpha,
 "device FPS stages still active":'fpsSequence.sample(' in scene,
 "device PixelCopy still active":'PixelCopy.request(frameSurface,pixels' in scene,
 "user-requested footer stays absent":'Отчёт не отправлялся автоматически' not in report,
 "no automatic network uploads":'android.permission.INTERNET' not in manifest,
 "upgrade certificate version":'versionCode 72' in gradle and "versionName '2.49.0'" in gradle,
 "same package ID":'applicationId \'dev.skyrimsemobile.launcher\'' in gradle,
 "CI v2.25 contract included":'v225_optional_alpha_blend_contract_test.py' in workflow,
}
assert all(checks.values()),[name for name,passed in checks.items() if not passed]
print("PASS v2.25 optional DDS alpha blend with normal-safe fallback:",len(checks),"checks")
