package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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


    public static String findDabPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            if (pm.getLaunchIntentForPackage(DabNotificationListener.DAB_PACKAGE) != null)
                return DabNotificationListener.DAB_PACKAGE;
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                String value = (String.valueOf(pm.getApplicationLabel(info)) + " " + info.packageName).toLowerCase(Locale.ROOT);
                if ((value.contains("dabdream") || value.contains("dab dream"))
                        && pm.getLaunchIntentForPackage(info.packageName) != null) return info.packageName;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Resolve the actual launcher activity used by the installed CarPlay/phone-link app.
     *
     * This intentionally queries ACTION_MAIN/CATEGORY_LAUNCHER, just like our "Alle Apps" page.
     * Some head-unit apps expose an activity icon that differs from the application icon, so using
     * ResolveInfo.loadIcon() is the only reliable way to get the exact icon Android shows.
     */
    public static ResolveInfo findCarPlayResolveInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> candidates;
        try { candidates = pm.queryIntentActivities(query, PackageManager.MATCH_ALL); }
        catch (Throwable error) { candidates = new ArrayList<>(); }

        ResolveInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ResolveInfo info : candidates) {
            if (info == null || info.activityInfo == null || !info.activityInfo.enabled
                    || info.activityInfo.applicationInfo == null
                    || !info.activityInfo.applicationInfo.enabled) continue;
            String pkg = info.activityInfo.packageName == null ? "" : info.activityInfo.packageName;
            String activity = info.activityInfo.name == null ? "" : info.activityInfo.name;
            String label;
            try { label = String.valueOf(info.loadLabel(pm)); }
            catch (Throwable ignored) { label = ""; }

            String labelLower = label.toLowerCase(Locale.ROOT).trim();
            String haystack = (label + " " + pkg + " " + activity).toLowerCase(Locale.ROOT);
            int score = carPlayCandidateScore(pkg, labelLower, haystack);
            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int carPlayCandidateScore(String pkg, String label, String haystack) {
        String lowerPkg = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        int score = 0;
        String[] known = {
                "com.zjinnova.zlink", "com.zjinnova.zlink5", "com.suding.speedplay",
                "cn.manstep.phonemirrorbox", "com.autokit", "com.carlinkit.autokit",
                "com.tima.carnet.mmain", "com.tima.carnet.m20"
        };
        for (String item : known) if (lowerPkg.equals(item)) score = Math.max(score, 1200);

        if ("carplay".equals(label)) score = Math.max(score, 1150);
        if (label.contains("carplay")) score = Math.max(score, 1100);
        if (containsAny(label, "zlink", "tlink", "autokit", "speedplay", "carlink"))
            score = Math.max(score, 1000);
        if (containsAny(haystack, "carplay", "zlink", "tlink", "autokit", "speedplay",
                "carlink", "phonemirror", "phone mirror", "phone link", "phonelink"))
            score = Math.max(score, 850);
        return score;
    }

    public static String findCarPlayPackage(Context context) {
        ResolveInfo info = findCarPlayResolveInfo(context);
        return info != null && info.activityInfo != null ? info.activityInfo.packageName : null;
    }

    /** Loads the launcher's activity icon first, falling back to the application icon. */
    public static Drawable loadLauncherIcon(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        PackageManager pm = context.getPackageManager();

        // CarPlay/phone-link packages sometimes expose more than one LAUNCHER activity.
        // Use the exact ResolveInfo selected by findCarPlayResolveInfo(), matching "Alle Apps".
        try {
            ResolveInfo carPlay = findCarPlayResolveInfo(context);
            if (carPlay != null && carPlay.activityInfo != null
                    && packageName.equals(carPlay.activityInfo.packageName)) {
                Drawable exact = carPlay.loadIcon(pm);
                if (exact != null) return exact;
            }
        } catch (Throwable ignored) {}

        try {
            Intent query = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName);
            List<ResolveInfo> matches = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
            if (matches != null && !matches.isEmpty()) {
                Drawable icon = matches.get(0).loadIcon(pm);
                if (icon != null) return icon;
            }
        } catch (Throwable ignored) {}
        try { return pm.getApplicationIcon(packageName); }
        catch (Throwable ignored) { return null; }
    }

    public static void launchDab(Activity a) {
        String pkg = findDabPackage(a);
        if (pkg != null && launchFirstSilent(a, pkg)) return;
        Toast.makeText(a, "DABdream+ nicht gefunden", Toast.LENGTH_SHORT).show();
    }

    public static void launchCarPlay(Activity a) {
        // An intentional tap must never be pulled back by the automatic return feature.
        CarPlayAutoReturn.markManualLaunch(a);
        ResolveInfo info = findCarPlayResolveInfo(a);
        if (info != null && info.activityInfo != null) {
            try {
                Intent launch = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.startActivity(launch);
                return;
            } catch (Throwable ignored) {}
        }
        String pkg = findCarPlayPackage(a);
        if (pkg != null && launchFirstSilent(a, pkg)) return;
        Toast.makeText(a, "CarPlay-App nicht erkannt", Toast.LENGTH_LONG).show();
    }

    static boolean isCarPlayPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        String detected = findCarPlayPackage(context);
        return packageName.equals(detected);
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

    static boolean containsAny(String value, String... needles) {
        for (String n : needles) if (value.contains(n)) return true;
        return false;
    }
}
