package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Locale;

final class TrafficDataManager {
    static final String PHASE_IDLE = "IDLE";
    static final String PHASE_DOWNLOAD = "DOWNLOAD";
    static final String PHASE_ANALYSE = "ANALYSE";
    static final String PHASE_BUILD = "AUFBAU";
    static final String PHASE_FINALIZE = "FINAL";

    private static final String PREF = "traffic_data_v1";
    private static final String ACTIVE_PHASE = "active_phase";
    private static final String ACTIVE_REGION = "active_region";
    private static final String ACTIVE_UPDATED = "active_updated";

    private TrafficDataManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String url(String region) {
        return "https://download.geofabrik.de/europe/germany/" + region + "-latest.osm.pbf";
    }

    static boolean active(Context context) {
        Status status = status(context, prefs(context).getString(ACTIVE_REGION, ""));
        return status.active;
    }

    static String delete(Context context, String region) {
        if (active(context)) return "Verkehrsdaten werden gerade verarbeitet";
        File db = TrafficDataStore.dbFile(context, region);
        if (db == null || !db.exists()) return "Keine Verkehrsdaten für diese Region installiert";
        long size = db.length();
        if (!db.delete()) return "Verkehrsdaten konnten nicht gelöscht werden";
        prefs(context).edit()
                .remove(readyModifiedKey(region))
                .remove(readySizeKey(region))
                .remove(readyBuiltKey(region))
                .remove(readyRoadsKey(region))
                .remove(readyCamerasKey(region))
                .apply();
        return "Verkehrsdaten gelöscht (" + humanBytes(size) + ")";
    }

    static void setActive(Context context, String region, String phase) {
        prefs(context).edit()
                .putString(ACTIVE_REGION, region == null ? "" : region)
                .putString(ACTIVE_PHASE, phase)
                .putLong(ACTIVE_UPDATED, System.currentTimeMillis())
                .apply();
    }

    static void clearActive(Context context, String finalPhase) {
        prefs(context).edit()
                .putString(ACTIVE_PHASE, finalPhase)
                .putLong(ACTIVE_UPDATED, System.currentTimeMillis())
                .apply();
    }

    static void markReady(Context context, String region, long sourceModified,
                          long sourceSize, long roads, long cameras) {
        prefs(context).edit()
                .putLong(readyModifiedKey(region), sourceModified)
                .putLong(readySizeKey(region), sourceSize)
                .putLong(readyBuiltKey(region), System.currentTimeMillis())
                .putLong(readyRoadsKey(region), roads)
                .putLong(readyCamerasKey(region), cameras)
                .apply();
    }

    static Status status(Context context, String region) {
        SharedPreferences p = prefs(context);
        File db = TrafficDataStore.dbFile(context, region);
        boolean ready = db != null && db.isFile() && db.length() >= 16_384L
                && p.getLong(readyBuiltKey(region), 0L) > 0L;

        String activeRegion = p.getString(ACTIVE_REGION, "");
        String phase = p.getString(ACTIVE_PHASE, PHASE_IDLE);
        long updated = p.getLong(ACTIVE_UPDATED, 0L);
        boolean phaseActive = PHASE_DOWNLOAD.equals(phase) || PHASE_ANALYSE.equals(phase)
                || PHASE_BUILD.equals(phase) || PHASE_FINALIZE.equals(phase);
        boolean stale = phaseActive && updated > 0L
                && System.currentTimeMillis() - updated > 90_000L;
        boolean sameRegion = region != null && region.equals(activeRegion);
        boolean active = phaseActive && !stale && sameRegion;

        return new Status(ready, active,
                p.getLong(readyModifiedKey(region), 0L),
                p.getLong(readySizeKey(region), 0L),
                p.getLong(readyRoadsKey(region), 0L),
                p.getLong(readyCamerasKey(region), 0L));
    }

    static String humanBytes(long bytes) {
        if (bytes <= 0L) return "--";
        if (bytes >= 1024L * 1024L * 1024L)
            return String.format(Locale.GERMANY, "%.1f GB", bytes / 1073741824.0);
        return String.format(Locale.GERMANY, "%.0f MB", bytes / 1048576.0);
    }

    private static String readyModifiedKey(String r) { return "ready_modified_" + r; }
    private static String readySizeKey(String r) { return "ready_size_" + r; }
    private static String readyBuiltKey(String r) { return "ready_built_" + r; }
    private static String readyRoadsKey(String r) { return "ready_roads_" + r; }
    private static String readyCamerasKey(String r) { return "ready_cameras_" + r; }

    static final class Status {
        final boolean ready, active;
        final long sourceModified, sourceSize, roads, cameras;

        Status(boolean ready, boolean active, long sourceModified, long sourceSize,
               long roads, long cameras) {
            this.ready = ready;
            this.active = active;
            this.sourceModified = sourceModified;
            this.sourceSize = sourceSize;
            this.roads = roads;
            this.cameras = cameras;
        }
    }
}
