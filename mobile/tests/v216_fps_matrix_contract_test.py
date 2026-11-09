"""Contract for real-device 30 / 60 / unlimited native Vulkan FPS measurement."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
sequence=read("app/src/main/java/dev/skyrimsemobile/launcher/FpsAutotestSequence.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestActivity.java")
gradle=read("app/build.gradle")
jvm=read("ci/run_jvm_suite.sh")
manifest=read("app/src/main/AndroidManifest.xml")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
workflow=(root.parent/".github/workflows/android-quality-gates.yml").read_text(encoding="utf-8")

checks={
    "three FPS modes": "CAPS={30,60,0}" in sequence,
    "distinct warmup":"WARMUP_MS=750" in sequence,
    "min valid sample":"MIN_SAMPLE_MS=1800" in sequence,
    "bounded 30s GPU test":"MAX_TOTAL_MS=30000" in sequence,
    "no stale samples on swapchain recreation":"if(totalFrames<lastFrames)" in sequence,
    "frame delta accounting":"totalFrames-baselineFrames" in sequence,
    "real wall-clock elapsed accounting":"nowMs-baselineMs" in sequence,
    "cap verification":"nativeCap==requestedCap()" in sequence,
    "full stage results":"Collections.unmodifiableList(results)" in sequence,
    "no synthetic FPS estimate":"frames*1000.0/elapsedMs" in sequence,
    "JNI FPS cap supports zero":"cap == 0 || cap == 30 || cap == 60" in native,
    "native actual cap published":r'\"fpsCap\"' in native,
    "native cumulative frame count":"renderedFrames" in native,
    "on-device sequencing":"fpsSequence.sample(" in scene,
    "native FPS cap changes":"fpsCap=fpsSequence.requestedCap();" in scene and
                             "nativeSetFpsCap(fpsCap)" in scene,
    "real native cap read":"info.optInt(\"fpsCap\",-1)" in scene,
    "autotest not single FPS":"fpsSequence.complete()&&frames>=45" in scene,
    "reported three independent values":"stage.put(\"fps\",r.fps)" in scene,
    "reported per-stage frame counts":"stage.put(\"frames\",r.frames)" in scene,
    "reported sample duration":"stage.put(\"sampleMillis\",r.elapsedMs)" in scene,
    "returned partial values on failure":"attachFpsMeasurements(snapshot)" in scene,
    "on-device report FPS 30/60/uncapped":"int[] expected={30,60,0}" in report,
    "false PASS prevented":"stage.optBoolean(\"passed\",false)" in report,
    "explicit uncapped wording":"Заданный лимит: %s" in report,
    "inplace Android update":"versionCode 60" in gradle and
                           "versionName '2.37.0'" in gradle,
    "persist signing":"signingConfig signingConfigs.stableDebug" in gradle,
    "never include emulator job":"android-emulator-ui:" not in workflow,
    "read only game files":"android.permission.WRITE_EXTERNAL_STORAGE" not in manifest,
    "JVM regression on every PR":"FpsAutotestSequence" in jvm,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.16 native on-device 30/60/uncapped FPS matrix contracts:",len(checks))
