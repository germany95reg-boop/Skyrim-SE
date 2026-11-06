"""Prevent silent loss of test coverage or accidentally shipping an emulator APK."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")

gradle = read("app/build.gradle")
manifest = read("app/src/main/AndroidManifest.xml")
workflow = (root.parent / ".github/workflows/android-quality-gates.yml").read_text(encoding="utf-8")
jvm = read("ci/run_jvm_suite.sh")
verify = read("ci/verify_apk.sh")
shaders = read("ci/compile_shaders.sh")
ui = read("app/src/androidTest/java/dev/skyrimsemobile/launcher/AndroidUiSmokeTest.java")

assert "abiFilters 'arm64-v8a'" in gradle
assert "if (project.hasProperty('ciEmulator')) abiFilters 'x86_64'" in gradle
assert "androidx.test.runner.AndroidJUnitRunner" in gradle
assert "applicationId 'dev.skyrimsemobile.launcher'" in gradle
assert "versionCode 37" in gradle and "versionName '2.14.0'" in gradle
assert "android:exported=\"false\"" in manifest
assert "github.ref" in workflow and "pull_request:" in workflow
assert "needs: jvm-regressions" in workflow
assert "needs: android-arm64-apk" in workflow
assert "bash mobile/ci/run_jvm_suite.sh" in workflow
assert "bash mobile/ci/verify_apk.sh" in workflow
assert "-PciEmulator=true :app:connectedDebugAndroidTest" in workflow
assert "-partition-size 2048" in workflow, "Emulator must fit hosted-runner disk budget"
assert "if: always()" in workflow and "test-results" in workflow
assert "*Test.java" in jvm and "java -ea" in jvm
assert 'lib/arm64-v8a/libskyrimse_scene.so' in verify
assert "grep -Eq '^lib/x86_64/'" in verify
assert "apksigner" in verify
assert "textured.frag:frag" in shaders
assert "textured_safe.vert:vert" in shaders
assert "textured_safe.frag:frag" in shaders
assert "textured_legacy.frag:frag" in shaders
assert "spirv-val --target-env vulkan1.0" in shaders
assert "python3 mobile/tests/v214_vulkan_fallback_contract_test.py" in workflow
assert "ActivityScenario.launch(BsaCatalogActivity.class)" in ui
assert "ActivityScenario.launch(MainActivity.class)" in ui
assert "ActivityScenario.launch(VulkanSceneActivity.class)" in ui

tests = sorted((root / "tests").glob("*Test.java"))
assert len(tests) >= 24, f"Only {len(tests)} tests; review missing tests"
for test in tests:
    assert re.search(r"\bstatic\s+void\s+main\s*\(", test.read_text(encoding="utf-8")), (
        f"Test has no executable entrypoint: {test}"
    )
print(f"PASS CI guardrails: {len(tests)} JVM suites, 3 Android UI smoke checks, APK/ABI/signing")
