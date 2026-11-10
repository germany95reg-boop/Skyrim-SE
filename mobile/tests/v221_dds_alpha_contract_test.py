"""v2.21: transparent diffuse DDS pixels must cut out on ALL Vulkan shader paths."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
read=lambda p:(r/p).read_text(encoding="utf-8")
s={
 "full":read("app/src/main/shaders/textured.frag"),
 "normal-safe":read("app/src/main/shaders/textured_normal_safe.frag"),
 "atlas-safe":read("app/src/main/shaders/textured_safe.frag"),
 "basic":read("app/src/main/shaders/textured_legacy.frag"),
}
alpha=read("app/src/main/java/dev/skyrimsemobile/launcher/DdsAlphaCoverage.java")
runner=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestRunner.java")
ui=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
native=read("app/src/main/cpp/vulkan_scene_test.cpp")
jvm=read("ci/run_jvm_suite.sh")
gradle=read("app/build.gradle")
ci=read("../.github/workflows/android-quality-gates.yml")
manifest=read("app/src/main/AndroidManifest.xml")
checks={
 "cutout in all four sampler shaders":all("if(base.a < 0.05) discard;" in v for v in s.values()),
 "each only one alpha discard":all(v.count("if(base.a < 0.05) discard;")==1 for v in s.values()),
 "full derivatives before discard":s["full"].index("dFdx(viewPosition)") <
     s["full"].index("if(base.a < 0.05) discard;"),
 "Adreno normal-safe derivatives before discard":s["normal-safe"].index("dFdx(viewPosition)") <
     s["normal-safe"].index("if(base.a < 0.05) discard;"),
 "Adreno normal-safe retains bump":'options.normalEnabled!=0' in s["normal-safe"],
 "fallback retains no derivative":'dFdx' not in s["atlas-safe"],
 "basic keeps one sampler":s["basic"].count("uniform sampler2D")==1,
 "no alpha blend promised":'not translucent alpha blending' in alpha,
 "CPU threshold matches 5 percent shader":'CUTOUT_ALPHA_EXCLUSIVE=13' in alpha,
 "normal gloss not treated as opacity":'perShape.values()' in alpha and
     'perNormal.values()' not in alpha,
 "bounded first mip processing":'MAX_SOURCE_PIXELS=8L*1024*1024' in alpha,
 "cache identical diffuse DDS":'IdentityHashMap<DdsBcDecoder.Image,Sample>' in alpha,
 "report alpha coverage after DDS decode":'report.pass("Diffuse DDS альфа-маска"' in runner,
 "manual BSA atlas reports masks":'DdsAlphaCoverage.inspect(perShape)' in ui,
 "no fake multiple diffuse DDS claim":'с несколькими diffuse DDS' not in runner,
 "NIF material pairs retained":'NifCompositeDdsAtlas.buildPaired(' in runner,
 "original FPS and PixelCopy unchanged":'fpsSequence.sample(' in scene and
     'PixelCopy.request(frameSurface,pixels' in scene,
 "native Vulkan remains enabled":'vkCreateGraphicsPipelines' in native,
 "new pure Java coverage included":'DdsAlphaCoverage' in jvm,
 "alpha JVM test included":(r/"tests/DdsAlphaCoverageTest.java").exists(),
 "new source checks included":'v221_dds_alpha_contract_test.py' in ci,
 "signing identity v2.21":"versionCode 72" in gradle and
     "versionName '2.49.0'" in gradle,
 "read-only SAF retained":'openOutputStream' not in runner,
 "no internet permission":'android.permission.INTERNET' not in manifest,
 "manual mask includes only diffuse source":'DdsAlphaCoverage.inspect(perShape)' in runner,
}
assert all(checks.values()),[k for k,v in checks.items() if not v]
print("PASS v2.21 diffuse DDS alpha-cutout / Adreno fallback source contracts:",len(checks))
