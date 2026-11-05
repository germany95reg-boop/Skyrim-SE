package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/** Native GPU frame smoke-test. Absolutely no Skyrim executable is loaded. */
public final class VulkanTestActivity extends Activity implements SurfaceHolder.Callback {
    public static final String PREF_RESULT = "vulkan_frame_test_result";
    private static final int LAVENDER = Color.rgb(202, 176, 228);
    private static final int BG = Color.rgb(14, 15, 20);

    private static String libraryError;
    static {
        try { System.loadLibrary("skyrimse_renderer"); }
        catch (UnsatisfiedLinkError e) { libraryError = "Не удалось загрузить JNI: " + e.getMessage(); }
    }
    private static native String nativeDrawFrame(Surface surface);
    private static native void nativeReleaseFrame(long session);

    private final AtomicBoolean working = new AtomicBoolean(false);
    private boolean surfaceReady;
    // Owned by this Activity; cleared before a retry or surface teardown.
    private long retainedSession;
    private SurfaceHolder surfaceHolder;
    private TextView status;
    private Button retry;

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        surface.setContentDescription("Тестовый кадр Vulkan — сиреневый цвет от GPU");
        screen.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("SKYRIM SE MOBILE\nVulkan · тест кадра");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setBackgroundColor(0xD914151C);
        title.setPadding(dp(12), dp(12), dp(12), dp(12));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        screen.addView(title, titleParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(18), dp(10), dp(18), dp(14));
        panel.setBackgroundColor(0xEE171820);
        panel.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setText("Создание Vulkan Surface и swapchain…");
        status.setTextSize(14);
        status.setTextColor(Color.WHITE);
        status.setPadding(0, dp(3), 0, dp(9));
        panel.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        retry = new Button(this);
        retry.setText("Повторить тест");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> startTest());
        Button back = new Button(this);
        back.setText("В лаунчер");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams buttonWidth = new LinearLayout.LayoutParams(0, dp(48), 1);
        buttonWidth.setMargins(dp(3), 0, dp(3), 0);
        buttons.addView(retry, buttonWidth);
        LinearLayout.LayoutParams otherWidth = new LinearLayout.LayoutParams(0, dp(48), 1);
        otherWidth.setMargins(dp(3), 0, dp(3), 0);
        buttons.addView(back, otherWidth);
        panel.addView(buttons);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        screen.addView(panel, bottom);
        setContentView(screen);
        if (libraryError != null) {
            status.setText(libraryError);
            status.setTextColor(0xffffb3b3);
            JSONObject failure = new JSONObject();
            try { failure.put("success", false).put("message", libraryError); }
            catch (Exception ignored) { }
            getSharedPreferences("launcher_state", MODE_PRIVATE).edit()
                .putString(PREF_RESULT, failure.toString()).apply();
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        surfaceReady = true;
        startTest();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // A rotation normally creates a new Activity/Surface; that Surface runs a fresh test.
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        surfaceHolder = null;
        releaseRetained();
        // Any in-flight worker holds its own ANativeWindow reference and cleans up on completion.
    }

    private void releaseRetained() {
        final long oldSession = retainedSession;
        retainedSession = 0;
        if (oldSession != 0 && libraryError == null) {
            new Thread(() -> nativeReleaseFrame(oldSession), "release-vulkan-surface").start();
        }
    }

    @Override protected void onDestroy() {
        surfaceReady = false;
        releaseRetained();
        super.onDestroy();
    }

    private void startTest() {
        if (libraryError != null) {
            status.setText(libraryError);
            return;
        }
        if (!surfaceReady || surfaceHolder == null || !surfaceHolder.getSurface().isValid()) {
            status.setText("Ожидание готовой поверхности Android…");
            return;
        }
        if (!working.compareAndSet(false, true)) return;
        retry.setEnabled(false);
        status.setTextColor(LAVENDER);
        status.setText("Отправляю тестовый кадр в Vulkan GPU…");
        final Surface current = surfaceHolder.getSurface();
        final long previous = retainedSession;
        retainedSession = 0;
        new Thread(() -> {
            // vkCreateAndroidSurfaceKHR cannot attach while an old swapchain owns this Surface.
            if (previous != 0) nativeReleaseFrame(previous);
            String json;
            try { json = nativeDrawFrame(current); }
            catch (Throwable e) {
                JSONObject error = new JSONObject();
                try { error.put("success", false).put("message", e.getClass().getSimpleName()); }
                catch (Exception ignored) { }
                json = error.toString();
            }
            final String report = json;
            // Only a renderer result, not a game readiness flag.
            getSharedPreferences("launcher_state", MODE_PRIVATE).edit()
                .putString(PREF_RESULT, report).apply();
            runOnUiThread(() -> {
                working.set(false);
                try {
                    JSONObject out = new JSONObject(report);
                    long frameSession = out.optLong("session", 0);
                    if (!surfaceReady || isFinishing() || isDestroyed()) {
                        if (frameSession != 0) new Thread(() -> nativeReleaseFrame(frameSession),
                                "release-destroyed-vulkan-surface").start();
                        return;
                    }
                    retry.setEnabled(true);
                    retainedSession = frameSession;
                    if (out.optBoolean("success")) {
                        status.setTextColor(0xff9ee7b9);
                        status.setText("Кадр Vulkan отправлен на экран: " + out.optInt("width") + "×" +
                            out.optInt("height") + " · " + out.optString("gpu") +
                            "\nЭто не запуск Skyrim SE.");
                    } else {
                        status.setTextColor(0xffffb4b4);
                        status.setText("Тест Vulkan не прошёл: " + out.optString("message"));
                    }
                } catch (Exception e) {
                    status.setTextColor(0xffffb4b4);
                    status.setText("Ошибка чтения отчёта Vulkan: " + e.getClass().getSimpleName());
                }
            });
        }, "skyrim-vulkan-one-frame").start();
    }
}
