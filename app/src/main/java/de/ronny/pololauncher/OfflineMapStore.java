package de.ronny.pololauncher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** German state maps distributed by the mapsforge project; no OSM tile scraping. */
final class OfflineMapStore {
    interface CompletionCallback { void onComplete(Activity owner, String message); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Two bounded workers let a later download finish even if a damaged/storage-backed
    // Mapsforge reader hangs. The queue is deliberately small: resume polling must not
    // build an unbounded list of validations.
    private static final ThreadPoolExecutor COMPLETION_WORKER = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), r -> {
        Thread t = new Thread(r, "offline-map-validator");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());
    private static final CompletionListeners<Activity> COMPLETIONS = new CompletionListeners<>();
    private static final ThreadPoolExecutor UI_IO = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), r -> {
        Thread t = new Thread(r, "offline-map-storage"); t.setDaemon(true); return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    /** Remote file size probe (HEAD); injectable so tests never touch the network. */
    interface SizeProbe { long size(String url); }
    static volatile SizeProbe sizeProbe = OfflineMapStore::probeRemoteSize;

    interface UiCallback { void onLoaded(Activity owner, UiState state, String error); }
    interface MapAction { String run(Context app); }

    static void loadUiStateAsync(Activity owner, UiCallback result) {
        Context app = owner.getApplicationContext();
        WeakReference<Activity> ownerRef = new WeakReference<>(owner);
        try {
            UI_IO.execute(() -> {
                UiState state = null;
                String error = null;
                try { state = readUiState(app); }
                catch (Throwable e) { error = "Kartenstatus: " + e.getClass().getSimpleName(); }
                final UiState snapshot = state;
                final String failure = error;
                MAIN.post(() -> {
                    Activity activity = ownerRef.get();
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        result.onLoaded(activity, snapshot, failure);
                });
            });
        } catch (RejectedExecutionException e) {
            result.onLoaded(owner, null, "Kartenverwaltung beschäftigt – erneuter Versuch folgt");
        }
    }

    static void runActionAsync(Activity owner, MapAction action, CompletionCallback result) {
        Context app = owner.getApplicationContext();
        WeakReference<Activity> ownerRef = new WeakReference<>(owner);
        try {
            UI_IO.execute(() -> {
                String message;
                try { message = action.run(app); }
                catch (Throwable e) { message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
                final String finished = message;
                MAIN.post(() -> {
                    Activity activity = ownerRef.get();
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        result.onComplete(activity, finished);
                });
            });
        } catch (RejectedExecutionException e) {
            result.onComplete(owner, "Kartenverwaltung beschäftigt – bitte erneut versuchen");
        }
    }
    static final String[] NAMES = {"Baden-Württemberg", "Bayern", "Berlin", "Brandenburg", "Bremen",
            "Hamburg", "Hessen", "Mecklenburg-Vorpommern", "Niedersachsen", "Nordrhein-Westfalen",
            "Rheinland-Pfalz", "Saarland", "Sachsen-Anhalt", "Sachsen", "Schleswig-Holstein", "Thüringen"};
    static final String[] FILES = {"baden-wuerttemberg", "bayern", "berlin", "brandenburg", "bremen",
            "hamburg", "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen", "schleswig-holstein", "thueringen"};
    static final int[] SIZE_MB = {403, 549, 52, 191, 13, 28, 218, 96, 347, 581, 176, 34, 122, 165, 108, 112};
    private static final String BASE = "https://download.mapsforge.org/maps/v5/europe/germany/";
    private static final String PREF = "offline_maps_v1";
    private static final String SELECTED = "selected";
    private static final String DOWNLOAD = "download_id";
    private static final String DOWNLOADING = "downloading";
    private static final String ACTIVATE_AFTER_DOWNLOAD = "activate_after_download";

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    static int indexOf(String key) {
        for (int i = 0; i < FILES.length; ++i) if (FILES[i].equals(key)) return i;
        return -1;
    }
    static File folder(Context c) {
        File base = c.getExternalFilesDir(null);
        return base == null ? null : new File(base, "offline_maps");
    }
    static File file(Context c, String key) {
        File dir = folder(c);
        return indexOf(key) < 0 || dir == null ? null : new File(dir, key + ".map");
    }
    static String selected(Context c) { return prefs(c).getString(SELECTED, ""); }
    static boolean installed(Context c, String key) {
        File f = file(c, key);
        return f != null && f.isFile() && f.length() >= 1_000_000;
    }
    static File selectedFile(Context c) {
        String key = selected(c);
        return installed(c, key) ? file(c, key) : null;
    }
    static String selectedSignature(Context c) {
        String key = selected(c);
        File selected = selectedFile(c);
        return selected == null ? key + ":missing"
                : key + ':' + selected.length() + ':' + selected.lastModified();
    }
    static void select(Context c, String key) {
        if (installed(c, key)) prefs(c).edit().putString(SELECTED, key).apply();
    }
    static long activeId(Context c) { return prefs(c).getLong(DOWNLOAD, -1); }
    static String activeKey(Context c) { return prefs(c).getString(DOWNLOADING, ""); }

    static long installedBytes(Context c) {
        long bytes = 0L;
        for (String key : FILES) {
            File map = file(c, key);
            if (map != null && map.isFile()) bytes += Math.max(0L, map.length());
        }
        return bytes;
    }

    static long availableBytes(Context c) {
        File dir = folder(c);
        File target = dir != null && dir.exists() ? dir : c.getExternalFilesDir(null);
        if (target == null) return -1L;
        try { return new StatFs(target.getAbsolutePath()).getAvailableBytes(); }
        catch (Throwable ignored) { return -1L; }
    }

    static synchronized String cancelActive(Context c) {
        long id = activeId(c);
        String key = activeKey(c);
        if (id < 0L) return "Kein Download aktiv";
        DownloadManager manager = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) try { manager.remove(id); } catch (Throwable ignored) {}
        File dir = folder(c);
        File part = dir == null ? null : new File(dir, key + ".download");
        boolean removed = part == null || !part.exists() || part.delete();
        clearDownloadState(c);
        return removed ? "Download abgebrochen" : "Download beendet; unvollständige Datei konnte nicht entfernt werden";
    }

    static synchronized String deleteInstalled(Context c, String key) {
        if (indexOf(key) < 0) return "Region unbekannt";
        if (key.equals(activeKey(c)) && activeId(c) >= 0L)
            return "Diese Region wird gerade heruntergeladen – zuerst den Download abbrechen";
        File map = file(c, key);
        if (map == null || !map.exists()) return "Karte ist nicht installiert";
        if (!map.delete()) return "Kartendatei konnte nicht gelöscht werden";
        File backup = new File(map.getParentFile(), map.getName() + ".backup");
        if (backup.exists()) backup.delete();
        if (key.equals(selected(c))) prefs(c).edit().remove(SELECTED).apply();
        return "Karte " + NAMES[indexOf(key)] + " wurde gelöscht";
    }

    /** Call from a worker thread: probes the real file size first, without holding the store lock. */
    static long start(Context c, String key) {
        int i = indexOf(key);
        long expected = i < 0 ? -1L : sizeProbe.size(BASE + key + ".map");
        return startLocked(c, key, expected);
    }

    private static long probeRemoteSize(String url) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(4_000);
            connection.setInstanceFollowRedirects(true);
            return connection.getResponseCode() == 200 ? connection.getContentLengthLong() : -1L;
        } catch (Exception unavailable) {
            return -1L;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static synchronized long startLocked(Context c, String key, long expectedBytes) {
        int i = indexOf(key);
        if (i < 0 || activeId(c) >= 0) throw new IllegalStateException("Download bereits aktiv oder Region unbekannt");
        File dir = folder(c);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) throw new IllegalStateException("Speicher nicht verfügbar");
        StatFs stats = new StatFs(dir.getAbsolutePath());
        // The size table goes stale as maps grow; prefer the size the server reports now.
        long sizeMb = expectedBytes > 0L ? (expectedBytes + 1_048_575L) / 1_048_576L : SIZE_MB[i];
        if (stats.getAvailableBytes() < (sizeMb + 80L) * 1024L * 1024L)
            throw new IllegalStateException("Zu wenig freier Speicher (mindestens " + (sizeMb + 80) + " MB nötig)");
        File part = new File(dir, key + ".download");
        if (part.exists() && !part.delete()) throw new IllegalStateException("Alte unvollständige Datei nicht entfernbar");
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(BASE + key + ".map"));
        request.setTitle("Polo Offline-Karte: " + NAMES[i]);
        request.setDescription("Mapsforge / OpenStreetMap");
        request.setAllowedOverMetered(true); // WLAN und mobile Daten ausdrücklich erlaubt.
        request.setAllowedOverRoaming(false); // Roaming nicht stillschweigend aktivieren.
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(c, null, "offline_maps/" + key + ".download");
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) throw new IllegalStateException("Android-Downloadverwaltung fehlt");
        boolean activateAfter = !installed(c, key) || key.equals(selected(c));
        long id = dm.enqueue(request);
        prefs(c).edit().putLong(DOWNLOAD, id).putString(DOWNLOADING, key)
                .putBoolean(ACTIVATE_AFTER_DOWNLOAD, activateAfter).apply();
        return id;
    }

    /** Call on resume, after a download notification, and before displaying the map. */
    static String finishIfComplete(Context c) {
        return finishIfComplete(c, activeId(c));
    }

    private static String finishIfComplete(Context c, long expectedId) {
        long id = activeId(c);
        String key = activeKey(c);
        boolean activateAfter = prefs(c).getBoolean(ACTIVATE_AFTER_DOWNLOAD, true);
        if (id < 0 || id != expectedId || indexOf(key) < 0) return null;
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return null;
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur == null || !cur.moveToFirst()) {
                return finishTerminal(c, id, key, "Download nicht mehr vorhanden");
            }
            int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) return null;
            if (status != DownloadManager.STATUS_SUCCESSFUL)
                return finishTerminal(c, id, key, "Download fehlgeschlagen (Android-Fehler "
                        + cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) + ")");
            File dir = folder(c);
            if (dir == null) return finishTerminal(c, id, key, "Speicher nicht verfügbar");
            File part = new File(dir, key + ".download");
            File output = new File(dir, key + ".map");
            if (!part.isFile() || part.length() < 1_000_000)
                return finishTerminal(c, id, key, "Kartendatei ist leer oder unvollständig");
            String validation = validateMap(part);
            if (validation != null) return finishTerminal(c, id, key, validation);
            synchronized (OfflineMapStore.class) {
                // Cancellation or a replacement may have happened while the large file
                // was being validated. Never activate a download that no longer owns state.
                if (!isActive(c, id, key)) return null;
                String activation = activateSafely(part, output);
                boolean inPlace = activation == null || MapFileActivation.activatedWithWarning(activation);
                if (!inPlace) {
                    clearDownloadState(c);
                    return activation;
                }
                if (activateAfter) select(c, key);
                clearDownloadState(c);
                return "Karte " + NAMES[indexOf(key)] + " ist bereit"
                        + (activation == null ? "" : " (" + activation + ")");
            }
        } catch (Exception e) { return "Downloadprüfung: " + e.getClass().getSimpleName(); }
    }

    /** Queries DownloadManager and validates the potentially large file off the UI thread. */
    static void finishIfCompleteAsync(Activity owner, CompletionCallback result) {
        Context app = owner.getApplicationContext();
        final long id = activeId(app);
        if (id < 0L) return;
        // Separate, short registry lock: activation may hold the store lock during
        // filesystem I/O. A second Activity must join, not lose its notification.
        if (!COMPLETIONS.join(id, owner, (activity, message) -> {
            if (result != null && !activity.isFinishing() && !activity.isDestroyed())
                result.onComplete(activity, message);
        })) return;
        try {
            COMPLETION_WORKER.execute(() -> {
                String message;
                try { message = finishIfComplete(app, id); }
                catch (Throwable error) { message = "Downloadprüfung: " + error.getClass().getSimpleName(); }
                final String finished = message;
                MAIN.post(() -> COMPLETIONS.finish(id, finished));
            });
        } catch (RejectedExecutionException ignored) {
            COMPLETIONS.finish(id, null);
        }
    }

    /** Filesystem and DownloadManager snapshot; call only from a worker. */
    static UiState readUiState(Context context) {
        boolean[] installed = new boolean[FILES.length];
        for (int i = 0; i < FILES.length; i++) installed[i] = installed(context, FILES[i]);
        return new UiState(selected(context), activeKey(context), activeId(context), installed,
                installedBytes(context), availableBytes(context), progress(context));
    }

    static final class UiState {
        final String selected, downloading;
        final long downloadId, used, free;
        final boolean[] installed;
        final long[] progress;
        UiState(String selected, String downloading, long downloadId, boolean[] installed,
                long used, long free, long[] progress) {
            this.selected = selected; this.downloading = downloading; this.downloadId = downloadId;
            this.installed = installed; this.used = used; this.free = free; this.progress = progress;
        }
    }

    private static synchronized String finishTerminal(Context c, long id, String key, String message) {
        if (!isActive(c, id, key)) return null;
        clearDownloadState(c);
        return message;
    }

    private static boolean isActive(Context c, long id, String key) {
        return activeId(c) == id && key.equals(activeKey(c));
    }

    private static void clearDownloadState(Context c) {
        prefs(c).edit().remove(DOWNLOAD).remove(DOWNLOADING)
                .remove(ACTIVATE_AFTER_DOWNLOAD).apply();
    }

    private static String validateMap(File candidate) {
        MapFile check = null;
        try {
            check = new MapFile(candidate);
            if (check.boundingBox() == null) return "Kartendatei enthält keinen gültigen Kartenbereich";
            return null;
        } catch (Throwable t) {
            return "Kartendatei ist beschädigt (" + t.getClass().getSimpleName() + ')';
        } finally {
            if (check != null) try { check.close(); } catch (Throwable ignored) {}
        }
    }

    /** Activates the verified download without risking an already working map. */
    private static String activateSafely(File part, File output) {
        return MapFileActivation.activate(part, output);
    }

    static long[] progress(Context c) {
        long id = activeId(c);
        if (id < 0) return new long[]{0, 0, -1};
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return new long[]{0, 0, -1};
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur == null || !cur.moveToFirst()) return new long[]{0, 0, -1};
            return new long[]{cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))};
        } catch (Exception e) { return new long[]{0, 0, -1}; }
    }
}
