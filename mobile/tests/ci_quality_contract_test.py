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
assert "versionCode 74" in gradle and "versionName '2.51.0'" in gradle
assert "android:exported=\"false\"" in manifest
assert re.search(r"(?m)^on:\n  workflow_dispatch:\s*\n",workflow)
assert not re.search(r"(?m)^  (push|pull_request):",workflow)
assert "needs: [jvm-regressions, source-contracts]" in workflow
assert "  source-contracts:" in workflow
assert "  android-compile-check:" in workflow
assert "Compile Android ARM64 independently of contract gates" in workflow
assert "for test in mobile/tests/v2[0-9]*_contract_test.py" in workflow
assert "android-emulator-ui:" not in workflow, "Use the real device's in-app tests"
assert "bash mobile/ci/run_jvm_suite.sh" in workflow
assert "bash mobile/ci/verify_apk.sh" in workflow
assert "v215_device_autotest_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "gh release create" not in workflow and "gh release upload" not in workflow
assert "actions/upload-artifact@v4" not in workflow

# Evaluate the actual concurrency template so a PR cannot cancel its branch's APK push.
concurrency=re.search(r"(?ms)^concurrency:\n(.*?)(?=^\S|\Z)",workflow)
assert concurrency, "Missing workflow concurrency policy"
group=re.search(r"(?m)^  group:\s*(.+)$",concurrency.group(1))
assert group, "Missing workflow concurrency group"
assert re.search(r"(?m)^  cancel-in-progress: true\s*$",concurrency.group(1))
def concurrency_group(event,branch,run_id):
    fields={
        "github.workflow":"Skyrim quality", "github.event_name":event,
        "github.head_ref":branch if event=="pull_request" else "",
        "github.ref_name":"35/merge" if event=="pull_request" else branch,
        "github.run_id":str(run_id),
    }
    def resolve(match):
        return next((fields[name.strip()] for name in match.group(1).split("||")
                     if fields[name.strip()]),"")
    return re.sub(r"\$\{\{\s*(.*?)\s*\}\}",resolve,group.group(1))
branch="dev/v2.51-stabilization"
push_group=concurrency_group("push",branch,1)
assert push_group!=concurrency_group("pull_request",branch,2), "PR would cancel APK push"
assert push_group==concurrency_group("push",branch,3), "Repeated pushes must cancel stale runs"
assert push_group!=concurrency_group("push","dev/other-branch",4), "Branches must run independently"

assert "*Test.java" in jvm and "java -ea" in jvm
assert "DeviceSelfTestReport" in jvm
assert "FpsAutotestSequence" in jvm
assert "VulkanFrameSanity" in jvm
assert "DdsAlphaCoverage" in jvm
assert "v221_dds_alpha_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v222_pixel_footprint_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v223_geometry_camera_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v224_compact_viewport_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v225_optional_alpha_blend_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v226_manual_viewport_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v220_frame_pixelcopy_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "NifHierarchyTransforms" in jvm
assert "NifCompositeNormalAtlas" in jvm
assert "v218_normal_atlas_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v219_paired_material_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v217_nif_hierarchy_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "v216_fps_matrix_contract_test.py" in {p.name for p in (root/"tests").glob("*contract_test.py")}
assert "lib/arm64-v8a/libskyrimse_scene.so" in verify
assert "grep -Eq '^lib/x86_64/'" in verify
assert "apksigner" in verify
assert "versionCode='74'" in verify
assert "textured.frag:frag" in shaders
assert "textured_safe.vert:vert" in shaders
assert "textured_safe.frag:frag" in shaders
assert "textured_legacy.frag:frag" in shaders
assert "spirv-val --target-env vulkan1.0" in shaders
assert (root/"tests/v214_vulkan_fallback_contract_test.py").is_file()

tests=sorted((root/"tests").glob("*Test.java"))
assert len(tests)>=30, f"Expected 25 JVM programs, got {len(tests)}"
for test in tests:
    assert re.search(r"\bstatic\s+void\s+main\s*\(",test.read_text(encoding="utf-8")),test
print(f"PASS CI checks: {len(tests)} JVM tests, signed ARM64 APK and in-app phone runner")
