package dev.skyrimsemobile.launcher;

import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/** Hardware capability probe, not an executable loader and not a Skyrim runtime. */
final class RuntimeDiagnostics {
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private volatile JSONObject result = defaultResult();

    private static native String nativeProbeVulkan();

    JSONObject snapshot() { return result; }

    private static JSONObject defaultResult() {
        JSONObject o = new JSONObject();
        try {
            o.put("state", "not_checked");
            o.put("arm64", false);
            o.put("nativeLibraryLoaded", false);
            o.put("vulkanLoader", false);
            o.put("vulkan11", false);
            o.put("gpuCount", 0);
            o.put("gpuName", "");
            o.put("apiVersion", "");
            o.put("message", "Диагностика ещё не запускалась");
        } catch (JSONException ignored) { }
        return o;
    }

    void run(Runnable onFinish) {
        if (!inFlight.compareAndSet(false, true)) return;
        try { result = new JSONObject(result.toString()).put("state", "checking"); }
        catch (JSONException ignored) { }
        new Thread(() -> {
            JSONObject o = defaultResult();
            try {
                boolean arm64 = false;
                for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) arm64 = true;
                o.put("arm64", arm64);
                o.put("androidVersion", Build.VERSION.RELEASE);
                o.put("primaryAbi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown");
                o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                if (!arm64) {
                    o.put("state", "done");
                    o.put("message", "Устройство не поддерживает ABI arm64-v8a");
                } else {
                    try {
                        System.loadLibrary("skyrimse_probe");
                        o.put("nativeLibraryLoaded", true);
                        JSONObject vk = new JSONObject(nativeProbeVulkan());
                        java.util.Iterator<String> keys = vk.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            o.put(key, vk.get(key));
                        }
                        o.put("state", "done");
                    } catch (UnsatisfiedLinkError | SecurityException error) {
                        o.put("state", "done");
                        o.put("message", "Не удалось загрузить ARM64-библиотеку: " + error.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                try { o.put("state", "error").put("message", "Ошибка проверки: " + e.getClass().getSimpleName()); }
                catch (JSONException ignored) { }
            } finally {
                result = o;
                inFlight.set(false);
                if (onFinish != null) onFinish.run();
            }
        }, "arm64-vulkan-diagnostic").start();
    }
}
