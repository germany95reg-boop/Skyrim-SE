package dev.skyrimsemobile.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Reports the VERSION OF THE APK INSTALLED on this device, not a screen feature revision. */
public final class InstalledAppVersion {
    private InstalledAppVersion() {}
    public static String label(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            String name = info.versionName == null ? "неизвестна" : info.versionName;
            return "v" + name + " · APK " + code;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "версия APK недоступна";
        }
    }
}
