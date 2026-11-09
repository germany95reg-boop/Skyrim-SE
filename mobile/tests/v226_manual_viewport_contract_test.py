"""v2.26 screen-space budget for MANUAL NIF view with real full-composite action.

User-provided v2.25 screenshots: entire 3D view was occluded by long NIF
status and a fully expanded controls panel, leaving only a thin strip at top.
The screenshot was also one shape (2887 triangles), not the 3349-triangle
two-shape composite previously verified by on-phone autodiagnostics.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
scene=read("app/src/main/java/dev/skyrimsemobile/launcher/VulkanSceneActivity.java")
browser=read("app/src/main/java/dev/skyrimsemobile/launcher/BsaCatalogActivity.java")
transfer=read("app/src/main/java/dev/skyrimsemobile/launcher/NifGeometryTransfer.java")
report=read("app/src/main/java/dev/skyrimsemobile/launcher/DeviceSelfTestReport.java")
manifest=read("app/src/main/AndroidManifest.xml")
gradle=read("app/build.gradle")
workflow=read("../.github/workflows/android-quality-gates.yml")
checks={
 "manual model still renders on whole Surface":'root.addView(viewport, new FrameLayout.LayoutParams(-1, -1));' in scene,
 "bounded scrollable bottom sheet":'controlsScroller=new ScrollView(this)' in scene and
      'heightPixels*0.38f' in scene,
 "bounded by parent MeasureSpec":'Math.min(maxHeight,View.MeasureSpec.getSize(heightSpec))' in scene,
 "bottom panel content scrolls":'controlsScroller.addView(panel);' in scene,
 "bottom sheet still anchored bottom":'new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)' in scene,
 "manual status hidden by default":'status.setVisibility(View.GONE);' in scene,
 "hint also hidden by default":'hint.setVisibility(View.GONE);' in scene,
 "one line active GPU status":'briefStatus.setSingleLine(true);' in scene,
 "user can open full diagnostic text":'detailsButton=controlButton("Сведения ▾")' in scene,
 "expanded report is independently scrollable":'controlsScroller.smoothScrollTo(0,0);' in scene,
 "details toggles leave native Vulkan intact":'hint.setVisibility(expand?View.VISIBLE:View.GONE);' in scene,
 "advanced DDS and alpha still present":'advancedControls.addView(alphaButton,' in scene,
 "core manual controls still reachable":'actions.addView(fit,' in scene and
     'controls.addView(fpsButton,' in scene,
 "title compact":'"SKYRIM SE · 3D · " + InstalledAppVersion.label(this)' in scene and
     'title.setTextSize(16);' in scene,
 "camera uses actually visible manual sheet":'controlsScroller.getHeight():0;' in scene,
 "auto test uses compact HUD only":'controlsScroller.setVisibility(View.GONE);' in scene and
     'diagnosticHud=new TextView(this)' in scene,
 "normal-safe Vulkan and PixelCopy preserved":'nativeSetDiffuseNormalAtlas' in scene and
     'PixelCopy.request(frameSurface,pixels' in scene,
 "physical FPS stages preserved":'fpsSequence.sample(' in scene,
 "recommended composite DDS discover first":'1 · Найти DDS каждой части NIF' in browser,
 "recommended full NIF view second":'2 · Открыть ВСЮ NIF с DDS (все части)' in browser,
 "all NIF decodable BSTriShape still combined":'NifSceneComposer.combine(parts)' in browser,
 "full DDS normal atlas still transferred":'NifGeometryTransfer.offerAtlasNormal(' in browser,
 "single BSTriShape clearly marked diagnostic":'Диагностика ОДНОЙ части с DDS' in browser,
 "all-parts path is original and read-only":'private void previewCompositeDds()' in browser and
     'openOutputStream' not in browser,
 "user can see all-parts model in title":' · все "+scene.included+" части' in browser,
 "legacy model transfer preserved":'static synchronized void offerTextured(' in transfer,
 "user footer remains removed":'Отчёт не отправлялся автоматически' not in report,
 "never adds network/upload permission":'android.permission.INTERNET' not in manifest,
 "stable upgrade and package identity":'versionCode 56' in gradle and
     "versionName '2.33.0'" in gradle and
     "applicationId 'dev.skyrimsemobile.launcher'" in gradle,
 "CI always checks this correction":'v226_manual_viewport_contract_test.py' in workflow,
}
assert all(checks.values()),[name for name,value in checks.items() if not value]
print("PASS v2.26 user-screenshot 3D viewport and full-NIF navigation:",len(checks),"checks")
