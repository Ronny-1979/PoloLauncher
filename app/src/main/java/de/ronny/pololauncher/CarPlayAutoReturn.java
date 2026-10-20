package de.ronny.pololauncher;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

/**
 * Preferences and permission helpers for the optional CarPlay -> Polo Launcher auto return.
 */
final class CarPlayAutoReturn {
    private static final String PREFS = "carplay_auto_return";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MANUAL_SUPPRESS_UNTIL = "manual_suppress_until";

    /** Let an intentional CarPlay launch from Polo Launcher stay in the foreground. */
    private static final long MANUAL_SUPPRESS_MS = 2 * 60_000L;

    /** First check after CarPlay came to the foreground; then every POLL_MS until connected. */
    static final long RETURN_DELAY_MS = 2_000L;
    static final long POLL_MS = 1_500L;

    private CarPlayAutoReturn() {}

    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    static void markManualLaunch(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_MANUAL_SUPPRESS_UNTIL,
                        System.currentTimeMillis() + MANUAL_SUPPRESS_MS)
                .apply();
    }

    static boolean manualLaunchSuppressed(Context context) {
        long until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_MANUAL_SUPPRESS_UNTIL, 0L);
        return System.currentTimeMillis() < until;
    }

    /**
     * PACKAGE_USAGE_STATS is a special-access AppOp, not a normal runtime permission.
     * The user grants it once on Android's "Usage access" settings screen.
     */
    static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
