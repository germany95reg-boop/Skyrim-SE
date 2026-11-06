package dev.skyrimsemobile.launcher;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Real Android emulator smoke checks. No Skyrim data is required.
 * Run against the app's actual Activities, callbacks, and native library.
 * Pure parser/texture correctness is covered by the JVM test suite.
 */
@RunWith(AndroidJUnit4.class)
public final class AndroidUiSmokeTest {
    private static Button button(View root,String label){
        if(root instanceof Button && label.equals(((Button)root).getText().toString()))
            return (Button)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                Button found=button(group.getChildAt(i),label);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private static boolean contains(View root,String fragment){
        if(root instanceof TextView &&
           ((TextView)root).getText().toString().contains(fragment))return true;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)
                if(contains(group.getChildAt(i),fragment))return true;
        }
        return false;
    }
    private static WebView web(View root){
        if(root instanceof WebView)return (WebView)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                WebView found=web(group.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    @Test public void catalogButtonsAreWiredAndHandleMissingBsaSafely(){
        try(ActivityScenario<BsaCatalogActivity> scenario=
                ActivityScenario.launch(BsaCatalogActivity.class)){
            scenario.onActivity(activity->{
                View root=activity.getWindow().getDecorView();
                assertNotNull("BSA picker",button(root,"Выбрать архив BSA"));
                assertNotNull("NIF part picker",
                    button(root,"Части NIF · выбрать BSTriShape · v2.11"));
                assertNotNull("v2.12 per-part DDS",
                    button(root,"Показать выбранную часть NIF с её DDS · v2.12"));
                assertNotNull("v2.13 composite DDS",
                    button(root,"Показать ВСЕ части NIF с DDS · v2.13"));
                button(root,"Показать ВСЕ части NIF с DDS · v2.13").performClick();
                assertTrue("Missing BSA shows actionable feedback",
                    contains(root,"Сначала выбери NIF"));
                button(root,"Найти DDS каждой части NIF · v2.11").performClick();
                assertTrue("Missing archive is handled without a crash",
                    contains(root,"Сначала выбери архив BSA"));
            });
            // Exercise restoring the Activity without any SAF permissions or game data.
            scenario.recreate();
            scenario.onActivity(activity->assertNotNull(
                button(activity.getWindow().getDecorView(),
                    "Показать ВСЕ части NIF с DDS · v2.13")));
        }
    }

    @Test public void launcherUsesOnlyBundledWebViewWithRestrictedFileAccess(){
        try(ActivityScenario<MainActivity> scenario=
                ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{
                WebView view=web(activity.getWindow().getDecorView());
                assertNotNull("Launcher WebView",view);
                assertTrue("JS bridge is enabled for bundled asset",
                    view.getSettings().getJavaScriptEnabled());
                assertFalse("No arbitrary file-to-file access",
                    view.getSettings().getAllowFileAccessFromFileURLs());
                assertFalse("No file-to-remote-origin access",
                    view.getSettings().getAllowUniversalAccessFromFileURLs());
            });
        }
    }

    @Test public void vulkanViewerControlsAndLifecycleDoNotRequireGameAssets(){
        try(ActivityScenario<VulkanSceneActivity> scenario=
                ActivityScenario.launch(VulkanSceneActivity.class)){
            scenario.onActivity(activity->{
                View root=activity.getWindow().getDecorView();
                assertTrue("Version label updated",
                    contains(root,"Vulkan 3D · v2.13"));
                assertNotNull("FPS button",button(root,"FPS: 60"));
                assertNotNull("Fit model",button(root,"Вписать"));
                assertNotNull("View from front",button(root,"Спереди"));
                assertNotNull("Back navigation",button(root,"← Назад"));
            });
            // Android lifecycle must stop and restart the Vulkan surface safely.
            scenario.recreate();
            scenario.onActivity(activity->assertNotNull(
                button(activity.getWindow().getDecorView(),"Вписать")));
        }
    }
}
