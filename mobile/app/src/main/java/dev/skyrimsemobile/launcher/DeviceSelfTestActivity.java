package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Locale;

/**
 * Native one-tap self-test on the user's physical Android device.
 * Reuses the last SAF permissions, runs without emulator/network, sends nothing
 * without an explicit user tap on Copy/Share.
 */
public final class DeviceSelfTestActivity extends Activity {
    private static final int VULKAN_TEST_REQUEST=215;
    private TextView progressText,reportText;
    private ProgressBar spinner;
    private Button startButton,copyButton,shareButton;
    private DeviceSelfTestRunner runner;
    private DeviceSelfTestReport report;
    private boolean running,destroyed;
    private long started;
    private String completedReport="";

    private int dp(int px){
        return Math.round(getResources().getDisplayMetrics().density*px);
    }
    private TextView text(String content,int size,int color){
        TextView t=new TextView(this);
        t.setText(content);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(dp(2),dp(8),dp(2),dp(8));
        return t;
    }
    private Button button(String label){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);
        return b;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff101115);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(14),dp(18),dp(30));
        scroll.addView(body);

        TextView heading=text("Автотест Skyrim SE Mobile",23,Color.WHITE);
        heading.setTypeface(null,1);
        body.addView(heading);
        body.addView(text(
            "Один запуск: проверка ARM64/Vulkan → сохранённый BSA → NIF → DDS → "+
            "составная модель → настоящий Vulkan с автоматическим переключением\n"+
            "30 FPS → 60 FPS → Без ограничения.\n"+
            "Никакого эмулятора и загрузки данных в интернет. "+
            "Архивы Skyrim остаются неизменными.",14,0xffc9c5d2));

        startButton=button("Запустить полный автотест");
        startButton.setOnClickListener(v->start());
        body.addView(startButton,new LinearLayout.LayoutParams(-1,dp(54)));

        Button openFiles=button("Открыть просмотрщик BSA / выбрать Data");
        openFiles.setOnClickListener(v->startActivity(new Intent(this,BsaCatalogActivity.class)));
        body.addView(openFiles,new LinearLayout.LayoutParams(-1,dp(48)));

        spinner=new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(dp(32),dp(32));
        bar.gravity=Gravity.CENTER_HORIZONTAL;
        body.addView(spinner,bar);

        progressText=text("Готово к проверке",15,0xffd5b9ff);
        body.addView(progressText);
        reportText=text("После проверки появится полный отчёт для копирования.",13,0xffeeeeee);
        reportText.setTextIsSelectable(true);
        body.addView(reportText);

        copyButton=button("Копировать отчёт");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v->copy());
        body.addView(copyButton,new LinearLayout.LayoutParams(-1,dp(48)));

        shareButton=button("Поделиться отчётом");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v->share());
        body.addView(shareButton,new LinearLayout.LayoutParams(-1,dp(48)));

        setContentView(scroll);
        if(saved==null)start(); // the first tap that opened this screen is enough.
    }

    private void start(){
        if(running||destroyed)return;
        running=true;
        started=SystemClock.elapsedRealtime();
        completedReport="";
        report=new DeviceSelfTestReport();
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        startButton.setEnabled(false);
        spinner.setVisibility(ProgressBar.VISIBLE);
        reportText.setText("Сбор данных на устройстве. Время зависит от размера папки Data.");
        updateProgress("Начинаю автоматические проверки…");
        // Clears any left-over one-shot NIF from a previously opened preview.
        NifGeometryTransfer.take();
        runner=new DeviceSelfTestRunner(this,report,this::updateProgress);
        DeviceSelfTestRunner active=runner;
        new Thread(()->{
            try{
                active.run();
            }catch(Throwable error){
                if(!destroyed)report.fail("Автотест",
                    error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()));
            }
            runOnUiThread(()->{
                if(!destroyed&&runner==active)startGpuPhase();
            });
        },"skyrim-on-device-autotest").start();
    }
    private void updateProgress(String message){
        runOnUiThread(()->{
            if(!destroyed&&running)progressText.setText(message);
        });
    }

    private void startGpuPhase(){
        updateProgress("Последний этап · Запускаю реальную Vulkan-сцену на устройстве…");
        try{
            Intent check=new Intent(this,VulkanSceneActivity.class);
            check.putExtra(VulkanSceneActivity.EXTRA_DEVICE_AUTOTEST,true);
            startActivityForResult(check,VULKAN_TEST_REQUEST);
        }catch(Exception ex){
            report.fail("Vulkan GPU-сцена","Не получилось открыть SurfaceView: "+ex.getClass().getSimpleName());
            finishReport();
        }
    }
    /** Actual FPS and frame counts are measured for every native cap separately. */
    private void recordFpsModes(JSONObject stats,boolean gpuCompleted){
        JSONArray stages=stats.optJSONArray("fpsStages");
        int[] expected={30,60,0};
        int passed=0;
        for(int target:expected){
            JSONObject stage=null;
            if(stages!=null)for(int i=0;i<stages.length();i++){
                JSONObject candidate=stages.optJSONObject(i);
                if(candidate!=null&&candidate.optInt("cap",-1)==target){
                    stage=candidate;
                    break;
                }
            }
            String name="Vulkan FPS "+(target==0?"Без ограничения":target);
            if(stage==null){
                if(gpuCompleted)
                    report.fail(name,"Автотест не вернул измерение для этого режима");
                else
                    report.warn(name,"Измерение не завершено из-за остановки Vulkan");
                continue;
            }
            long frames=stage.optLong("frames",0),ms=stage.optLong("sampleMillis",0);
            double fps=stage.optDouble("fps",0),nativeFps=stage.optDouble("nativeFps",0);
            boolean confirmed=stage.optBoolean("capConfirmed",false);
            String details=String.format(Locale.ROOT,
                "Заданный лимит: %s · фактически %.1f FPS · %d кадров за %.2f с"+
                " · окно Vulkan %.1f FPS · лимит подтверждён: %s",
                target==0?"отключён":Integer.toString(target),
                fps,frames,ms/1000.0,nativeFps,confirmed?"да":"НЕТ");
            if(stage.optBoolean("passed",false)&&confirmed&&frames>0&&
               ms>=1800&&Double.isFinite(fps)&&fps>0){
                report.pass(name,details);
                passed++;
            }else report.fail(name,details);
        }
        if(passed==expected.length)
            report.pass("Переключение FPS",
                "Все 3 режима переключены автоматически, без перезапуска Vulkan и ручных нажатий");
        else
            report.warn("Переключение FPS",passed+" из "+expected.length+
                " этапов подтверждены; смотри строки Vulkan FPS выше");
    }
    @Override protected void onActivityResult(int request,int resultCode,Intent data){
        super.onActivityResult(request,resultCode,data);
        if(request!=VULKAN_TEST_REQUEST||!running||destroyed)return;
        JSONObject stats=new JSONObject();
        if(data!=null)try{
            String json=data.getStringExtra(VulkanSceneActivity.EXTRA_DEVICE_AUTOTEST_RESULT);
            if(json!=null)stats=new JSONObject(json);
        }catch(Exception ex){
            report.fail("Vulkan GPU-сцена","Невозможно прочитать статистику: "+
                ex.getClass().getSimpleName());
        }
        boolean completed=resultCode==RESULT_OK;
        int frames=stats.optInt("frames",0);
        if(completed&&frames>0&&!stats.optBoolean("error",false)){
            int width=stats.optInt("width",0),height=stats.optInt("height",0);
            report.pass("Настоящий Vulkan GPU",frames+" кадров всего · "+
                width+"×"+height+" · "+stats.optString("gpu","GPU"));
            int triangles=stats.optInt("triangles",0);
            if(triangles>0)
                report.pass("3D-модель Vulkan",triangles+" треугольников · DDS: "+
                    stats.optString("textureMode","неизвестно"));
            else report.warn("3D-модель Vulkan",
                "Отображался встроенный каркас, выбранная NIF недоступна");
            if(stats.optBoolean("normalAtlasPresent",false)){
                if(stats.optBoolean("normalMapApplied",false))
                    report.pass("Normal DDS GPU",
                        "Карты нормалей включены в Vulkan · shader: "+
                        stats.optString("textureMode","неизвестно"));
                else report.warn("Normal DDS GPU",
                    "Карты нормалей загружены, но GPU выбрал совместимый "+
                    stats.optString("textureMode","off")+
                    " без normal shading; diffuse DDS сохранены");
            }else report.skip("Normal DDS GPU",
                "Не передан составной normal DDS-атлас; GPU тестирует diffuse или геометрию");
            // Real pixel evidence is checked separately from submitted GPU
            // frames. Do not falsely PASS when Vulkan only presents a clear
            // surface. A failed PixelCopy produces WARN, never loses the model.
            String frameStatus=stats.optString("frameCaptureStatus","not-requested");
            String frameDetails=stats.optString("frameCaptureNote",
                "Нет данных PixelCopy с Android SurfaceView");
            if("visible".equals(frameStatus)){
                int foreground=stats.optInt("frameNonBackground",0);
                int total=stats.optInt("frameSamplePixels",0);
                if(total>0&&foreground>0)
                    report.pass("Изображение Vulkan PixelCopy",
                        frameDetails+" · реальный SurfaceView (не UI)");
                else report.warn("Изображение Vulkan PixelCopy",
                    "Некорректные данные пикселей: "+frameDetails);
            }else if("small".equals(frameStatus)){
                // A small footprint may be camera framing, a thin object,
                // or a face-on texture. Keep Vulkan/FPS PASS unchanged.
                report.warn("Изображение Vulkan PixelCopy",
                    "Модель есть, но видимая область компактна по обеим осям. "+
                    "Проверь авто-вписывание и масштаб камеры: "+frameDetails+
                    " · дистанция "+String.format(Locale.ROOT,"%.1f",
                        stats.optDouble("distance",0))+" (Vulkan)");
            }else if("blank".equals(frameStatus)){
                report.warn("Изображение Vulkan PixelCopy",
                    "Рендер выводит кадры, но на SurfaceView не найдено убедительных "+
                    "пикселей модели: "+frameDetails);
            }else if("capturing".equals(frameStatus)){
                report.warn("Изображение Vulkan PixelCopy",
                    "PixelCopy не завершился до конца теста; FPS и Vulkan проверены отдельно");
            }else if("not-requested".equals(frameStatus)&&triangles==0){
                report.skip("Изображение Vulkan PixelCopy",
                    "Статическая NIF не подготовлена для проверки изображения");
            }else{
                report.warn("Изображение Vulkan PixelCopy",
                    "Не удалось проверить изображение: "+frameDetails);
            }
        }else {
            String reason=data==null?"Проверка была закрыта или прервана":
                data.getStringExtra(VulkanSceneActivity.EXTRA_DEVICE_AUTOTEST_ERROR);
            report.fail("Настоящий Vulkan GPU",reason==null?
                stats.optString("message","Не удалось завершить Vulkan"):reason);
        }
        recordFpsModes(stats,completed);
        finishReport();
    }
    private void finishReport(){
        if(destroyed||!running)return;
        running=false;
        spinner.setVisibility(ProgressBar.GONE);
        startButton.setEnabled(true);
        copyButton.setEnabled(true);
        shareButton.setEnabled(true);
        completedReport=report.finish(SystemClock.elapsedRealtime()-started);
        progressText.setText("Проверка завершена · PASS "+report.passed()+
            " · WARN "+report.warnings()+" · FAIL "+report.failed()+
            " · SKIP "+report.skipped());
        reportText.setText(completedReport);
    }
    private void copy(){
        if(completedReport.isEmpty())return;
        ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager!=null){
            manager.setPrimaryClip(ClipData.newPlainText("Skyrim SE Mobile — автотест",completedReport));
            Toast.makeText(this,"Отчёт скопирован. Вставь его в чат.",Toast.LENGTH_LONG).show();
        }
    }
    private void share(){
        if(completedReport.isEmpty())return;
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT,"Skyrim SE Mobile — автотест на устройстве");
        share.putExtra(Intent.EXTRA_TEXT,completedReport);
        startActivity(Intent.createChooser(share,"Отправить отчёт"));
    }
    @Override protected void onDestroy(){
        destroyed=true;
        if(runner!=null)runner.cancel();
        super.onDestroy();
    }
}
