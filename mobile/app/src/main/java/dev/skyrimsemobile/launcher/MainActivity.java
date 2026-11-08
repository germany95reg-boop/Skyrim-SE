package dev.skyrimsemobile.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * First launcher MVP. The game engine is intentionally NOT bundled or simulated.
 * The WebView only loads the app's immutable local asset and never visits remote URLs.
 */
public final class MainActivity extends Activity {
    private static final int PICK_GAME = 410;
    private static final int PICK_ZIP = 411;
    private static final int MAX_FILES = 5000;
    private static final long MAX_UNPACKED_BYTES = 512L * 1024 * 1024;
    private static final String ASSET_URL = "file:///android_asset/index.html";
    private static final List<String> KEYS = Arrays.asList("resolution", "quality", "msaa", "anisotropic", "touch", "fps");
    private final Object stateLock = new Object();
    private SharedPreferences prefs;
    private final RuntimeDiagnostics runtimeDiagnostics = new RuntimeDiagnostics();
    private WebView web;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("launcher_state", MODE_PRIVATE);
        getWindow().setStatusBarColor(0xff101115);
        getWindow().setNavigationBarColor(0xff101115);
        web = new WebView(this);
        web.setBackgroundColor(0xff0b0c11);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true); // Only for the bundled file:///android_asset/ page.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !ASSET_URL.equals(url); // No remote or arbitrary file navigation.
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(web);
        web.loadUrl(ASSET_URL);
        runtimeDiagnostics.run(this::refresh);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) refresh(); // Pick up the most recent Vulkan surface test report.
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("Закрыть лаунчер?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Закрыть", (d, w) -> finish()).show();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidBridge");
            web.destroy();
        }
        super.onDestroy();
    }

    private final class Bridge {
        @JavascriptInterface public String getState() { return stateJson().toString(); }
        @JavascriptInterface public void chooseFolder() { runOnUiThread(MainActivity.this::pickFolder); }
        @JavascriptInterface public void importMod() { runOnUiThread(MainActivity.this::pickZip); }
        @JavascriptInterface public void checkFolder() { runOnUiThread(() -> { refresh(); toast("Проверка папки завершена", false); }); }
        @JavascriptInterface public void runDiagnostics() { runOnUiThread(() -> {
            runtimeDiagnostics.run(() -> { refresh(); toast("Проверка ARM64 и Vulkan завершена", false); });
            refresh();
        }); }
        @JavascriptInterface public void copyDiagnostics() { runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Skyrim SE Device Diagnostics", stateJson().toString()));
                toast("Отчёт скопирован в буфер обмена", false);
            }
        }); }
        @JavascriptInterface public void openVulkanTest() { runOnUiThread(() -> {
            startActivity(new Intent(MainActivity.this, VulkanTestActivity.class));
        }); }
        @JavascriptInterface public void openResourceInspector() { runOnUiThread(() -> startActivity(new Intent(MainActivity.this, ResourceInspectorActivity.class))); }
        @JavascriptInterface public void openPluginInspector() { runOnUiThread(() ->
            startActivity(new Intent(MainActivity.this, ResourceInspectorActivity.class).putExtra("open_tes4", true))); }
        @JavascriptInterface public void openPluginGroupIndex() { runOnUiThread(() ->
            startActivity(new Intent(MainActivity.this, ResourceInspectorActivity.class).putExtra("open_grup", true))); }
        @JavascriptInterface public void openDeviceSelfTest() { runOnUiThread(() ->
            startActivity(new Intent(MainActivity.this, DeviceSelfTestActivity.class))); }
        @JavascriptInterface public void openVulkan3DTest() { runOnUiThread(() -> {
            startActivity(new Intent(MainActivity.this, VulkanSceneActivity.class));
        }); }
        @JavascriptInterface public void launch() { runOnUiThread(() ->
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Нативный движок не подключён")
                .setMessage("Это рабочий лаунчер-подготовка. Для реального запуска Skyrim Special Edition нужно разработать и подключить отдельный ARM64-движок.")
                .setPositiveButton("Понятно", null).show()); }
        @JavascriptInterface public void setModEnabled(String id, boolean value) {
            if (isValidId(id) && new File(modRoot(), id).isDirectory())
                prefs.edit().putBoolean("mod_" + id, value).apply();
            refresh();
        }
        @JavascriptInterface public void saveSetting(String key, String value) {
            if (!KEYS.contains(key) || value == null || value.length() > 64) return;
            prefs.edit().putString("setting_" + key, value).apply();
        }
        @JavascriptInterface public void saveModOrder(String order) {
            if (order != null && order.length() < 12000 && order.matches("[a-zA-Z0-9,_-]*"))
                prefs.edit().putString("mod_order", order).apply();
        }
        @JavascriptInterface public void deleteMod(String id) {
            if (!isValidId(id)) return;
            new Thread(() -> {
                synchronized (stateLock) { deleteRecursively(new File(modRoot(), id)); }
                prefs.edit().remove("mod_" + id).apply();
                refresh();
                toast("Мод удалён из хранилища лаунчера", false);
            }, "delete-mod").start();
        }
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_GAME);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_ZIP);
    }

    @Override protected void onActivityResult(int request, int result, Intent i) {
        super.onActivityResult(request, result, i);
        if (result != RESULT_OK || i == null || i.getData() == null) return;
        Uri uri = i.getData();
        if (request == PICK_GAME) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("folder_uri", uri.toString()).apply();
                refresh();
                toast(probeFolder().status, false);
            } catch (Exception e) { toast("Нет доступа к выбранной папке: " + e.getMessage(), true); }
        } else if (request == PICK_ZIP) {
            final String displayName = getDisplayName(uri);
            if (!displayName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                toast("Пока поддерживаются только ZIP-архивы", true);
                return;
            }
            toast("Импорт ZIP-мода начался", false);
            new Thread(() -> {
                try {
                    int count;
                    synchronized (stateLock) { count = unpackMod(uri, displayName); }
                    refresh();
                    toast("Мод импортирован (" + count + " файлов). Пока не применяется к игре.", false);
                } catch (Exception e) {
                    toast("Не удалось импортировать: " + e.getMessage(), true);
                }
            }, "unpack-zip").start();
        }
    }

    private static final class FolderProbe {
        String name = "";
        String status = "Папка игры не выбрана";
        boolean ready = false;
        String exeValidation = "EXE ещё не проверен";
    }

    private FolderProbe probeFolder() {
        FolderProbe out = new FolderProbe();
        String uriString = prefs.getString("folder_uri", "");
        if (uriString.isEmpty()) return out;
        try {
            Uri tree = Uri.parse(uriString);
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            out.name = queryName(DocumentsContract.buildDocumentUriUsingTree(tree, rootId));
            if (out.name == null || out.name.isEmpty()) out.name = "Папка выбрана";
            HashMap<String,String> rootChildren = childMap(tree, rootId);
            String exe = rootChildren.get("skyrimse.exe");
            String data = rootChildren.get("data");
            List<String> missing = new ArrayList<>();
            if (exe == null) missing.add("SkyrimSE.exe");
            else {
                Uri exeUri = DocumentsContract.buildDocumentUriUsingTree(tree, exe);
                try (InputStream in = getContentResolver().openInputStream(exeUri)) {
                    PeHeaderInspector.Result pe = PeHeaderInspector.inspect(in);
                    out.exeValidation = pe.description;
                    if (!pe.isPe64) missing.add("корректный 64-битный SkyrimSE.exe");
                } catch (IOException err) {
                    out.exeValidation = "Не удалось прочитать заголовок EXE: " + err.getClass().getSimpleName();
                    missing.add("доступный для чтения SkyrimSE.exe");
                }
            }
            if (data == null) missing.add("Data/");
            else if (!childMap(tree, data).containsKey("skyrim.esm")) missing.add("Data/Skyrim.esm");
            out.ready = missing.isEmpty();
            out.status = out.ready ? "Структура папки и PE32+ x64 подтверждены. Нативный движок Skyrim SE пока отсутствует." :
                "Проверка файлов: " + String.join(", ", missing);
        } catch (Exception e) { out.status = "Не удалось проверить выбранную папку: " + e.getClass().getSimpleName(); }
        return out;
    }

    private HashMap<String,String> childMap(Uri tree, String folderId) {
        HashMap<String,String> files = new HashMap<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId);
        try (Cursor c = getContentResolver().query(children, new String[]{
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID
        }, null, null, null)) {
            if (c == null) return files;
            while (c.moveToNext()) files.put(c.getString(0).toLowerCase(Locale.ROOT), c.getString(1));
        }
        return files;
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : "";
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "mod.zip";
    }

    private File modRoot() {
        File dir = new File(getFilesDir(), "staged_mods");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private int unpackMod(Uri uri, String originalName) throws IOException {
        String id = UUID.randomUUID().toString();
        File base = new File(modRoot(), id);
        File content = new File(base, "content");
        if (!content.mkdirs()) throw new IOException("Не удалось создать папку мода");
        long total = 0;
        int entries = 0;
        int files = 0;
        int pluginFiles = 0;
        int skseDllFiles = 0;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Архив недоступен");
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                byte[] buffer = new byte[32768];
                String prefix = content.getCanonicalPath() + File.separator;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_FILES) throw new IOException("Слишком много файлов в ZIP");
                    String path = entry.getName().replace('\\', '/');
                    if (path.startsWith("/") || path.indexOf('\0') >= 0 || path.matches("^[A-Za-z]:.*"))
                        throw new IOException("Некорректный путь в архиве");
                    File dest = new File(content, path);
                    String canonical = dest.getCanonicalPath();
                    if (!canonical.startsWith(prefix) || path.isEmpty()) throw new IOException("Небезопасный путь в ZIP");
                    if (entry.isDirectory()) {
                        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("Ошибка создания папки");
                    } else {
                        File parent = dest.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Ошибка создания папки");
                        try (OutputStream output = new FileOutputStream(dest)) {
                            int n;
                            while ((n = zip.read(buffer)) != -1) {
                                total += n;
                                if (total > MAX_UNPACKED_BYTES) throw new IOException("Архив превышает лимит 512 МБ после распаковки");
                                output.write(buffer, 0, n);
                            }
                        }
                        files++;
                        String low = path.toLowerCase(Locale.ROOT);
                        if (low.endsWith(".esp") || low.endsWith(".esm") || low.endsWith(".esl")) pluginFiles++;
                        if (low.endsWith(".dll") && (low.startsWith("skse/plugins/") || low.contains("/skse/plugins/"))) skseDllFiles++;
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            deleteRecursively(base);
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException(e.getMessage(), e);
        }
        if (files == 0) { deleteRecursively(base); throw new IOException("Архив не содержит файлов"); }
        Properties metadata = new Properties();
        metadata.setProperty("name", originalName.replaceAll("(?i)\\.zip$", ""));
        metadata.setProperty("files", String.valueOf(files));
        metadata.setProperty("pluginFiles", String.valueOf(pluginFiles));
        metadata.setProperty("skseDllFiles", String.valueOf(skseDllFiles));
        try (OutputStream out = new FileOutputStream(new File(base, "info.properties"))) {
            metadata.store(out, "Skyrim SE Launcher staging metadata");
        } catch (IOException e) { deleteRecursively(base); throw e; }
        prefs.edit().putBoolean("mod_" + id, false).apply();
        return files;
    }

    private boolean isValidId(String id) {
        return id != null && id.matches("[A-Za-z0-9-]{1,80}");
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File f : children) deleteRecursively(f);
        }
        file.delete();
    }

    private JSONObject stateJson() {
        JSONObject json = new JSONObject();
        try {
            FolderProbe probe = probeFolder();
            json.put("installedVersion", InstalledAppVersion.label(this));
            json.put("gameFolder", probe.name);
            json.put("folderStatus", probe.status);
            json.put("exeValidation", probe.exeValidation);
            json.put("folderReady", probe.ready);
            json.put("engineConnected", false); // A successful hardware probe is NOT a game runtime.
            json.put("runtime", runtimeDiagnostics.snapshot());
            String frameReport = prefs.getString(VulkanTestActivity.PREF_RESULT, "");
            if (!frameReport.isEmpty()) {
                try { json.put("frameTest", new JSONObject(frameReport)); }
                catch (JSONException e) { json.put("frameTest", new JSONObject()); }
            } else json.put("frameTest", JSONObject.NULL);
            String sceneReport = prefs.getString(VulkanSceneActivity.PREF_RESULT, "");
            if (!sceneReport.isEmpty()) {
                try { json.put("sceneTest", new JSONObject(sceneReport)); }
                catch (JSONException e) { json.put("sceneTest", JSONObject.NULL); }
            } else json.put("sceneTest", JSONObject.NULL);
            JSONObject settings = new JSONObject();
            settings.put("resolution", prefs.getString("setting_resolution", "1280 × 720"));
            settings.put("quality", prefs.getString("setting_quality", "Среднее"));
            settings.put("msaa", Boolean.parseBoolean(prefs.getString("setting_msaa", "false")));
            settings.put("anisotropic", Boolean.parseBoolean(prefs.getString("setting_anisotropic", "true")));
            settings.put("touch", Boolean.parseBoolean(prefs.getString("setting_touch", "true")));
            settings.put("fps", prefs.getString("setting_fps", "30"));
            json.put("settings", settings);
            json.put("resourceReport", prefs.getString(ResourceInspectorActivity.PREF_REPORT, ""));
            json.put("mods", modJson());
        } catch (JSONException ignored) { }
        return json;
    }

    private static int parseCount(String s) {
        try { return Math.max(0, Integer.parseInt(s)); } catch (Exception ignored) { return 0; }
    }

    private JSONArray modJson() throws JSONException {
        List<JSONObject> mods = new ArrayList<>();
        synchronized (stateLock) {
            File[] folders = modRoot().listFiles();
            if (folders != null) for (File folder : folders) {
                if (!folder.isDirectory() || !isValidId(folder.getName())) continue;
                File info = new File(folder, "info.properties");
                if (!info.isFile()) continue;
                Properties metadata = new Properties();
                try (InputStream in = new FileInputStream(info)) { metadata.load(in); }
                catch (IOException ignored) { continue; }
                JSONObject item = new JSONObject();
                String id = folder.getName();
                item.put("id", id);
                item.put("name", metadata.getProperty("name", id));
                item.put("files", parseCount(metadata.getProperty("files")));
                item.put("pluginCount", parseCount(metadata.getProperty("pluginFiles")));
                item.put("skseDllCount", parseCount(metadata.getProperty("skseDllFiles")));
                item.put("enabled", prefs.getBoolean("mod_" + id, false));
                mods.add(item);
            }
        }
        final List<String> order = Arrays.asList(prefs.getString("mod_order", "").split(","));
        Collections.sort(mods, (a,b) -> {
            int ai = order.indexOf(a.optString("id")), bi = order.indexOf(b.optString("id"));
            if (ai < 0) ai = 99999;
            if (bi < 0) bi = 99999;
            if (ai != bi) return Integer.compare(ai, bi);
            return a.optString("name").compareToIgnoreCase(b.optString("name"));
        });
        JSONArray array = new JSONArray();
        for (JSONObject mod : mods) array.put(mod);
        return array;
    }

    private void refresh() {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.nativeRefresh && window.nativeRefresh()", null); });
    }

    private void toast(String message, boolean error) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.nativeNotify && window.nativeNotify("+
                JSONObject.quote(message == null ? "Ошибка" : message) + "," + error + ")", null);
        });
    }
}
