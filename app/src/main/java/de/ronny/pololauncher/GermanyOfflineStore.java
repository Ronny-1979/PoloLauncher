package de.ronny.pololauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

/**
 * Single user-facing Germany offline package.
 *
 * The package is intentionally presented as ONE offline map. Internally it keeps the
 * Geofabrik Shortbread state archives separate so MapLibre can read them directly without
 * rewriting multi-gigabyte vector tiles on the radio. The traffic databases are likewise
 * per extract, but hidden from the UI. This avoids border-tile corruption and keeps updates
 * resumable while still behaving as one seamless Germany map to the user.
 */
final class GermanyOfflineStore {
    static final String ACTION_INSTALL = "de.ronny.pololauncher.GERMANY_OFFLINE_INSTALL";
    static final String ACTION_CANCEL = "de.ronny.pololauncher.GERMANY_OFFLINE_CANCEL";
    static final String EXTRA_UPDATE_EXISTING = "update_existing";
    static final String EXTRA_IMPORT_SOURCE = "import_source";

    // Brandenburg already contains Berlin; Niedersachsen already contains Bremen on Geofabrik.
    // Keeping only the 14 non-overlapping extracts avoids duplicate tiles and duplicate traffic data.
    static final String[] REGION_KEYS = {
            "baden-wuerttemberg", "bayern", "brandenburg", "hamburg",
            "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen",
            "schleswig-holstein", "thueringen"
    };
    static final String[] REGION_NAMES = {
            "Baden-Württemberg", "Bayern", "Brandenburg (mit Berlin)", "Hamburg",
            "Hessen", "Mecklenburg-Vorpommern", "Niedersachsen (mit Bremen)", "Nordrhein-Westfalen",
            "Rheinland-Pfalz", "Saarland", "Sachsen-Anhalt", "Sachsen",
            "Schleswig-Holstein", "Thüringen"
    };

    // Geofabrik experimental Shortbread packages are updated in-place at these stable URLs.
    private static final String GEOFABRIK = "https://download.geofabrik.de/europe/germany/";
    private static final String GLYPH_PRIMARY_BASE = "https://demotiles.maplibre.org/font/"
            + "Open%20Sans%20Regular,Arial%20Unicode%20MS%20Regular/";
    // Independent fallback for radios/proxies that return a tiny error body from demotiles.maplibre.org.
    // These are the same MapLibre demo glyph PBFs, served directly from the public demotiles repository.
    private static final String GLYPH_GITHUB_BASE = "https://raw.githubusercontent.com/maplibre/demotiles/"
            + "gh-pages/font/Open%20Sans%20Regular%2CArial%20Unicode%20MS%20Regular/";
    private static final int[] GLYPH_STARTS = {0, 256, 512, 768, 1024, 1280, 1536, 1792};

    private static final String PREF = "germany_offline_v1";
    private static final String READY = "ready";
    private static final String BUILT_AT = "built_at";
    private static final String PHASE = "phase";
    private static final String DETAIL = "detail";
    private static final String STEP = "step";
    private static final String STEPS = "steps";
    private static final String DONE = "done";
    private static final String TOTAL = "total";
    private static final String UPDATED = "updated";
    private static final String RENDERED_AT = "rendered_at";
    private static final String UPDATE_CHECKED_AT = "update_checked_at";
    private static final String UPDATE_MAPS = "update_maps";
    private static final String UPDATE_TRAFFIC = "update_traffic";
    private static final String UPDATE_FAILED = "update_failed";
    private static final String UPDATE_DETAIL = "update_detail";
    private static final String UPDATE_OK_AT = "update_complete_check_at";
    private static final String UPDATE_REMIND_AFTER = "update_remind_after";
    private static final String RUN_PENDING = "run_pending";
    private static final String RUN_UPDATE = "run_update";
    private static final String IMPORT_SOURCE = "import_source";

    static final String PHASE_IDLE = "IDLE";
    static final String PHASE_MAP = "KARTE";
    static final String PHASE_TRAFFIC = "VERKEHR";
    static final String PHASE_FONTS = "SCHRIFTEN";
    static final String PHASE_FINAL = "FINAL";
    static final String PHASE_ERROR = "ERROR";
    static final String PHASE_CANCELLED = "CANCELLED";

    private GermanyOfflineStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static File root(Context c) {
        File base = c.getExternalFilesDir(null);
        return base == null ? null : new File(base, "offline_germany");
    }

    static File mapsDir(Context c) {
        File root = root(c);
        return root == null ? null : new File(root, "maps");
    }

    static File mapFile(Context c, String region) {
        File dir = mapsDir(c);
        return dir == null ? null : new File(dir, region + "-shortbread-1.0.mbtiles");
    }

    static File mapPartFile(Context c, String region) {
        File f = mapFile(c, region);
        return f == null ? null : new File(f.getAbsolutePath() + ".part");
    }

    static File glyphRoot(Context c) {
        File root = root(c);
        return root == null ? null : new File(root, "glyphs");
    }

    static String glyphStack() {
        return "Open Sans Regular,Arial Unicode MS Regular";
    }

    static File glyphDir(Context c) {
        File root = glyphRoot(c);
        return root == null ? null : new File(root, glyphStack());
    }

    static File glyphFile(Context c, int start) {
        File dir = glyphDir(c);
        return dir == null ? null : new File(dir, start + "-" + (start + 255) + ".pbf");
    }

    static String[] glyphUrls(int start) {
        String file = start + "-" + (start + 255) + ".pbf";
        return new String[]{GLYPH_PRIMARY_BASE + file, GLYPH_GITHUB_BASE + file};
    }

    static int[] glyphStarts() { return GLYPH_STARTS.clone(); }

    static String mapUrl(String region) {
        return GEOFABRIK + region + "-shortbread-1.0.mbtiles";
    }

    static String regionName(String region) {
        for (int i = 0; i < REGION_KEYS.length; i++) {
            if (REGION_KEYS[i].equals(region)) return REGION_NAMES[i];
        }
        return region == null ? "" : region;
    }

    static boolean mapsReady(Context c) {
        for (String region : REGION_KEYS) {
            File f = mapFile(c, region);
            if (f == null || !f.isFile() || f.length() < 1_000_000L) return false;
        }
        return true;
    }

    static boolean fontsReady(Context c) {
        for (int start : GLYPH_STARTS) {
            if (!OfflineGlyphValidator.isUsable(glyphFile(c, start), start)) return false;
        }
        return true;
    }

    static boolean trafficReady(Context c) {
        for (String region : REGION_KEYS) {
            if (!TrafficDataManager.status(c, region).ready) return false;
            try (TrafficDataStore.Reader reader = TrafficDataStore.open(c, region)) {
                if (reader == null || !"speed-camera".equals(reader.meta("road_profile"))) return false;
            } catch (Throwable ignored) { return false; }
        }
        return true;
    }

    /** Cheap runtime state only. Full ready() validates all files/DBs and must not run in UI polling loops. */
    static boolean markedReady(Context c) {
        return prefs(c).getBoolean(READY, false);
    }

    // ready() opens all regional SQLite DBs and checks every map/glyph file. Menus call it several
    // times on the UI thread, so a positive result is cached per build signature for a few minutes.
    private static volatile String readyCacheKey;
    private static volatile long readyCacheAt;
    private static volatile long bytesCache = -1L;
    private static volatile long bytesCacheAt;

    /** Drop cached validation results (call after the package was deleted or rebuilt). */
    static void invalidateCaches() {
        readyCacheKey = null;
        bytesCache = -1L;
    }

    static boolean ready(Context c) {
        if (!markedReady(c)) return false;
        String key = signature(c);
        long now = android.os.SystemClock.elapsedRealtime();
        if (key.equals(readyCacheKey) && now - readyCacheAt < 300_000L) return true;
        boolean ok = mapsReady(c) && fontsReady(c) && trafficReady(c);
        if (ok) { readyCacheKey = key; readyCacheAt = now; }
        else readyCacheKey = null;
        return ok;
    }

    /** Computes the expensive values in the background so menus open from warm caches. */
    static void warmUpAsync(Context c, Runnable onInvalid) {
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                // The UI starts from the cheap READY flag; the real validation runs here.
                if (markedReady(app) && !ready(app)) {
                    prefs(app).edit().putBoolean(READY, false).apply();
                    invalidateCaches();
                    DiagLog.add("Offline Deutschland: Paket unvollständig – READY zurückgesetzt");
                    if (onInvalid != null)
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onInvalid);
                    return;
                }
                installedBytes(app);
            } catch (Throwable ignored) {}
        }, "germany-store-warmup");
        t.setDaemon(true);
        t.start();
    }

    static String signature(Context c) {
        // Deliberately excludes download/file sizes. Those change continuously while .part files grow
        // and previously caused MainActivity to recreate every few seconds during a long install.
        SharedPreferences p = prefs(c);
        return p.getBoolean(READY, false) + ":" + p.getLong(BUILT_AT, 0L);
    }

    static long installedBytes(Context c) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (bytesCache >= 0L && now - bytesCacheAt < 30_000L) return bytesCache;
        long value = computeInstalledBytes(c);
        bytesCache = value;
        bytesCacheAt = now;
        return value;
    }

    private static long computeInstalledBytes(Context c) {
        long total = 0L;
        File maps = mapsDir(c);
        if (maps != null) total += directoryBytes(maps);
        File glyphs = glyphRoot(c);
        if (glyphs != null) total += directoryBytes(glyphs);
        for (String region : REGION_KEYS) {
            File db = TrafficDataStore.dbFile(c, region);
            if (db != null && db.isFile()) total += Math.max(0L, db.length());
        }
        return total;
    }

    private static long directoryBytes(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        long sum = 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) sum += directoryBytes(child);
        return sum;
    }

    static long freeBytes(Context c) {
        File base = root(c);
        if (base == null) base = c.getExternalFilesDir(null);
        if (base == null) return -1L;
        try { return new StatFs(base.getAbsolutePath()).getAvailableBytes(); }
        catch (Throwable ignored) { return -1L; }
    }

    static void start(Context c) {
        // First install/resume is strictly local-first: valid existing artifacts are authoritative.
        // A ready package is never updated through this path; updates require the explicit
        // user-triggered HEAD check and confirmation in OfflineMapsActivity.
        if (ready(c)) return;
        startService(c, false);
    }

    static void startUpdate(Context c) {
        if (!ready(c)) return;
        startService(c, true);
    }

    /** Import maps from a USB stick/folder (see {@link UsbImportSource}); builds everything locally. */
    static void startImport(Context c, String sourceSpec) {
        if (sourceSpec == null || active(c)) return;
        prefs(c).edit().putString(IMPORT_SOURCE, sourceSpec).commit();
        startService(c, false);
    }

    static String importSource(Context c) { return prefs(c).getString(IMPORT_SOURCE, null); }

    private static void startService(Context c, boolean updateExisting) {
        markRunRequested(c, updateExisting);
        launchService(c, updateExisting);
    }

    private static void launchService(Context c, boolean updateExisting) {
        Intent intent = new Intent(c, GermanyOfflineService.class)
                .setAction(ACTION_INSTALL)
                .putExtra(EXTRA_UPDATE_EXISTING, updateExisting);
        String importSource = importSource(c);
        if (importSource != null) intent.putExtra(EXTRA_IMPORT_SOURCE, importSource);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intent);
        else c.startService(intent);
    }

    /** Persist the user's long-running request before Android gets a chance to kill the process. */
    static void markRunRequested(Context c, boolean updateExisting) {
        prefs(c).edit()
                .putBoolean(RUN_PENDING, true)
                .putBoolean(RUN_UPDATE, updateExisting)
                .commit();
    }

    static boolean runPending(Context c) {
        return prefs(c).getBoolean(RUN_PENDING, false);
    }

    static boolean pendingUpdate(Context c) {
        return prefs(c).getBoolean(RUN_UPDATE, false);
    }

    /**
     * Best-effort recovery after ACC/ignition process death, Android service recreation or a real boot.
     * Calling this repeatedly is safe: GermanyOfflineService has its own single-run guard.
     */
    static void autoResumePending(Context c) {
        if (c == null || !runPending(c) || GermanyOfflineService.isRunningInProcess()) return;
        try {
            boolean update = pendingUpdate(c);
            DiagLog.add(update
                    ? "Deutschland Offline: ausstehende Aktualisierung automatisch fortsetzen"
                    : "Deutschland Offline: ausstehenden Aufbau automatisch fortsetzen");
            launchService(c.getApplicationContext(), update);
        } catch (Throwable error) {
            // Keep RUN_PENDING intact. The visible launcher or BOOT_COMPLETED can retry later.
            DiagLog.add("Deutschland Offline: automatische Wiederaufnahme wartet – "
                    + error.getClass().getSimpleName());
        }
    }

    /** Keep the UI from declaring a live multi-minute PBF pass stale. */
    static void touch(Context c) {
        SharedPreferences p = prefs(c);
        String phase = p.getString(PHASE, PHASE_IDLE);
        if (PHASE_MAP.equals(phase) || PHASE_TRAFFIC.equals(phase)
                || PHASE_FONTS.equals(phase) || PHASE_FINAL.equals(phase)) {
            p.edit().putLong(UPDATED, System.currentTimeMillis()).apply();
        }
    }


    static void saveUpdateCheck(Context c, GermanyOfflineUpdateChecker.Result result) {
        if (result == null || result.busy) return;
        SharedPreferences.Editor e = prefs(c).edit();
        if (result.checkedAt > 0L) e.putLong(UPDATE_CHECKED_AT, result.checkedAt);
        if (result.complete()) e.putLong(UPDATE_OK_AT, System.currentTimeMillis());
        e.putInt(UPDATE_MAPS, result.mapUpdates.size())
                .putInt(UPDATE_TRAFFIC, result.trafficUpdates.size())
                .putInt(UPDATE_FAILED, result.failedChecks)
                .putString(UPDATE_DETAIL, result.summary())
                .apply();
    }

    static UpdateStatus updateStatus(Context c) {
        SharedPreferences p = prefs(c);
        long checkedAt = p.getLong(UPDATE_CHECKED_AT, 0L);
        return new UpdateStatus(checkedAt, p.getInt(UPDATE_MAPS, 0),
                p.getInt(UPDATE_TRAFFIC, 0), p.getInt(UPDATE_FAILED, 0),
                p.getString(UPDATE_DETAIL, ""));
    }

    /** Time of the last check that reached every region (manual or automatic), 0 if none. */
    static long lastCompleteCheckAt(Context c) { return prefs(c).getLong(UPDATE_OK_AT, 0L); }

    /** Number of regions with a newer map or traffic package found by the last check. */
    static int pendingUpdateRegions(Context c) {
        SharedPreferences p = prefs(c);
        return p.getInt(UPDATE_MAPS, 0) + p.getInt(UPDATE_TRAFFIC, 0);
    }

    static long updateRemindAfter(Context c) { return prefs(c).getLong(UPDATE_REMIND_AFTER, 0L); }

    static void setUpdateRemindAfter(Context c, long whenMs) {
        prefs(c).edit().putLong(UPDATE_REMIND_AFTER, whenMs).apply();
    }

    /** Known newer regions and the "Später" pause is over: the launcher should ask now. */
    static boolean updatePromptDue(Context c) {
        return MapUpdateSchedule.promptDue(System.currentTimeMillis(), pendingUpdateRegions(c), updateRemindAfter(c));
    }

    /** The weekly automatic check is due. */
    static boolean updateCheckDue(Context c) {
        return MapUpdateSchedule.checkDue(System.currentTimeMillis(), lastCompleteCheckAt(c));
    }

    /** "Später": ask again after the pause. */
    static void postponeUpdatePrompt(Context c) {
        setUpdateRemindAfter(c, System.currentTimeMillis() + MapUpdateSchedule.LATER_MS);
    }

    static void clearUpdateStatus(Context c) {
        prefs(c).edit().remove(UPDATE_CHECKED_AT).remove(UPDATE_MAPS)
                .remove(UPDATE_TRAFFIC).remove(UPDATE_FAILED).remove(UPDATE_DETAIL)
                .remove(UPDATE_REMIND_AFTER).apply();
    }

    static void cancel(Context c) {
        c.startService(new Intent(c, GermanyOfflineService.class).setAction(ACTION_CANCEL));
    }

    static boolean active(Context c) {
        String p = prefs(c).getString(PHASE, PHASE_IDLE);
        boolean running = PHASE_MAP.equals(p) || PHASE_TRAFFIC.equals(p)
                || PHASE_FONTS.equals(p) || PHASE_FINAL.equals(p);
        long updated = prefs(c).getLong(UPDATED, 0L);
        return running && (updated == 0L || System.currentTimeMillis() - updated < 120_000L);
    }

    static void setProgress(Context c, String phase, int step, int steps,
                            long done, long total, String detail) {
        prefs(c).edit()
                .putString(PHASE, phase)
                .putInt(STEP, step)
                .putInt(STEPS, steps)
                .putLong(DONE, done)
                .putLong(TOTAL, total)
                .putString(DETAIL, detail == null ? "" : detail)
                .putLong(UPDATED, System.currentTimeMillis())
                .apply();
    }

    static void markReady(Context c) {
        invalidateCaches();
        prefs(c).edit()
                .putBoolean(READY, true)
                .remove(RUN_PENDING).remove(RUN_UPDATE).remove(IMPORT_SOURCE)
                .putLong(BUILT_AT, System.currentTimeMillis())
                .putString(PHASE, PHASE_IDLE)
                .putString(DETAIL, "Offline-Karte Deutschland bereit")
                .putLong(UPDATED, System.currentTimeMillis())
                .apply();
    }

    static void markStopped(Context c, String phase, String detail) {
        prefs(c).edit()
                .remove(RUN_PENDING).remove(RUN_UPDATE).remove(IMPORT_SOURCE)
                .putString(PHASE, phase)
                .putString(DETAIL, detail == null ? "" : detail)
                .putLong(UPDATED, System.currentTimeMillis())
                .apply();
    }

    static Status status(Context c) {
        SharedPreferences p = prefs(c);
        String phase = p.getString(PHASE, PHASE_IDLE);
        long updated = p.getLong(UPDATED, 0L);
        boolean stale = (PHASE_MAP.equals(phase) || PHASE_TRAFFIC.equals(phase)
                || PHASE_FONTS.equals(phase) || PHASE_FINAL.equals(phase))
                && updated > 0L && System.currentTimeMillis() - updated > 120_000L;
        return new Status(ready(c), !stale && active(c), stale ? PHASE_ERROR : phase,
                p.getInt(STEP, 0), p.getInt(STEPS, 0), p.getLong(DONE, 0L),
                p.getLong(TOTAL, 0L), stale ? "Verarbeitung wurde unterbrochen – erneut starten"
                        : p.getString(DETAIL, ""), installedBytes(c), freeBytes(c),
                p.getLong(BUILT_AT, 0L));
    }

    static String diagnosticSummary(Context c) {
        Status s = status(c);
        if (s.ready) {
            long roads = 0L, cameras = 0L;
            for (String region : REGION_KEYS) {
                TrafficDataManager.Status t = TrafficDataManager.status(c, region);
                roads += t.roads;
                cameras += t.cameras;
            }
            return "Offline Deutschland: bereit | " + humanBytes(s.bytes)
                    + " | " + roads + " Straßensegmente | " + cameras + " feste Blitzer";
        }
        if (s.active) return "Offline Deutschland: " + s.detail;
        if (PHASE_ERROR.equals(s.phase) || PHASE_CANCELLED.equals(s.phase))
            return "Offline Deutschland: " + s.detail;
        return "Offline Deutschland: nicht installiert";
    }

    static String humanBytes(long bytes) {
        if (bytes < 0L) return "--";
        if (bytes >= 1024L * 1024L * 1024L)
            return String.format(Locale.GERMANY, "%.1f GB", bytes / 1073741824.0);
        return String.format(Locale.GERMANY, "%.0f MB", bytes / 1048576.0);
    }

    /** Called only after MapLibre reports a fully rendered offline Germany frame. */
    static void confirmRendered(Context c) {
        if (!ready(c)) return;
        SharedPreferences p = prefs(c);
        if (p.getLong(RENDERED_AT, 0L) > 0L) return;
        p.edit().putLong(RENDERED_AT, System.currentTimeMillis()).apply();
        DiagLog.add("Offline-Migration: Deutschlandkarte real gerendert – alte Karte kann entfernt werden");
        removeLegacyMaps(c);
    }

    /** Remove the old selectable Mapsforge regions only after Germany is fully usable. */
    static void removeLegacyMaps(Context c) {
        File folder = GermanyRegions.legacyFolder(c);
        if (folder == null || !folder.exists()) return;
        for (String key : GermanyRegions.FILES) {
            File map = GermanyRegions.legacyFile(c, key);
            if (map != null && map.exists()) map.delete();
            File part = new File(folder, key + ".download");
            if (part.exists()) part.delete();
        }
        DiagLog.add("Offline-Migration: alte Bundesland-.map-Dateien entfernt");
    }

    static String deleteAll(Context c) {
        if (active(c)) return "Deutschland-Paket wird gerade verarbeitet";
        File root = root(c);
        deleteTree(root);
        for (String region : REGION_KEYS) TrafficDataManager.delete(c, region);
        prefs(c).edit().clear().apply();
        invalidateCaches();
        return "Offline-Karte Deutschland gelöscht";
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        f.delete();
    }

    static final class UpdateStatus {
        final long checkedAt;
        final int maps, traffic, failed;
        final String detail;

        UpdateStatus(long checkedAt, int maps, int traffic, int failed, String detail) {
            this.checkedAt = checkedAt;
            this.maps = maps;
            this.traffic = traffic;
            this.failed = failed;
            this.detail = detail == null ? "" : detail;
        }

        boolean checked() { return checkedAt > 0L; }
    }

    static final class Status {
        final boolean ready, active;
        final String phase, detail;
        final int step, steps;
        final long done, total, bytes, free, builtAt;

        Status(boolean ready, boolean active, String phase, int step, int steps,
               long done, long total, String detail, long bytes, long free, long builtAt) {
            this.ready = ready;
            this.active = active;
            this.phase = phase;
            this.step = step;
            this.steps = steps;
            this.done = done;
            this.total = total;
            this.detail = detail;
            this.bytes = bytes;
            this.free = free;
            this.builtAt = builtAt;
        }
    }
}
