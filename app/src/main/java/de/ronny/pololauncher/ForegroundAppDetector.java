package de.ronny.pololauncher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/** Reads the most recently foregrounded package when Usage Access is granted. */
final class ForegroundAppDetector {
    private ForegroundAppDetector() {}

    static String currentPackage(Context context) {
        if (!CarPlayAutoReturn.hasUsageAccess(context)) return null;
        try {
            UsageStatsManager manager =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) return null;

            long now = System.currentTimeMillis();
            UsageEvents events = manager.queryEvents(now - 30_000L, now);
            UsageEvents.Event event = new UsageEvents.Event();
            String latestPackage = null;
            long latestTime = Long.MIN_VALUE;

            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (event.getTimeStamp() >= latestTime) {
                        latestTime = event.getTimeStamp();
                        latestPackage = event.getPackageName();
                    }
                }
            }
            return latestPackage;
        } catch (Throwable error) {
            DiagLog.add("CarPlay-Automatik: Vordergrund-App nicht lesbar: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }
}
