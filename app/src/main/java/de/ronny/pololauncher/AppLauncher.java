package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppLauncher {
    private AppLauncher() {}

    public static boolean launchFirst(Activity a, String label, String... packages) {
        PackageManager pm = a.getPackageManager();
        for (String pkg : packages) {
            try {
                Intent i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        Toast.makeText(a, label + " nicht gefunden", Toast.LENGTH_SHORT).show();
        return false;
    }

    public static void launchMaps(Activity a) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
            i.setPackage("com.google.android.apps.maps");
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))); }
            catch (Throwable t) { Toast.makeText(a, "Keine Karten-App gefunden", Toast.LENGTH_SHORT).show(); }
        }
    }

    public static void launchDab(Activity a) {
        if (launchFirstSilent(a, DabNotificationListener.DAB_PACKAGE)) return;
        PackageManager pm = a.getPackageManager();
        try {
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                String value = (String.valueOf(pm.getApplicationLabel(info)) + " " + info.packageName).toLowerCase(Locale.ROOT);
                if (value.contains("dabdream") || value.contains("dab dream")) {
                    Intent i = pm.getLaunchIntentForPackage(info.packageName);
                    if (i != null) { a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                }
            }
        } catch (Throwable ignored) {}
        Toast.makeText(a, "DABdream+ nicht gefunden", Toast.LENGTH_SHORT).show();
    }

    public static void launchCarPlay(Activity a) {
        String[] known = {
                "com.zjinnova.zlink", "com.zjinnova.zlink5", "com.suding.speedplay",
                "cn.manstep.phonemirrorBox", "com.autokit", "com.carlinkit.autokit",
                "com.tima.carnet.mmain", "com.tima.carnet.m20"
        };
        if (launchFirstSilent(a, known)) return;

        PackageManager pm = a.getPackageManager();
        List<ApplicationInfo> apps;
        try { apps = pm.getInstalledApplications(0); }
        catch (Throwable t) { apps = new ArrayList<>(); }
        for (ApplicationInfo info : apps) {
            String name = String.valueOf(pm.getApplicationLabel(info)).toLowerCase(Locale.ROOT);
            String pkg = info.packageName.toLowerCase(Locale.ROOT);
            if (containsAny(name + " " + pkg, "zlink", "tlink", "autokit", "speedplay", "carplay", "carlink")) {
                Intent i = pm.getLaunchIntentForPackage(info.packageName);
                if (i != null) {
                    a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
            }
        }
        Toast.makeText(a, "CarPlay-App nicht erkannt – Paketname steht in Diagnose", Toast.LENGTH_LONG).show();
    }

    private static boolean launchFirstSilent(Activity a, String... packages) {
        PackageManager pm = a.getPackageManager();
        for (String pkg : packages) {
            try {
                Intent i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) { a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** Starts an activity; head-unit firmware often strips settings screens, so failure is normal. */
    public static boolean startSafely(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
            return true;
        } catch (RuntimeException notAvailable) {
            return false;
        }
    }

    public static void openAndroidSettings(Activity a) {
        if (!startSafely(a, new Intent(Settings.ACTION_SETTINGS)))
            Toast.makeText(a, "Android-Einstellungen sind auf diesem Radio nicht verfügbar", Toast.LENGTH_SHORT).show();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String n : needles) if (value.contains(n)) return true;
        return false;
    }
}
