package de.ronny.pololauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StatFs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Builds the one user-facing Germany offline package sequentially and resumably. */
public final class GermanyOfflineService extends Service {
    private static final String CHANNEL = "germany_offline";
    private static final int NOTIFICATION_ID = 4213;
    private static volatile boolean processRunning;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "germany-offline-builder");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean cancelled;
    private volatile int consecutiveFailures;
    private int totalRetries;
    private int invalidRetries;
    private volatile String lastPhase = GermanyOfflineStore.PHASE_MAP;
    private volatile int lastStep, lastSteps;
    private volatile boolean userCancelled;
    private volatile boolean running;
    private volatile boolean updateExisting;
    private volatile String importSpec;
    private UsbImportSource importSource;
    private volatile boolean serviceDestroying;
    private PowerManager.WakeLock wakeLock;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "germany-offline-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeatTask;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Offline-Karte Deutschland", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Download von Deutschlandkarte, Tempolimits und Blitzern");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (GermanyOfflineStore.ACTION_CANCEL.equals(action)) {
            userCancelled = true;
            cancelled = true;
            GermanyOfflineStore.markStopped(this, GermanyOfflineStore.PHASE_CANCELLED,
                    "Abbruch angefordert …");
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }

        // START_REDELIVER_INTENT normally gives us the original ACTION_INSTALL again. The null-intent
        // fallback additionally covers vendor firmware that recreates a sticky service without it.
        boolean restorePending = intent == null && GermanyOfflineStore.runPending(this);
        if (!GermanyOfflineStore.ACTION_INSTALL.equals(action) && !restorePending) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (running) return START_REDELIVER_INTENT;

        cancelled = false;
        userCancelled = false;
        totalRetries = 0;
        invalidRetries = 0;
        consecutiveFailures = 0;
        serviceDestroying = false;
        updateExisting = intent != null
                ? intent.getBooleanExtra(GermanyOfflineStore.EXTRA_UPDATE_EXISTING,
                        GermanyOfflineStore.pendingUpdate(this))
                : GermanyOfflineStore.pendingUpdate(this);
        String requestedImport = intent == null ? null : intent.getStringExtra(GermanyOfflineStore.EXTRA_IMPORT_SOURCE);
        importSpec = requestedImport != null ? requestedImport
                : (intent == null ? GermanyOfflineStore.importSource(this) : null);
        GermanyOfflineStore.markRunRequested(this, updateExisting);
        running = true;
        processRunning = true;
        DiagLog.add((restorePending ? "Deutschland Offline: Dienst automatisch wiederhergestellt · " : "")
                + (updateExisting
                ? "Aktualisierung gestartet"
                : "Aufbau fortsetzen – gültige Teildaten werden beibehalten"));
        startForeground(NOTIFICATION_ID,
                notification("Offline Deutschland", "Hintergrundaufbau wird vorbereitet …", 0, 0, true));
        acquireWakeLock();
        startHeartbeat();
        worker.execute(this::runInstall);
        // If Android/ACC kills the service process, redeliver this request instead of waiting for
        // another button press. The persisted RUN_PENDING flag is a second recovery layer.
        return START_REDELIVER_INTENT;
    }

    private void runInstall() {
        try {
            while (runInstallOnce()) { /* transient failure: waited, now continue where we stopped */ }
        } finally {
            running = false;
            processRunning = false;
            stopHeartbeat();
            releaseWakeLock();
            if (!serviceDestroying) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    /** Returns true when a network-type failure was waited out and the run should simply continue. */
    private boolean runInstallOnce() {
        try {
            File root = GermanyOfflineStore.root(this);
            File maps = GermanyOfflineStore.mapsDir(this);
            File glyphs = GermanyOfflineStore.glyphDir(this);
            if (root == null || maps == null || glyphs == null)
                throw new IllegalStateException("Speicher nicht verfügbar");
            if ((!root.exists() && !root.mkdirs()) || (!maps.exists() && !maps.mkdirs())
                    || (!glyphs.exists() && !glyphs.mkdirs()))
                throw new IllegalStateException("Offline-Ordner nicht verfügbar");

            importSource = importSpec == null ? null : UsbImportSource.open(this, importSpec);
            if (importSpec != null) {
                if (importSource == null) throw new IllegalStateException("USB-Quelle nicht lesbar – bitte Stick prüfen");
                DiagLog.add("USB-Import: " + importSource.count() + " Dateien ("
                        + GermanyOfflineStore.humanBytes(importSource.totalBytes()) + ") gefunden");
            }

            final int totalSteps = GermanyOfflineStore.REGION_KEYS.length * 2
                    + GermanyOfflineStore.glyphStarts().length + 1;
            int step = 0;

            // 1) Vector base-map archives (Shortbread incl. POI layer; the style shows POI symbols from zoom 14).
            for (int i = 0; i < GermanyOfflineStore.REGION_KEYS.length; i++) {
                checkCancelled();
                String region = GermanyOfflineStore.REGION_KEYS[i];
                String name = GermanyOfflineStore.REGION_NAMES[i];
                step++;
                installMap(region, name, step, totalSteps);
            }

            // 2) Offline glyph ranges required by MapLibre Native for street/place labels.
            for (int start : GermanyOfflineStore.glyphStarts()) {
                checkCancelled();
                step++;
                installGlyph(start, step, totalSteps);
            }

            // 3) Compact speed-limit/camera databases from the same OSM source family.
            for (int i = 0; i < GermanyOfflineStore.REGION_KEYS.length; i++) {
                checkCancelled();
                String region = GermanyOfflineStore.REGION_KEYS[i];
                String name = GermanyOfflineStore.REGION_NAMES[i];
                step++;
                installTraffic(region, name, step, totalSteps);
            }

            checkCancelled();
            GermanyOfflineStore.setProgress(this, GermanyOfflineStore.PHASE_FINAL,
                    totalSteps, totalSteps, 0L, 0L, "Prüfe Deutschland-Paket …");
            updateNotification("Offline Deutschland", "Prüfe Deutschland-Paket …", 0, 0, true);
            if (!GermanyOfflineStore.mapsReady(this))
                throw new IllegalStateException("Nicht alle Kartenarchive sind gültig");
            if (!GermanyOfflineStore.fontsReady(this))
                throw new IllegalStateException("Offline-Schriften unvollständig");
            if (!GermanyOfflineStore.trafficReady(this))
                throw new IllegalStateException("Tempolimit-/Blitzerdaten unvollständig");

            GermanyOfflineStore.markReady(this);
            if (updateExisting || importSource != null) GermanyOfflineStore.clearUpdateStatus(this);
            // Legacy Mapsforge is kept until MapLibre has really rendered the new offline map once.
            String message = "Offline-Karte Deutschland bereit (Karte + Tempolimits + Blitzer)";
            DiagLog.add(message);
            updateNotification("Offline Deutschland", message, 0, 0, false);
        } catch (TrafficPbfScanner.Cancelled cancelledError) {
            if (userCancelled) {
                GermanyOfflineStore.markStopped(this, GermanyOfflineStore.PHASE_CANCELLED,
                        "Deutschland-Aufbau abgebrochen – vorhandene fertige Daten bleiben erhalten");
            } else {
                // Service/process teardown (for example ACC) must not turn into an explicit cancel.
                // RUN_PENDING stays set so Android or the next launcher process can resume automatically.
                DiagLog.add("Deutschland Offline: Hintergrundlauf unterbrochen – automatische Wiederaufnahme vorgemerkt");
            }
        } catch (Throwable error) {
            if (cancelled && !userCancelled) {
                DiagLog.add("Deutschland Offline: Dienst beendet während Verarbeitung – automatische Wiederaufnahme vorgemerkt");
            } else if (userCancelled) {
                GermanyOfflineStore.markStopped(this, GermanyOfflineStore.PHASE_CANCELLED,
                        "Deutschland-Aufbau abgebrochen – vorhandene fertige Daten bleiben erhalten");
            } else if (MapResumePolicy.isTransient(error)) {
                // Connection lost / server hiccup: NOT an error for the user. RUN_PENDING stays set, the
                // part files stay on disk, and we continue automatically (also after a process restart).
                consecutiveFailures++;
                if (++totalRetries > 100) {
                    String message = "Offline Deutschland: zu viele Verbindungsfehler – " + error.getClass().getSimpleName();
                    DiagLog.add(message);
                    GermanyOfflineStore.markStopped(this, GermanyOfflineStore.PHASE_ERROR, message);
                    updateNotification("Offline Deutschland", message, 0, 0, false);
                    return false;
                }
                long wait = MapResumePolicy.backoffMs(consecutiveFailures);
                DiagLog.add("Deutschland Offline: Verbindung unterbrochen ("
                        + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " " + error.getMessage())
                        + ") – automatischer neuer Versuch in " + (wait / 1000L) + " s, Fortsetzung an gleicher Stelle");
                return waitBeforeRetry(wait);
            } else {
                String message = "Offline Deutschland fehlgeschlagen: "
                        + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : " – " + error.getMessage());
                DiagLog.add(message);
                GermanyOfflineStore.markStopped(this, GermanyOfflineStore.PHASE_ERROR, message);
                updateNotification("Offline Deutschland", message, 0, 0, false);
            }
        }
        return false;
    }

    /** Waits (cancellable, heartbeat alive) and reports why. Returns false if the user cancelled or the service is going down. */
    private boolean waitBeforeRetry(long waitMs) {
        long end = System.currentTimeMillis() + waitMs;
        String text = "Verbindung unterbrochen – es geht automatisch weiter (Versuch " + (consecutiveFailures + 1) + ")";
        GermanyOfflineStore.setProgress(this, lastPhase, lastStep, lastSteps, 0L, 0L, text);
        updateNotification("Offline Deutschland", text, 0, 0, true);
        while (System.currentTimeMillis() < end) {
            if (cancelled || Thread.currentThread().isInterrupted()) return false;
            try { Thread.sleep(1000L); } catch (InterruptedException e) { return false; }
        }
        return !cancelled;
    }

    private void installMap(String region, String name, int step, int totalSteps) throws Exception {
        File target = GermanyOfflineStore.mapFile(this, region);
        File part = GermanyOfflineStore.mapPartFile(this, region);
        if (target == null || part == null) throw new IllegalStateException("Kartenspeicher fehlt");

        UsbImportSource.Entry usbMap = importSource == null ? null
                : importSource.find(UsbImportLayout.mapName(region));
        if (usbMap != null && importMap(usbMap, name, target, part, step, totalSteps)) return;

        boolean localValid = target.isFile() && validMbtiles(target);
        // RESUME means exactly that: continue the interrupted build. A locally valid MBTiles
        // must never be replaced merely because HEAD now reports another size/date. Geofabrik
        // can publish a newer extract while a multi-hour build is in progress. Remote replacement is
        // only allowed after the user explicitly checked for updates and confirmed the READY package update.
        if (localValid && !updateExisting) {
            if (part.exists()) part.delete();
            progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, 0L, 0L,
                    "Karte " + name + " vorhanden – beim Fortsetzen übernommen");
            return;
        }

        // A complete .part can survive a process/service interruption between download and
        // atomic activation. In resume mode activate it locally instead of deleting/downloading it.
        if (!localValid && !updateExisting && part.isFile() && validMbtiles(part)) {
            atomicReplace(part, target);
            DiagLog.add("Deutschland Karte " + name
                    + ": vollständigen Teil-Download beim Fortsetzen übernommen");
            progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, 0L, 0L,
                    "Karte " + name + " aus vorhandenem Download übernommen");
            return;
        }

        Remote remote = probe(GermanyOfflineStore.mapUrl(region));
        if (localValid && (!remote.available || !GermanyOfflineUpdatePolicy.updateAvailable(
                target.length(), target.lastModified(), remote.size, remote.modified))) {
            progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, 0L, 0L,
                    "Karte " + name + " bereits aktuell");
            return;
        }

        progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, 0L, remote.size,
                "Karte " + name + " wird geladen …");
        download(GermanyOfflineStore.mapUrl(region), part, remote, (done, total) ->
                progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, done, total,
                        "Karte " + name + ": " + GermanyOfflineStore.humanBytes(done)
                                + (total > 0L ? " / " + GermanyOfflineStore.humanBytes(total) : "")));
        checkCancelled();
        if (!validMbtiles(part)) {
            part.delete();
            new File(part.getAbsolutePath() + ".pos").delete();
            if (++invalidRetries <= 2) {
                DiagLog.add("Deutschland Karte " + name + ": Download ungültig – wird komplett neu geladen (Versuch "
                        + invalidRetries + "/2)");
                throw new java.io.IOException("MBTiles ungültig: " + name + " – neu laden");
            }
            throw new IllegalStateException("MBTiles ungültig: " + name);
        }
        atomicReplace(part, target);
        if (remote.modified > 0L) target.setLastModified(remote.modified);
    }

    /** Takes a map file from the USB stick. Returns false if the stick copy is unusable (normal flow continues). */
    private boolean importMap(UsbImportSource.Entry usb, String name, File target, File part,
                              int step, int totalSteps) throws Exception {
        boolean localValid = target.isFile() && validMbtiles(target);
        if (localValid && !GermanyOfflineUpdatePolicy.updateAvailable(target.length(), target.lastModified(),
                usb.size(), usb.modified())) {
            progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, 0L, 0L,
                    "Karte " + name + ": Stick nicht neuer – übersprungen");
            return true;
        }
        if (part.exists() && !part.delete())
            throw new IllegalStateException("Temporäre Datei nicht löschbar");
        ensureSpace(part, usb.size());
        copyEntry(usb, part, (done, total) -> progress(GermanyOfflineStore.PHASE_MAP, step, totalSteps, done, total,
                "Karte " + name + " vom Stick: " + GermanyOfflineStore.humanBytes(done)
                        + (total > 0L ? " / " + GermanyOfflineStore.humanBytes(total) : "")));
        checkCancelled();
        if (!validMbtiles(part)) {
            part.delete();
            DiagLog.add("USB-Import: Karte " + name + " auf dem Stick ist ungültig – übersprungen");
            return false;
        }
        atomicReplace(part, target);
        if (usb.modified() > 0L) target.setLastModified(usb.modified());
        DiagLog.add("USB-Import: Karte " + name + " übernommen");
        return true;
    }

    /** Copies a stick file to local storage, reporting progress; the source is not needed afterwards. */
    private void copyEntry(UsbImportSource.Entry source, File target, DownloadProgress progress) throws Exception {
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs()))
            throw new IllegalStateException("Zielordner fehlt");
        byte[] buffer = new byte[256 * 1024];
        long done = 0L, lastUi = 0L;
        try (InputStream input = new BufferedInputStream(source.open(), buffer.length);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target), buffer.length)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                checkCancelled();
                if (read == 0) continue;
                output.write(buffer, 0, read);
                done += read;
                if (done - lastUi >= 4L * 1024L * 1024L) {
                    lastUi = done;
                    progress.update(done, source.size());
                }
            }
        }
        progress.update(done, source.size());
    }

    private void installGlyph(int start, int step, int totalSteps) throws Exception {
        File target = GermanyOfflineStore.glyphFile(this, start);
        if (target == null) throw new IllegalStateException("Schriftspeicher fehlt");
        String range = start + "–" + (start + 255);
        if (OfflineGlyphValidator.isUsable(target, start)) {
            progress(GermanyOfflineStore.PHASE_FONTS, step, totalSteps, 0L, 0L,
                    "Offline-Schrift " + range + " vorhanden");
            return;
        }

        // A previous version could keep a tiny HTTP/proxy error body as a glyph candidate.
        // Remove only that glyph; all already downloaded Germany MBTiles remain untouched.
        if (target.exists()) {
            DiagLog.add("Offline-Schrift " + range + " ungültig (" + target.length()
                    + " B) – wird gezielt neu geladen");
            if (!target.delete()) throw new IllegalStateException("Defekte Glyph-Datei nicht löschbar: " + range);
        }

        File part = new File(target.getAbsolutePath() + ".part");
        // v0.31.55 failed before atomicReplace when a perfectly valid empty glyph range was
        // smaller than 100 bytes. Reuse that already downloaded .part immediately if valid.
        if (OfflineGlyphValidator.isUsable(part, start)) {
            atomicReplace(part, target);
            DiagLog.add("Offline-Schrift " + range + " aus vorhandenem Teil-Download repariert ("
                    + target.length() + " B)");
            progress(GermanyOfflineStore.PHASE_FONTS, step, totalSteps, 0L, 0L,
                    "Offline-Schrift " + range + " repariert");
            return;
        }
        if (part.exists() && !part.delete())
            throw new IllegalStateException("Defekte Teil-Glyph-Datei nicht löschbar: " + range);

        String lastError = "keine Quelle versucht";
        String[] urls = GermanyOfflineStore.glyphUrls(start);
        for (int source = 0; source < urls.length; source++) {
            String sourceName = source == 0 ? "MapLibre" : "GitHub-Fallback";
            for (int attempt = 1; attempt <= 2; attempt++) {
                checkCancelled();
                if (part.exists() && !part.delete())
                    throw new IllegalStateException("Temporäre Glyph-Datei nicht löschbar: " + range);
                progress(GermanyOfflineStore.PHASE_FONTS, step, totalSteps, 0L, 0L,
                        "Offline-Schrift " + range + " · " + sourceName
                                + (attempt > 1 ? " · Versuch " + attempt : "") + " …");
                try {
                    download(urls[source], part, Remote.none(), (done, total) -> {});
                    long bytes = part.isFile() ? part.length() : 0L;
                    if (!OfflineGlyphValidator.isUsable(part, start)) {
                        lastError = sourceName + ": ungültige Datei (" + bytes + " B)";
                        DiagLog.add("Offline-Schrift " + range + " verworfen: " + lastError);
                        if (part.exists()) part.delete();
                        continue;
                    }
                    atomicReplace(part, target);
                    DiagLog.add("Offline-Schrift " + range + " geladen über " + sourceName
                            + " (" + target.length() + " B)");
                    return;
                } catch (TrafficPbfScanner.Cancelled cancelledError) {
                    throw cancelledError;
                } catch (Throwable error) {
                    long bytes = part.isFile() ? part.length() : 0L;
                    lastError = sourceName + ": " + error.getClass().getSimpleName()
                            + (error.getMessage() == null ? "" : " " + error.getMessage())
                            + (bytes > 0L ? " (" + bytes + " B)" : "");
                    DiagLog.add("Offline-Schrift " + range + " Downloadfehler: " + lastError);
                    if (part.exists()) part.delete();
                }
            }
        }
        throw new IllegalStateException("Glyph " + range + " konnte nicht geladen werden – " + lastError);
    }

    private void installTraffic(String region, String name, int step, int totalSteps) throws Exception {
        TrafficDataManager.Status current = TrafficDataManager.status(this, region);
        boolean localTrafficValid = current.ready && hasCompactTraffic(region);
        UsbImportSource.Entry usbPbf = importSource == null ? null
                : importSource.find(UsbImportLayout.trafficName(region));
        Remote remote;
        if (usbPbf != null) {
            remote = new Remote(true, usbPbf.size(), usbPbf.modified());
        } else {
            if (localTrafficValid && !updateExisting) {
                progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, 0L, 0L,
                        "Verkehr " + name + " vorhanden – beim Fortsetzen übernommen");
                return;
            }
            remote = probe(TrafficDataManager.url(region));
        }
        if (localTrafficValid
                && (!remote.available || !GermanyOfflineUpdatePolicy.updateAvailable(
                current.sourceSize, current.sourceModified, remote.size, remote.modified))) {
            progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, 0L, 0L,
                    "Verkehr " + name + " bereits aktuell");
            return;
        }

        File pbf = TrafficDataStore.pbfFile(this, region);
        File tempDb = TrafficDataStore.tempDbFile(this, region);
        if (pbf == null || tempDb == null) throw new IllegalStateException("Verkehrsspeicher fehlt");
        File dir = pbf.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs()))
            throw new IllegalStateException("Verkehrsdaten-Ordner fehlt");

        if (usbPbf != null && !reusableTrafficSource(pbf, remote)) {
            if (pbf.exists() && !pbf.delete())
                throw new IllegalStateException("Alte OSM-Rohdaten nicht löschbar: " + name);
            ensureSpace(pbf, usbPbf.size());
            progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, 0L, usbPbf.size(),
                    "Verkehr " + name + ": kopiere vom Stick …");
            copyEntry(usbPbf, pbf, (done, total) -> progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps,
                    done, total, "Verkehr " + name + " vom Stick: " + GermanyOfflineStore.humanBytes(done)
                            + (total > 0L ? " / " + GermanyOfflineStore.humanBytes(total) : "")));
            if (usbPbf.modified() > 0L) pbf.setLastModified(usbPbf.modified());
            if (!reusableTrafficSource(pbf, remote)) {
                pbf.delete();
                throw new IllegalStateException("OSM-Rohdaten vom Stick unvollständig: " + name);
            }
        }
        boolean completeSource = reusableTrafficSource(pbf, remote);
        boolean activated = false;
        // A half-downloaded PBF is kept (network loss) so the next attempt resumes at that byte.
        boolean keepPartial = remote.modified > 0L && remote.size > 0L;
        try {
            if (completeSource) {
                DiagLog.add("Deutschland Verkehr " + name
                        + ": vollständige OSM-Rohdaten wiederverwendet ("
                        + GermanyOfflineStore.humanBytes(pbf.length()) + ")");
                TrafficDataManager.setActive(this, region, TrafficDataManager.PHASE_ANALYSE);
                progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, 0L, 0L,
                        "Verkehr " + name + ": Download vollständig – Analyse wird fortgesetzt …");
            } else {
                // A shorter partial file is continued by download() (Range); anything else starts fresh.
                if (pbf.exists() && MapResumePolicy.resumeOffset(pbf.length(), remote.size, remote.modified) == 0L
                        && !pbf.delete())
                    throw new IllegalStateException("Unvollständige OSM-Rohdaten nicht löschbar: " + name);
                progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, 0L, remote.size,
                        "Verkehr " + name + ": lade OSM-Rohdaten …");
                download(TrafficDataManager.url(region), pbf, remote, (done, total) -> {
                    TrafficDataManager.setActive(GermanyOfflineService.this, region,
                            TrafficDataManager.PHASE_DOWNLOAD);
                    progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps, done, total,
                            "Verkehr " + name + ": " + GermanyOfflineStore.humanBytes(done)
                                    + (total > 0L ? " / " + GermanyOfflineStore.humanBytes(total) : ""));
                });
                completeSource = true;
            }
            checkCancelled();
            long modified = remote.modified > 0L ? remote.modified : pbf.lastModified();
            long size = remote.size > 0L ? remote.size : pbf.length();

            TrafficPbfScanner.Stats stats;
            try (TrafficDataStore.Builder builder = new TrafficDataStore.Builder(tempDb)) {
                stats = TrafficPbfScanner.scan(pbf, builder, new TrafficPbfScanner.Progress() {
                    @Override public boolean cancelled() { return GermanyOfflineService.this.cancelled; }
                    @Override public void update(String phase, long count, String detail) {
                        TrafficDataManager.setActive(GermanyOfflineService.this, region,
                                "ANALYSE".equals(phase) ? TrafficDataManager.PHASE_ANALYSE
                                        : TrafficDataManager.PHASE_BUILD);
                        progress(GermanyOfflineStore.PHASE_TRAFFIC, step, totalSteps,
                                count, 0L, "Verkehr " + name + ": " + detail);
                    }
                }, false);
                checkCancelled();
                builder.finish(region, modified, size, "speed-camera");
            }
            checkCancelled();
            if (!TrafficDataStore.atomicActivate(this, region, tempDb))
                throw new IllegalStateException("Verkehrsdaten nicht aktivierbar: " + name);
            activated = true;
            TrafficDataManager.markReady(this, region, modified, size, stats.segments, stats.cameras);
            TrafficDataManager.clearActive(this, TrafficDataManager.PHASE_IDLE);
            DiagLog.add("Deutschland Verkehr " + name + ": " + stats.segments
                    + " Segmente, " + stats.cameras + " Blitzer");
        } finally {
            // The downloaded PBF is expensive (hundreds of MB). Keep a complete source
            // after scanner/SQLite failures or cancellation so "Aufbau fortsetzen" can
            // retry the local analysis instead of downloading it again. Delete it only
            // after successful activation. Partial downloads are discarded.
            if (activated) {
                if (pbf.exists()) pbf.delete();
            } else if (completeSource && pbf.isFile()) {
                DiagLog.add("Deutschland Verkehr " + name
                        + ": OSM-Rohdaten für Fortsetzen behalten ("
                        + GermanyOfflineStore.humanBytes(pbf.length()) + ")");
            } else if (pbf.exists() && !(pbf.length() > 0L && keepPartial)) {
                pbf.delete();
            }
            if (tempDb.exists()) tempDb.delete();
        }
    }

    private static boolean reusableTrafficSource(File pbf, Remote remote) {
        if (pbf == null || !pbf.isFile() || pbf.length() < 1_000_000L) return false;
        // Reuse only when HEAD gives us a trustworthy identity for the remote file.
        // That avoids treating an interrupted/HTML error download as valid input.
        if (remote == null || !remote.available || remote.size <= 0L
                || pbf.length() != remote.size) return false;
        return remote.modified <= 0L || pbf.lastModified() <= 0L
                || Math.abs(remote.modified - pbf.lastModified()) < 2_000L;
    }

    private boolean hasCompactTraffic(String region) {
        try (TrafficDataStore.Reader reader = TrafficDataStore.open(this, region)) {
            return reader != null && "speed-camera".equals(reader.meta("road_profile"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void progress(String phase, int step, int steps, long done, long total, String detail) {
        checkCancelled();
        GermanyOfflineStore.setProgress(this, phase, step, steps, done, total, detail);
        lastPhase = phase; lastStep = step; lastSteps = steps;
        if (done > 0L) consecutiveFailures = 0;
        int percent = total > 0L ? (int) Math.min(100L, done * 100L / total) : 0;
        updateNotification("Offline Deutschland " + step + "/" + steps,
                detail, 100, percent, total <= 0L);
    }

    private interface DownloadProgress { void update(long done, long total); }

    private void download(String url, File target, Remote head, DownloadProgress progress) throws Exception {
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs()))
            throw new IllegalStateException("Download-Ordner fehlt");
        // Continue an interrupted .part exactly where it stopped (Range + If-Range). If the server file
        // changed meanwhile, If-Range makes the server answer 200 with the full new file instead.
        // Only bytes that were fsync'ed AND recorded in the ".pos" sidecar count: after a hard power-off
        // the file length can be larger than the data really on flash (zero-filled tail -> corrupt MBTiles).
        File posFile = new File(target.getAbsolutePath() + ".pos");
        long safeLength = Math.min(target.isFile() ? target.length() : 0L, readPos(posFile));
        long offset = MapResumePolicy.resumeOffset(safeLength,
                head == null ? 0L : head.size, head == null ? 0L : head.modified);
        if (offset == 0L) {
            posFile.delete();
            if (target.exists() && !target.delete())
                throw new IllegalStateException("Temporäre Datei nicht löschbar");
        } else if (target.length() > offset) {
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(target, "rw")) {
                raf.setLength(offset);
            }
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PoloLauncher/" + BuildConfig.VERSION_NAME
                + " (Germany offline map)");
        if (offset > 0L) {
            connection.setRequestProperty("Range", "bytes=" + offset + "-");
            connection.setRequestProperty("If-Range", httpDate(head.modified));
        }
        try {
            int code = connection.getResponseCode();
            if (code == 416) {
                target.delete();
                throw new java.io.IOException("Teil-Download verworfen (HTTP 416) – neuer Versuch");
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            boolean resumed = offset > 0L && code == 206;
            long base = resumed ? offset : 0L;
            long total = connection.getContentLengthLong();
            if (total > 0L) total += base;
            else total = head.size;
            if (total > 0L) ensureSpace(target, total - base);
            if (resumed) DiagLog.add("Download fortgesetzt ab " + GermanyOfflineStore.humanBytes(offset)
                    + " – bereits Geladenes wird nicht erneut geholt");
            long modified = connection.getLastModified();
            byte[] buffer = new byte[256 * 1024];
            long done = base, lastUi = base;
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream(), buffer.length);
                 FileOutputStream fileOut = new FileOutputStream(target, resumed);
                 BufferedOutputStream output = new BufferedOutputStream(fileOut, buffer.length)) {
                int read;
                long lastSync = base;
                while ((read = input.read(buffer)) >= 0) {
                    checkCancelled();
                    if (read == 0) continue;
                    output.write(buffer, 0, read);
                    done += read;
                    if (done - lastSync >= 8L * 1024L * 1024L) {
                        output.flush();
                        fileOut.getFD().sync();
                        writePos(posFile, done);
                        lastSync = done;
                    }
                    if (done - lastUi >= 4L * 1024L * 1024L) {
                        lastUi = done;
                        progress.update(done, total);
                    }
                }
                output.flush();
                fileOut.getFD().sync();
            }
            posFile.delete();
            progress.update(done, total);
            if (total > 0L && done != total)
                throw new IllegalStateException("Download unvollständig (" + done + "/" + total + ")");
            if (modified > 0L) target.setLastModified(modified);
        } finally {
            connection.disconnect();
        }
    }

    private static long readPos(File f) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line = r.readLine();
            return line == null ? 0L : Math.max(0L, Long.parseLong(line.trim()));
        } catch (Throwable ignored) { return 0L; }
    }

    private static void writePos(File f, long value) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(Long.toString(value).getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
        } catch (Throwable ignored) {}
    }

    private static String httpDate(long millis) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return f.format(new java.util.Date(millis));
    }

    private static void ensureSpace(File target, long incoming) {
        File parent = target == null ? null : target.getParentFile();
        if (parent == null) return;
        try {
            long free = new StatFs(parent.getAbsolutePath()).getAvailableBytes();
            // Keep enough headroom for one PBF + SQLite build and Android itself.
            long reserve = 2L * 1024L * 1024L * 1024L;
            if (free > 0L && free < incoming + reserve)
                throw new IllegalStateException("Zu wenig Speicher: frei "
                        + GermanyOfflineStore.humanBytes(free) + ", benötigt mindestens "
                        + GermanyOfflineStore.humanBytes(incoming + reserve));
        } catch (IllegalStateException e) { throw e; }
        catch (Throwable ignored) {}
    }

    private static boolean validMbtiles(File file) {
        if (file == null || !file.isFile() || file.length() < 1_000_000L) return false;
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try (Cursor c = db.rawQuery("SELECT tile_data FROM tiles LIMIT 1", null)) {
                if (!c.moveToFirst() || c.isNull(0) || c.getBlob(0).length < 8) return false;
            }
            try (Cursor c = db.rawQuery("SELECT value FROM metadata WHERE name='format' LIMIT 1", null)) {
                if (c.moveToFirst()) {
                    String format = c.getString(0);
                    if (format != null && !format.isEmpty() && !"pbf".equalsIgnoreCase(format)) return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    private static void atomicReplace(File part, File target) throws Exception {
        File backup = new File(target.getAbsolutePath() + ".bak");
        if (backup.exists()) backup.delete();
        if (target.exists() && !target.renameTo(backup))
            throw new IllegalStateException("Alte Datei nicht sicherbar");
        boolean activated = part.renameTo(target);
        if (!activated) {
            if (backup.exists()) backup.renameTo(target);
            throw new IllegalStateException("Neue Datei nicht aktivierbar");
        }
        if (backup.exists()) backup.delete();
    }

    /** Returns Remote.none() only when the server answered "no such file" (4xx); network problems throw. */
    private Remote probe(String url) throws java.io.IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "PoloLauncher/" + BuildConfig.VERSION_NAME);
            int code = c.getResponseCode();
            if (code >= 500 || code == 408 || code == 429) throw new IllegalStateException("HTTP " + code);
            if (code < 200 || code >= 300) return Remote.none();
            return new Remote(true, c.getContentLengthLong(), c.getLastModified());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted())
            throw new TrafficPbfScanner.Cancelled();
    }

    private Notification notification(String title, String text, int max, int value, boolean indeterminate) {
        PendingIntent open = PendingIntent.getActivity(this, 4213,
                new Intent(this, OfflineMapsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title).setContentText(text)
                .setOnlyAlertOnce(true).setOngoing(running).setContentIntent(open);
        if (max > 0 || indeterminate) b.setProgress(max, value, indeterminate);
        return b.build();
    }

    private void updateNotification(String title, String text, int max, int value, boolean indeterminate) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                notification(title, text, max, value, indeterminate));
    }

    private void startHeartbeat() {
        stopHeartbeat();
        GermanyOfflineStore.touch(this);
        heartbeatTask = heartbeat.scheduleAtFixedRate(() -> {
            if (running && !cancelled) GermanyOfflineStore.touch(GermanyOfflineService.this);
        }, 20L, 20L, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) task.cancel(false);
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // The foreground service is intentionally independent from the launcher task/menu.
        DiagLog.add("Deutschland Offline: Launcher-Task verlassen – Hintergrundaufbau bleibt aktiv");
        super.onTaskRemoved(rootIntent);
    }

    private void acquireWakeLock() {
        try {
            PowerManager power = getSystemService(PowerManager.class);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "PoloLauncher:GermanyOffline");
                wakeLock.setReferenceCounted(false);
                // Complete Germany can take hours on a low-power head unit.
                wakeLock.acquire(12L * 60L * 60L * 1000L);
            }
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Throwable ignored) {}
        wakeLock = null;
    }

    static boolean isRunningInProcess() { return processRunning; }

    @Override public void onDestroy() {
        serviceDestroying = true;
        processRunning = false;
        // A system/ACC teardown is NOT a user cancellation. Keep RUN_PENDING so the next service
        // instance can continue automatically. Explicit ACTION_CANCEL sets userCancelled first.
        cancelled = true;
        stopHeartbeat();
        heartbeat.shutdownNow();
        worker.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class Remote {
        final boolean available;
        final long size, modified;
        Remote(boolean available, long size, long modified) {
            this.available = available; this.size = size; this.modified = modified;
        }
        static Remote none() { return new Remote(false, -1L, -1L); }
    }
}
