package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.InputStream;
import java.io.IOException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** A real animated Vulkan cube test. It does not load a Skyrim executable or any game assets. */
public final class VulkanSceneActivity extends Activity implements SurfaceHolder.Callback {
    public static final String PREF_RESULT = "vulkan_scene_test_result";
    private static String loadingError;
    static {
        try { System.loadLibrary("skyrimse_scene"); }
        catch (Throwable t) { loadingError = t.toString(); }
    }
    private static native void nativeResetScene();
    private static native void nativeStopScene();
    private static native void nativeRunScene(Surface surface, AssetManager assets);
    private static native String nativeGetStats();
    private static native void nativeSetView(float yaw, float pitch, float distance, boolean autoRotate);
    private static native void nativeSetFpsCap(int cap);
    private static native void nativeSetViewportOffset(float ndcY);
    private static native boolean nativeSetGeometry(float[] vertices, String label);
    private static native void nativeClearGeometry();
    private static native boolean nativeSetSurfaceTriangles(float[] triangles);
    private static native boolean nativeSetSolidMode(boolean enabled);
    private static native boolean nativeSetTexture(float[] uv,int[] argb,int width,int height);
    private static native void nativeSetUseTexture(boolean enabled);
    private static native boolean nativeSetNormalTexture(int[] argb,int width,int height);
    private static native void nativeSetUseNormalMap(boolean enabled);
    private static native void nativeSetLightMode(int mode);
    private static native void nativeSetNormalStrength(float strength);
    private static native void nativeSetSpecularStrength(float strength);
    private static native void nativeSetSpecularSize(int size);
    private static final int PICK_OBJ = 606;
    private String meshLabel = "Куб (встроенный)";
    private String nifDimensions = "";
    private String viewPreset = "Свободный";
    private boolean loadingModel = false;
    private FrameLayout sceneRoot;
    private TextView sceneTitle;
    private LinearLayout scenePanel;
    private float modelRadius=SceneFraming.CUBE_RADIUS;
    private boolean fitFollowsLayout=true;
    private String framingInfo="";
    private Button modelButton;
    private Button surfaceButton;
    private Button textureButton;
    private Button normalButton;
    private Button lightButton,reliefButton;
    private Button specularButton,specularSizeButton;
    private int lightMode=0,reliefPreset=1;
    private int specularPreset=0,specularSizePreset=1;
    private boolean hasSurface = false;
    private boolean hasTexture = false,hasNormalMap=false;
    private boolean solidMode = false,useTexture = false,useNormalMap=false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!destroyed && loadingError == null) {
                try {
                    String json = nativeGetStats();
                    JSONObject info = new JSONObject(json);
                    boolean error = info.optBoolean("error", false);
                    boolean running = info.optBoolean("running", false);
                    String message = info.optString("message", "");
                    int w = info.optInt("width"), h = info.optInt("height");
                    double fps = info.optDouble("fps");
                    long frames = info.optLong("frames");
                    status.setText(message + "\n" + (running ? String.format(java.util.Locale.US,
                        "FPS: %.1f · Кадров: %d · %d×%d\nУгол: %.1f · Наклон: %.1f · Дистанция: %.1f\nГеометрия: %s · Рёбер: %d",
                        fps, frames, w, h, info.optDouble("yaw"), info.optDouble("pitch"),
                        info.optDouble("distance"), info.optString("geometry","Куб"), info.optInt("edges")) : "") +
                        (nifDimensions.isEmpty() ? "" : "\n"+nifDimensions) +
                        "\nРежим: " + (info.optBoolean("solidMode") ?
                          (hasTexture&&useTexture?"Поверхность · DDS":"Поверхность") : "Каркас") +
                        " · Треугольников: " + info.optInt("triangles") + "\nВид: "+viewPreset +
                        (framingInfo.isEmpty()?"":"\n"+framingInfo));
                    status.setTextColor(error ? 0xffffabab : 0xffb4d8fd);
                    if (frames > 0 && !error) {
                        getSharedPreferences("launcher_state", MODE_PRIVATE).edit()
                            .putString(PREF_RESULT, json).apply();
                    }
                } catch (Throwable e) {
                    status.setText("Ошибка диагностики: " + e.getClass().getSimpleName());
                }
                ui.postDelayed(this, 700);
            }
        }
    };
    private SurfaceHolder currentSurface;
    private Thread worker;
    private TextView status;
    private boolean paused = true;
    private boolean destroyed;
    private boolean surfaceReady;
    private boolean restartPending;
    private float yaw = 0.0f, pitch = 0.42f, zoom = 4.4f;
    private float pointerX, pointerY, pinchSpan;
    private boolean autoSpin = true;
    private int fpsCap = 60;
    private Button autoButton, fpsButton;
    private int surfaceW, surfaceH;
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);
        FrameLayout root = new FrameLayout(this);
        sceneRoot=root;
        root.setBackgroundColor(0xff101115);
        SurfaceView viewport = new SurfaceView(this);
        viewport.getHolder().addCallback(this);
        viewport.setContentDescription("Тест трёхмерной геометрии через Vulkan — жестами можно менять камеру");
        viewport.setOnTouchListener((view, event) -> onSceneTouch(event));
        root.addView(viewport, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("SKYRIM SE MOBILE\nVulkan 3D · v2.5");
        sceneTitle=title;
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(0xde11121b);
        title.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scenePanel=panel;
        panel.setPadding(dp(12), dp(10), dp(12), dp(8));
        panel.setBackgroundColor(0xe914151c);
        status = new TextView(this);
        status.setText("Ожидание Android Surface…");
        status.setTextColor(0xffd8bfef);
        status.setTextSize(13);
        status.setPadding(dp(4), dp(3), dp(4), dp(10));
        panel.addView(status);
        TextView hint = new TextView(this);
        hint.setText("1 палец — вращать · 2 пальца — приблизить / отдалить");
        hint.setTextSize(12);
        hint.setTextColor(0xffdddddd);
        hint.setPadding(dp(4), dp(1), dp(4), dp(4));
        panel.addView(hint);
        LinearLayout controls = new LinearLayout(this);
        autoButton = controlButton("Авто: Вкл");
        autoButton.setOnClickListener(v -> { autoSpin = !autoSpin; updateControls(); });
        controls.addView(autoButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        fpsButton = controlButton("FPS: 60");
        fpsButton.setOnClickListener(v -> { fpsCap = fpsCap == 30 ? 60 : fpsCap == 60 ? 0 : 30; updateControls(); });
        controls.addView(fpsButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button reset = controlButton("Сброс");
        reset.setOnClickListener(v -> { yaw = 0.0f; pitch = 0.42f; viewPreset="Свободный"; fitFollowsLayout=true; refreshFraming(); updateControls(); });
        controls.addView(reset, new LinearLayout.LayoutParams(0, dp(46), 1));
        panel.addView(controls);
        LinearLayout meshControls = new LinearLayout(this);
        modelButton = controlButton("Выбрать OBJ");
        modelButton.setOnClickListener(v -> chooseObj());
        meshControls.addView(modelButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button pyramid = controlButton("Пример: пирамида");
        pyramid.setOnClickListener(v -> loadPyramid());
        meshControls.addView(pyramid, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button cube = controlButton("Куб");
        cube.setOnClickListener(v -> {
            nativeClearGeometry(); hasSurface=false;hasTexture=false;hasNormalMap=false;useTexture=false;useNormalMap=false;solidMode=false;updateSurfaceButton(); meshLabel = "Куб (встроенный)"; nifDimensions=""; modelRadius=SceneFraming.CUBE_RADIUS; fitFollowsLayout=true; refreshFraming();
            getSharedPreferences("launcher_state",MODE_PRIVATE).edit().remove("obj_uri").apply();
            restartRenderer();
        });
        meshControls.addView(cube, new LinearLayout.LayoutParams(0, dp(46), 1));
        // OBJ, cube and pyramid remain available in the optional panel.
        LinearLayout actions = new LinearLayout(this);
        Button back = new Button(this);
        back.setText("← Назад");
        back.setContentDescription("Вернуться на предыдущий экран без закрытия приложения");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        Button fit=controlButton("Вписать");
        fit.setContentDescription("Поместить модель целиком между заголовком и панелью управления");
        fit.setOnClickListener(v -> { autoSpin=false; fitFollowsLayout=true; refreshFraming(); updateControls(); });
        actions.addView(fit,new LinearLayout.LayoutParams(0,dp(44),1));
        Button closer=controlButton("Крупнее");
        closer.setContentDescription("Приблизить модель на один шаг; кнопка Вписать возвращает безопасный масштаб");
        closer.setOnClickListener(v->{
            autoSpin=false;
            fitFollowsLayout=false;
            zoom=SceneFraming.closer(zoom);
            refreshFraming();
            updateControls();
        });
        actions.addView(closer,new LinearLayout.LayoutParams(0,dp(44),1));
        surfaceButton=controlButton("Поверхность");
        surfaceButton.setEnabled(false);
        surfaceButton.setContentDescription("Переключить каркас и цельные треугольники NIF");
        surfaceButton.setOnClickListener(v -> {
            if(!hasSurface)return;
            if(nativeSetSolidMode(!solidMode)) {solidMode=!solidMode;updateSurfaceButton();}
            else status.setText("Невозможно переключить Vulkan-режим");
        });
        actions.addView(surfaceButton,new LinearLayout.LayoutParams(0,dp(44),1));
        textureButton=controlButton("DDS: Выкл");
        textureButton.setEnabled(false);
        textureButton.setOnClickListener(v->{
            if(!hasTexture)return;
            useTexture=!useTexture;
            nativeSetUseTexture(useTexture);
            updateSurfaceButton();
        });
        actions.addView(textureButton,new LinearLayout.LayoutParams(0,dp(44),1));
        normalButton=controlButton("Нормали: Выкл");
        normalButton.setEnabled(false);
        normalButton.setOnClickListener(v->{
            if(!hasNormalMap)return;
            useNormalMap=!useNormalMap;
            nativeSetUseNormalMap(useNormalMap);
            updateSurfaceButton();
        });

        // Move navigation to its own short, unwrapped footer row.
        LinearLayout views = new LinearLayout(this);
        Button front = controlButton("Спереди");
        front.setOnClickListener(v -> setCameraPreset("Спереди",0f,0f));
        views.addView(front,new LinearLayout.LayoutParams(0,dp(42),1));
        Button side = controlButton("Сбоку");
        side.setOnClickListener(v -> setCameraPreset("Сбоку",(float)(Math.PI/2),0f));
        views.addView(side,new LinearLayout.LayoutParams(0,dp(42),1));
        Button top = controlButton("Сверху");
        top.setOnClickListener(v -> setCameraPreset("Сверху",0f,1.3f));
        views.addView(top,new LinearLayout.LayoutParams(0,dp(42),1));
        panel.addView(views);
        // Keep normal-map control readable and camera visible on tall phones.
        panel.addView(normalButton,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout lightingControls=new LinearLayout(this);
        lightButton=controlButton(PreviewLighting.lightLabel(lightMode));
        lightButton.setEnabled(false);
        lightButton.setOnClickListener(v->{
            if(!hasTexture)return;
            lightMode=PreviewLighting.nextLight(lightMode);
            nativeSetLightMode(lightMode);
            updateSurfaceButton();
        });
        lightingControls.addView(lightButton,new LinearLayout.LayoutParams(0,dp(40),1));
        reliefButton=controlButton(PreviewLighting.reliefLabel(reliefPreset));
        reliefButton.setEnabled(false);
        reliefButton.setOnClickListener(v->{
            if(!hasNormalMap)return;
            reliefPreset=PreviewLighting.nextRelief(reliefPreset);
            nativeSetNormalStrength(PreviewLighting.reliefStrength(reliefPreset));
            updateSurfaceButton();
        });
        lightingControls.addView(reliefButton,new LinearLayout.LayoutParams(0,dp(40),1));
        // Lighting is accessible from "Параметры", hidden by default for a
        // larger 3D viewport. No native state is modified by collapsing UI.
        // Material controls are independent of mesh geometry and only active when
        // a normal DDS supplies a per-pixel alpha mask for the preview highlight.
        LinearLayout materialControls=new LinearLayout(this);
        specularButton=controlButton(PreviewMaterial.specularLabel(specularPreset));
        specularButton.setEnabled(false);
        specularButton.setOnClickListener(v->{
            if(!hasNormalMap)return;
            specularPreset=PreviewMaterial.nextSpecular(specularPreset);
            nativeSetSpecularStrength(PreviewMaterial.specularStrength(specularPreset));
            updateSurfaceButton();
        });
        materialControls.addView(specularButton,new LinearLayout.LayoutParams(0,dp(40),1));
        specularSizeButton=controlButton(PreviewMaterial.sizeLabel(specularSizePreset));
        specularSizeButton.setEnabled(false);
        specularSizeButton.setOnClickListener(v->{
            if(!hasNormalMap)return;
            specularSizePreset=PreviewMaterial.nextSize(specularSizePreset);
            nativeSetSpecularSize(specularSizePreset);
            updateSurfaceButton();
        });
        materialControls.addView(specularSizeButton,new LinearLayout.LayoutParams(0,dp(40),1));
        panel.addView(actions);
        LinearLayout advancedControls = new LinearLayout(this);
        advancedControls.setOrientation(LinearLayout.VERTICAL);
        advancedControls.addView(lightingControls);
        advancedControls.addView(materialControls);
        advancedControls.addView(meshControls);
        advancedControls.setVisibility(View.GONE);
        panel.addView(advancedControls);
        LinearLayout footer = new LinearLayout(this);
        Button advancedButton=controlButton("Параметры ▾");
        advancedButton.setContentDescription("Показать или скрыть освещение, материалы и выбор OBJ");
        advancedButton.setOnClickListener(v->{
            boolean expand=advancedControls.getVisibility()!=View.VISIBLE;
            advancedControls.setVisibility(expand?View.VISIBLE:View.GONE);
            advancedButton.setText(expand?"Скрыть параметры ▴":"Параметры ▾");
            // Reframe after the panel's height actually changes.
            panel.post(this::refreshFraming);
        });
        footer.addView(advancedButton,new LinearLayout.LayoutParams(0,dp(42),1));
        footer.addView(back,new LinearLayout.LayoutParams(0,dp(42),1));
        panel.addView(footer);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        root.addOnLayoutChangeListener((v,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->refreshFraming());
        // The dynamic status text may resize the panel without changing root bounds.
        panel.addOnLayoutChangeListener((v,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->refreshFraming());
        title.addOnLayoutChangeListener((v,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->refreshFraming());
        root.post(this::refreshFraming);
        if (loadingError != null) {
            status.setText("Не удалось загрузить ARM64: " + loadingError);
            status.setTextColor(0xffffabab);
        } else {
            ui.post(poll);
            NifGeometryTransfer.Selected nif=NifGeometryTransfer.take();
            if(nif != null){
                nifDimensions=nif.details;
                activateModel(nif.model,nif.label,null,nif.surfaceTriangles,
                    nif.surfaceUVs,nif.argb,nif.textureWidth,nif.textureHeight,
                    nif.normalArgb,nif.normalWidth,nif.normalHeight);
            }
            else restoreModel();
        }
    }
    private void chooseObj() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_OBJ);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request != PICK_OBJ || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String display = getDisplayName(uri);
        if(!display.toLowerCase(java.util.Locale.ROOT).endsWith(".obj")) {
            status.setText("Для этой версии выбери файл с расширением .obj"); return;
        }
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch(SecurityException ignored) { /* Some providers only supply temporary read access. */ }
        loadModel(uri, display, true);
    }
    private String getDisplayName(Uri uri) {
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()) return cursor.getString(0);
        } catch(Exception ignored) {}
        return uri.getLastPathSegment()==null ? "model.obj" : uri.getLastPathSegment();
    }
    private void restoreModel() {
        String saved = getSharedPreferences("launcher_state", MODE_PRIVATE).getString("obj_uri", "");
        if(saved.isEmpty()) return;
        try{Uri uri=Uri.parse(saved);loadModel(uri,getDisplayName(uri),false);}
        catch(Exception e){status.setText("Не удалось открыть сохранённый OBJ: "+e.getMessage());}
    }
    private void loadPyramid() {
        if(loadingModel) return;
        loadingModel=true;modelButton.setEnabled(false);
        status.setText("Загрузка тестовой пирамиды…");
        new Thread(()-> {
            try(InputStream file=getAssets().open("pyramid.obj")) {
                ObjWireParser.Model m=ObjWireParser.parse(file);
                runOnUiThread(()->activateModel(m,"Пример: пирамида",null));
            }catch(Exception e){runOnUiThread(()->modelError(e));}
        },"obj-pyramid-reader").start();
    }
    private void loadModel(Uri uri,String name,boolean persist) {
        if(loadingModel) return;
        loadingModel=true;modelButton.setEnabled(false);
        status.setText("Читаю OBJ: "+name);
        new Thread(()-> {
            try(InputStream file=getContentResolver().openInputStream(uri)) {
                ObjWireParser.Model m=ObjWireParser.parse(file);
                runOnUiThread(()->activateModel(m,name,persist?uri:null));
            }catch(Exception e){runOnUiThread(()->modelError(e));}
        },"obj-document-reader").start();
    }
    private void modelError(Exception error) {
        loadingModel=false; modelButton.setEnabled(true);
        status.setText("Ошибка OBJ: "+error.getMessage());
    }
    private void activateModel(ObjWireParser.Model model,String name,Uri persist) {
        activateModel(model,name,persist,null);
    }
    private void updateSurfaceButton(){
        if(surfaceButton!=null){
            surfaceButton.setEnabled(hasSurface);
            surfaceButton.setText(solidMode?"Каркас":"Поверхность");
        }
        if(textureButton!=null){
            textureButton.setEnabled(hasTexture);
            textureButton.setText(useTexture?"DDS: Вкл":"DDS: Выкл");
        }
        if(normalButton!=null){
            normalButton.setEnabled(hasNormalMap);
            normalButton.setText(useNormalMap?"Нормали: Вкл":"Нормали: Выкл");
        }
        if(lightButton!=null){
            lightButton.setEnabled(hasTexture);
            lightButton.setText(PreviewLighting.lightLabel(lightMode));
        }
        if(reliefButton!=null){
            reliefButton.setEnabled(hasNormalMap);
            reliefButton.setText(PreviewLighting.reliefLabel(reliefPreset));
        }
        if(specularButton!=null){
            specularButton.setEnabled(hasNormalMap);
            specularButton.setText(PreviewMaterial.specularLabel(specularPreset));
        }
        if(specularSizeButton!=null){
            specularSizeButton.setEnabled(hasNormalMap);
            specularSizeButton.setText(PreviewMaterial.sizeLabel(specularSizePreset));
        }
    }
    private void activateModel(ObjWireParser.Model model,String name,Uri persist,float[] triangles) {
        activateModel(model,name,persist,triangles,null,null,0,0,null,0,0);
    }
    private void activateModel(ObjWireParser.Model model,String name,Uri persist,float[] triangles,
           float[] uv,int[] pixels,int textureW,int textureH,
           int[] normalPixels,int normalW,int normalH) {
        loadingModel=false;modelButton.setEnabled(true);
        if(destroyed || loadingError!=null)return;
        boolean accepted=nativeSetGeometry(model.segments,name);
        if(!accepted){status.setText("Модель отклонена нативным рендерером");return;}
        hasSurface=triangles!=null && nativeSetSurfaceTriangles(triangles);
        hasTexture=hasSurface&&uv!=null&&pixels!=null&&nativeSetTexture(uv,pixels,textureW,textureH);
        useTexture=hasTexture;
        nativeSetUseTexture(useTexture);
        hasNormalMap=hasTexture&&normalPixels!=null&&
            nativeSetNormalTexture(normalPixels,normalW,normalH);
        useNormalMap=hasNormalMap;
        nativeSetUseNormalMap(useNormalMap);
        lightMode=0;
        reliefPreset=1;
        nativeSetLightMode(lightMode);
        nativeSetNormalStrength(PreviewLighting.reliefStrength(reliefPreset));
        specularPreset=0;
        specularSizePreset=1;
        nativeSetSpecularStrength(PreviewMaterial.specularStrength(specularPreset));
        nativeSetSpecularSize(specularSizePreset);
        solidMode=false;
        nativeSetSolidMode(false); // Always open with the proven wireframe first.
        updateSurfaceButton();
        meshLabel=name;
        modelRadius=SceneFraming.radius(model.segments);
        fitFollowsLayout=true;
        refreshFraming();
        if(!name.startsWith("NIF:")) nifDimensions="";
        status.setText("Загружено: "+name+" · "+model.vertices+" вершин · "+model.edges+" рёбер");
        if(persist != null) getSharedPreferences("launcher_state",MODE_PRIVATE).edit().putString("obj_uri",persist.toString()).apply();
        else if(name.startsWith("Пример:")) getSharedPreferences("launcher_state",MODE_PRIVATE).edit().remove("obj_uri").apply();
        restartRenderer();
    }
    private void restartRenderer(){restartPending=true;stopIfActive();startIfReady();}
    private Button controlButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setPadding(0, 0, 0, 0);
        return button;
    }
    private void setCameraPreset(String name,float newYaw,float newPitch){
        viewPreset=name; yaw=newYaw; pitch=newPitch; autoSpin=false;
        fitFollowsLayout=true; refreshFraming(); updateControls();
    }
    /** Align the projected model with the actually visible region of the SurfaceView. */
    private void refreshFraming(){
        if(sceneRoot==null||sceneTitle==null||scenePanel==null || loadingError!=null) return;
        int w=sceneRoot.getWidth(),h=sceneRoot.getHeight();
        if(w<=0||h<=0)return;
        SceneFraming.Frame f=SceneFraming.compute(modelRadius,w,h,sceneTitle.getHeight(),scenePanel.getHeight());
        nativeSetViewportOffset(f.ndcYOffset);
        if(fitFollowsLayout){ zoom=f.distance; nativeSetView(yaw,pitch,zoom,autoSpin); }
        framingInfo=String.format(java.util.Locale.US,"Кадр: %.0f%% свободно · авто-вписывание: %s",
            100*f.visibleFraction,fitFollowsLayout?"да":"ручной масштаб");
    }
    private void updateControls() {
        autoButton.setText(autoSpin ? "Авто: Вкл" : "Авто: Выкл");
        fpsButton.setText(fpsCap == 0 ? "FPS: ∞" : "FPS: " + fpsCap);
        if (loadingError == null) {
            nativeSetView(yaw, pitch, zoom, autoSpin);
            nativeSetFpsCap(fpsCap);
        }
    }
    private boolean onSceneTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerX = event.getX(); pointerY = event.getY();
            pinchSpan = 0;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            pinchSpan = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() >= 2) {
                float span = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
                if (pinchSpan > 0) {zoom = clamp(zoom - (span - pinchSpan) / dp(90), 2.7f, 40.0f); fitFollowsLayout=false;}
                pinchSpan = span;
            } else {
                float x = event.getX(), y = event.getY();
                yaw += (x - pointerX) / dp(130);
                pitch = clamp(pitch + (y - pointerY) / dp(150), -1.3f, 1.3f);
                pointerX = x; pointerY = y;
            }
            viewPreset="Свободный";
            updateControls();
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            pinchSpan = 0;
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            if (remaining < event.getPointerCount()) {
                pointerX = event.getX(remaining); pointerY = event.getY(remaining);
            }
            return true;
        }
        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    @Override protected void onResume() {
        super.onResume();
        paused = false;
        restartPending = true;
        startIfReady();
    }
    @Override protected void onPause() {
        paused = true;
        stopIfActive();
        super.onPause();
    }
    @Override public void surfaceCreated(SurfaceHolder holder) {
        currentSurface = holder;
        surfaceReady = true;
        restartPending = true;
        startIfReady();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int fmt, int w, int h) {
        currentSurface = holder;
        boolean changed = surfaceW != 0 && (w != surfaceW || h != surfaceH);
        surfaceW = w;
        surfaceH = h;
        if (changed) {
            restartPending = true;
            stopIfActive(); // Recreate the swapchain for the new orientation.
        }
        startIfReady();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        restartPending = false;
        currentSurface = null;
        surfaceW = surfaceH = 0;
        stopIfActive();
    }
    private void stopIfActive() {
        if (loadingError == null) nativeStopScene();
    }
    private void startIfReady() {
        if (destroyed || paused || !surfaceReady || currentSurface == null || loadingError != null) return;
        Surface surface = currentSurface.getSurface();
        if (surface == null || !surface.isValid()) return;
        if (worker != null && worker.isAlive()) return; // Lifecycle callbacks already set restartPending.
        restartPending = false;
        nativeResetScene();
        nativeSetView(yaw, pitch, zoom, autoSpin);
        nativeSetFpsCap(fpsCap);
        nativeSetSolidMode(solidMode);
        nativeSetUseTexture(useTexture);
        nativeSetUseNormalMap(useNormalMap);
        nativeSetLightMode(lightMode);
        nativeSetNormalStrength(PreviewLighting.reliefStrength(reliefPreset));
        nativeSetSpecularStrength(PreviewMaterial.specularStrength(specularPreset));
        nativeSetSpecularSize(specularSizePreset);
        refreshFraming();
        final AssetManager assets = getAssets();
        worker = new Thread(() -> {
            try { nativeRunScene(surface, assets); }
            catch (Throwable t) {
                runOnUiThread(() -> status.setText("Ошибка JNI: " + t.toString()));
            }
            runOnUiThread(() -> {
                worker = null;
                // A paused/resumed Activity can retain the *same* Surface; restart anyway.
                if (!destroyed && !paused && surfaceReady && currentSurface != null &&
                    (restartPending || currentSurface.getSurface() != surface)) startIfReady();
            });
        }, "skyrim-vulkan-scene");
        worker.start();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        stopIfActive();
        ui.removeCallbacks(poll);
        super.onDestroy();
    }
}
