"""Source-contract regression for Vulkan VK_ERROR_UNKNOWN (-13) recovery in v2.14.

This does not replace on-device GPU execution. The CI workflow additionally
compiles and validates all three fragment variants as Vulkan 1.0 SPIR-V.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")

native = read("app/src/main/cpp/vulkan_scene_test.cpp")
safe_vert = read("app/src/main/shaders/textured_safe.vert")
safe_frag = read("app/src/main/shaders/textured_safe.frag")
legacy_frag = read("app/src/main/shaders/textured_legacy.frag")
gradle = read("app/build.gradle")
activity = read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
ci = read("../.github/workflows/build-skyrimse-v2-13-apk.yml")

checks = {
    "upgradeable version code": "versionCode 51" in gradle,
    "upgradeable version name": "versionName '2.28.0'" in gradle,
    "persistent Android package": "applicationId 'dev.skyrimsemobile.launcher'" in gradle,
    "same signing certificate": "signingConfig signingConfigs.stableDebug" in gradle,
    "all existing DDS/NIF features": "nativeSetDiffuseAtlas" in native and "nativeSetNormalTexture" in native,
    "full pipeline VkResult handled": "const VkResult fullResult=vkCreateGraphicsPipelines" in native,
    "fallback does not throw on -13": '"vkCreateGraphicsPipelines(textured)"' not in native,
    "bounded fallback order": native.index('const VkResult fullResult') <
                              native.index('const VkResult safeResult') <
                              native.index('const VkResult basicResult'),
    "safe per-material texture": 'textured_safe.frag.spv' in native and 'textured_safe.vert.spv' in native,
    "basic DDS mode": 'textured_legacy.frag.spv' in native,
    "no texture never aborts entire scene": 'textureReady=false; // Skip texture upload' in native,
    "solid/line unaffected": 'vkCreateGraphicsPipelines(solid)' in native and 'vkCreateGraphicsPipelines(lines)' in native,
    "fallback surfaced in UI": 'atlas-safe' in activity and 'basic' in activity and 'textureMode' in native,
    "safe fragment flat float material": 'flat in float materialSlot' in safe_frag,
    "safe vertex float material": 'flat out float materialSlot' in safe_vert,
    "safe atlas uses float math": 'float row=floor(slot/grid);' in safe_frag,
    "safe shader avoids derivatives": 'dFdx' not in safe_frag and 'dFdy' not in safe_frag,
    "safe shader avoids int modulo": '%grid' not in safe_frag,
    "legacy shader avoids material slots": 'materialSlot' not in legacy_frag,
    "legacy shader avoids push constants": 'push_constant' not in legacy_frag,
    "three SPIR-V variants in CI": all(
        k in ci for k in ('textured.vert.spv', 'textured.frag.spv',
                         'textured_safe.vert.spv', 'textured_safe.frag.spv',
                         'textured_legacy.frag.spv')),
    "SPIR-V validation": "spirv-val --target-env vulkan1.0" in ci,
    "runtime installed version in 3D UI": '"SKYRIM SE · 3D · " + InstalledAppVersion.label(this)' in activity,
}
assert all(checks.values()), [k for k,v in checks.items() if not v]
print("PASS v2.14 Vulkan fallback contracts:", len(checks))
