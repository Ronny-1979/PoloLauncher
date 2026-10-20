package de.ronny.pololauncher;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, user-triggered HEAD-only update check for the unified Germany package. */
final class GermanyOfflineUpdateChecker {
    interface Callback { void onChecked(Activity owner, Result result); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "germany-offline-update-check");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private GermanyOfflineUpdateChecker() {}

    static boolean running() { return RUNNING.get(); }

    static void checkAsync(Activity owner, Callback callback) {
        if (owner == null || callback == null) return;
        if (!RUNNING.compareAndSet(false, true)) {
            callback.onChecked(owner, Result.busy());
            return;
        }
        Context app = owner.getApplicationContext();
        WeakReference<Activity> ownerRef = new WeakReference<>(owner);
        WORKER.execute(() -> {
            Result result;
            try { result = check(app); }
            catch (Throwable error) {
                result = Result.failure("Updateprüfung: " + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : " – " + error.getMessage()));
            } finally {
                RUNNING.set(false);
            }
            GermanyOfflineStore.saveUpdateCheck(app, result);
            final Result finished = result;
            MAIN.post(() -> {
                Activity activity = ownerRef.get();
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                    callback.onChecked(activity, finished);
            });
        });
    }

    private static Result check(Context context) {
        if (!GermanyOfflineStore.ready(context))
            return Result.failure("Deutschland-Paket ist noch nicht vollständig aufgebaut");

        List<String> mapUpdates = new ArrayList<>();
        List<String> trafficUpdates = new ArrayList<>();
        int mapChecks = 0;
        int trafficChecks = 0;
        int failed = 0;

        for (int i = 0; i < GermanyOfflineStore.REGION_KEYS.length; i++) {
            String region = GermanyOfflineStore.REGION_KEYS[i];
            String name = GermanyOfflineStore.REGION_NAMES[i];

            File map = GermanyOfflineStore.mapFile(context, region);
            Remote mapRemote = probe(GermanyOfflineStore.mapUrl(region));
            if (mapRemote.available) {
                mapChecks++;
                long localSize = map != null && map.isFile() ? map.length() : -1L;
                long localModified = map != null && map.isFile() ? map.lastModified() : -1L;
                if (GermanyOfflineUpdatePolicy.updateAvailable(localSize, localModified,
                        mapRemote.size, mapRemote.modified)) mapUpdates.add(name);
            } else failed++;

            TrafficDataManager.Status traffic = TrafficDataManager.status(context, region);
            Remote trafficRemote = probe(TrafficDataManager.url(region));
            if (trafficRemote.available) {
                trafficChecks++;
                if (GermanyOfflineUpdatePolicy.updateAvailable(traffic.sourceSize,
                        traffic.sourceModified, trafficRemote.size, trafficRemote.modified))
                    trafficUpdates.add(name);
            } else failed++;
        }

        return new Result(false, null, System.currentTimeMillis(),
                mapChecks, trafficChecks, failed, mapUpdates, trafficUpdates);
    }

    private static Remote probe(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "PoloLauncher/" + BuildConfig.VERSION_NAME
                    + " (Germany update check)");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return Remote.none();
            return new Remote(true, connection.getContentLengthLong(), connection.getLastModified());
        } catch (Throwable ignored) {
            return Remote.none();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static final class Result {
        final boolean busy;
        final String error;
        final long checkedAt;
        final int mapChecks, trafficChecks, failedChecks;
        final List<String> mapUpdates, trafficUpdates;

        Result(boolean busy, String error, long checkedAt,
               int mapChecks, int trafficChecks, int failedChecks,
               List<String> mapUpdates, List<String> trafficUpdates) {
            this.busy = busy;
            this.error = error;
            this.checkedAt = checkedAt;
            this.mapChecks = mapChecks;
            this.trafficChecks = trafficChecks;
            this.failedChecks = failedChecks;
            this.mapUpdates = mapUpdates == null ? List.of() : List.copyOf(mapUpdates);
            this.trafficUpdates = trafficUpdates == null ? List.of() : List.copyOf(trafficUpdates);
        }

        static Result busy() {
            return new Result(true, "Updateprüfung läuft bereits", 0L, 0, 0, 0, List.of(), List.of());
        }

        static Result failure(String error) {
            return new Result(false, error, System.currentTimeMillis(), 0, 0, 0, List.of(), List.of());
        }

        boolean complete() {
            return error == null && failedChecks == 0
                    && mapChecks == GermanyOfflineStore.REGION_KEYS.length
                    && trafficChecks == GermanyOfflineStore.REGION_KEYS.length;
        }

        boolean hasUpdates() { return !mapUpdates.isEmpty() || !trafficUpdates.isEmpty(); }

        String summary() {
            if (error != null) return error;
            return "Karten: " + mapUpdates.size() + " Regionen neuer · Verkehr: "
                    + trafficUpdates.size() + " Regionen neuer";
        }

        String detail() {
            StringBuilder out = new StringBuilder(summary());
            if (!mapUpdates.isEmpty()) out.append("\n\nKarten: ").append(String.join(", ", mapUpdates));
            if (!trafficUpdates.isEmpty()) out.append("\n\nVerkehr: ").append(String.join(", ", trafficUpdates));
            if (failedChecks > 0) out.append("\n\nNicht erreichbar: ").append(failedChecks).append(" Prüfungen");
            return out.toString();
        }
    }

    private static final class Remote {
        final boolean available;
        final long size, modified;
        Remote(boolean available, long size, long modified) {
            this.available = available;
            this.size = size;
            this.modified = modified;
        }
        static Remote none() { return new Remote(false, -1L, -1L); }
    }
}
