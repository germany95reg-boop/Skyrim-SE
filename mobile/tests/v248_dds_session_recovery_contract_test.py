"""v2.48: keep all real diffuse DDS with bounded previews; never lose exact GPU test session."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/ResourceInspectorActivity.java")
gpu=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
down=read("app/src/main/java/dev/skyrimsemobile/launcher/DdsPreviewDownsampler.java")
jtest=read("tests/DdsPreviewDownsamplerTest.java")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
assert "public static DdsBcDecoder.Image fit(" in down
assert "MAX_SIDE=512" in down
assert "alpha+=a;red+=" in down
assert "source.format+" in down
assert "DdsPreviewDownsampler.fit(" in ui
assert "totalPixels+pixels>4L*1024*1024" in ui
assert "DDS оптимизирована" in ui
assert "DdsPreviewDownsampler" in jvm
assert "DdsPreviewDownsampler.fit(source,512)" in jtest
assert "new int[2048*2048]" in jtest
assert "PREF_AUTOMATIC_PLACED_RESULT" in ui and "PREF_AUTOMATIC_PLACED_RESULT" in gpu
assert 'backup.put("token",placedPreviewToken)' in gpu
assert 'backup.put("stats",attached)' in gpu
assert 'backup.put("success",success)' in gpu
assert ".commit();" in gpu
assert "token.equals(backup.optString(" in ui
assert "sessionCompleted=backup.optBoolean(" in ui
assert "if(automaticPlacedTest&&!deviceAutotestFinished)" in gpu
assert "Дождись завершения трёх режимов FPS" in gpu
assert "if(!token.equals(delivered))" in ui
assert "Автоматический Vulkan-тест прерван" in ui
assert "versionCode 74" in gradle and "versionName '2.51.0'" in gradle
assert "Skyrim-SE-Mobile-v2.51.0-ARM64.apk" in workflow
assert "v248_dds_session_recovery_contract_test.py" in workflow
print("PASS v2.48 bounded DDS previews, full session backup + explicit interrupted GPS test")
