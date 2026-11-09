"""Prevent loss of JVM/Vulkan coverage or shipping an incomplete signed ARM64 APK.

The user's actual phone runs the UI/GPU self-test. Android emulator job is no
longer part of automatic CI; it cannot validate the Adreno device.
"""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
gradle=read("app/build.gradle")
manifest=read("app/src/main/AndroidManifest.xml")
workflow=(root.parent/".github/workflows/android-quality-gates.yml").read_text(encoding="utf-8")
jvm=read("ci/run_jvm_suite.sh")
verify=read("ci/verify_apk.sh")
shaders=read("ci/compile_shaders.sh")

assert "abiFilters 'arm64-v8a'" in gradle
assert "applicationId 'dev.skyrimsemobile.launcher'" in gradle
assert "versionCode 56" in gradle and "versionName '2.33.0'" in gradle
assert "android:exported=\"false\"" in manifest
assert "pull_request:" in workflow and "branches: [main]" in workflow
assert "needs: jvm-regressions" in workflow
assert "android-emulator-ui:" not in workflow, "Use the real device's in-app tests"
assert "bash mobile/ci/run_jvm_suite.sh" in workflow
assert "bash mobile/ci/verify_apk.sh" in workflow
assert "v215_device_autotest_contract_test.py" in workflow
assert "Skyrim-SE-v2.33-CI-ARM64-APK" in workflow
assert "*Test.java" in jvm and "java -ea" in jvm
assert "DeviceSelfTestReport" in jvm
assert "FpsAutotestSequence" in jvm
assert "VulkanFrameSanity" in jvm
assert "DdsAlphaCoverage" in jvm
assert "v221_dds_alpha_contract_test.py" in workflow
assert "v222_pixel_footprint_contract_test.py" in workflow
assert "v223_geometry_camera_contract_test.py" in workflow
assert "v224_compact_viewport_contract_test.py" in workflow
assert "v225_optional_alpha_blend_contract_test.py" in workflow
assert "v226_manual_viewport_contract_test.py" in workflow
assert "v220_frame_pixelcopy_contract_test.py" in workflow
assert "NifHierarchyTransforms" in jvm
assert "NifCompositeNormalAtlas" in jvm
assert "v218_normal_atlas_contract_test.py" in workflow
assert "v219_paired_material_contract_test.py" in workflow
assert "v217_nif_hierarchy_contract_test.py" in workflow
assert "v216_fps_matrix_contract_test.py" in workflow
assert "lib/arm64-v8a/libskyrimse_scene.so" in verify
assert "grep -Eq '^lib/x86_64/'" in verify
assert "apksigner" in verify
assert "versionCode='56'" in verify
assert "textured.frag:frag" in shaders
assert "textured_safe.vert:vert" in shaders
assert "textured_safe.frag:frag" in shaders
assert "textured_legacy.frag:frag" in shaders
assert "spirv-val --target-env vulkan1.0" in shaders
assert "python3 mobile/tests/v214_vulkan_fallback_contract_test.py" in workflow

tests=sorted((root/"tests").glob("*Test.java"))
assert len(tests)>=30, f"Expected 25 JVM programs, got {len(tests)}"
for test in tests:
    assert re.search(r"\bstatic\s+void\s+main\s*\(",test.read_text(encoding="utf-8")),test
print(f"PASS CI checks: {len(tests)} JVM tests, signed ARM64 APK and in-app phone runner")
