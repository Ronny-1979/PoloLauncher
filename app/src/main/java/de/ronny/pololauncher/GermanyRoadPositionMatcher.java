package de.ronny.pololauncher;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Road matcher for the Germany package.
 *
 * Road lines come from the Shortbread map tiles already on the device (every road, see
 * GermanyRoadTiles); the compact traffic database is only the fallback. A RoadSnapSelector keeps
 * the car on the road it was on. On every uncertain result the matcher returns raw GNSS.
 *
 * Matching only changes the camera position. Raw GNSS remains authoritative for speed limits,
 * cameras, settlement detection and course tracking.
 */
final class GermanyRoadPositionMatcher implements AutoCloseable {
    interface Listener { void onPosition(Location source, Location display, boolean snapped); }

    private static final long FALLBACK_MS = 350L;
    private static final float MAX_ACCURACY_METRES = 45f;
    private static final double MAX_DIRECTION_ERROR = 55d;

    private final GermanyTrafficReader traffic;
    private final GermanyRoadTiles tiles;
    private final WeakReference<View> ui;
    // Strong reference on purpose: MainActivity passes an inline lambda that nothing else holds.
    // A WeakReference lets the first GC drop it and the camera silently stops following.
    // The matcher is owned by the Activity and close()d in onDestroy, so there is no leak.
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "germany-road-position-reader");
        t.setDaemon(true);
        return t;
    });
    private final PositionResultGate resultGate = new PositionResultGate();
    private final RoadSnapSelector selector = new RoadSnapSelector();
    private static final int MAX_CANDIDATES = 12;

    private volatile boolean closed;
    private boolean inFlight;
    private Location pending;
    private Location latest;
    private long pendingRequest;
    private boolean fallbackScheduled;
    private final Runnable fallback = this::deliverFallback;
    private Location lastSnapped;
    private long lastSnappedAt;

    GermanyRoadPositionMatcher(Context context, View ui, Listener listener) {
        this.traffic = GermanyTrafficReader.open(context);
        this.tiles = GermanyRoadTiles.open(context);
        this.ui = new WeakReference<>(ui);
        this.listener = listener;
    }

    boolean available() { return tiles != null || traffic != null; }

    private static final long WARM_UP_DELAY_MS = 20_000L;

    /**
     * Reads the tile headers on the worker, a while after start-up so it does not compete with the
     * map's own first load for the storage. Without it the first fix pays the same cost.
     */
    void warmUp() {
        GermanyRoadTiles t = tiles;
        View target = ui.get();
        if (t == null || target == null) return;
        target.postDelayed(() -> {
            if (closed) return;
            try { worker.execute(t::warmUp); }
            catch (RejectedExecutionException ignored) {}
        }, WARM_UP_DELAY_MS);
    }

    String tileRegion() { return tiles == null ? "keine Kacheln" : tiles.activeRegion(); }

    void accept(Location location) {
        if (closed || location == null) return;
        if (!available()) {
            Listener callback = listener;
            if (callback != null) callback.onPosition(new Location(location), new Location(location), false);
            return;
        }
        pending = new Location(location);
        latest = new Location(location);
        pendingRequest = resultGate.request();
        View target = ui.get();
        // Exactly like the old Mapsforge matcher: never let a slow road lookup starve camera GPS.
        if (!fallbackScheduled && target != null) {
            fallbackScheduled = true;
            target.postDelayed(fallback, FALLBACK_MS);
        }
        if (!inFlight) processLatest();
    }

    private void deliverFallback() {
        fallbackScheduled = false;
        Listener callback = listener;
        if (!closed && latest != null && resultGate.fallback() && callback != null)
            callback.onPosition(new Location(latest), new Location(latest), false);
    }

    private void processLatest() {
        if (closed || inFlight || pending == null) return;
        Location source = pending;
        long request = pendingRequest;
        pending = null;
        inFlight = true;
        try {
            worker.execute(() -> {
                Match computed;
                try { computed = find(source); }
                catch (Throwable ignored) { computed = raw(source); }
                final Match match = computed;
                View target = ui.get();
                if (target == null) { inFlight = false; return; }
                target.post(() -> {
                    inFlight = false;
                    if (closed) return;
                    Listener callback = listener;
                    if (callback != null && resultGate.complete(request)) {
                        target.removeCallbacks(fallback);
                        fallbackScheduled = false;
                        callback.onPosition(new Location(source), new Location(match.location), match.snapped);
                    }
                    processLatest();
                });
            });
        } catch (RejectedExecutionException ignored) {
            inFlight = false;
        }
    }

    private Match find(Location source) {
        if (!source.hasAccuracy() || !Float.isFinite(source.getAccuracy())
                || source.getAccuracy() > MAX_ACCURACY_METRES) return biased(source);

        float accuracy = Math.max(0f, source.getAccuracy());
        // Restore the proven v0.31.49 Mapsforge snap envelope. The stricter Germany values
        // (12..26 m and only 14 m without a reliable bearing) let normal automotive GNSS drift
        // visibly beside the road. v0.31.49 allowed 18..35 m and used heading as a ranking/filter,
        // not as a reason to collapse the search radius.
        double maxDistance = maximumSnapDistance(accuracy);

        // All roads of the map tile under the car; if the tile is missing or unreadable, the
        // traffic database (speed-tagged roads only). The selector then keeps the car on the road
        // it was on (and its neighbours) instead of flipping to whichever is nearest per fix.
        List<RoadSnapSelector.Candidate> candidates = java.util.Collections.emptyList();
        if (tiles != null) {
            candidates = RoadSnapCandidates.best(RoadSnapCandidates.fromFlat(
                    tiles.segmentsNear(source.getLatitude(), source.getLongitude(), maxDistance + 10d),
                    source, maxDistance, MAX_DIRECTION_ERROR, true), MAX_CANDIDATES);
        }
        if (candidates.isEmpty() && traffic != null)
            candidates = traffic.findRoadCandidates(source, maxDistance, MAX_DIRECTION_ERROR, MAX_CANDIDATES);
        RoadSnapSelector.Candidate road = selector.choose(candidates);
        if (road == null || road.distanceMetres > maxDistance) return biased(source);

        Location snapped = new Location(source);
        snapped.setLatitude(road.lat);
        snapped.setLongitude(road.lon);

        long now = SystemClock.elapsedRealtime();
        if (lastSnapped != null && now - lastSnappedAt > 6_000L) { lastSnapped = null; selector.reset(); }
        if (lastSnapped != null) {
            float seconds = Math.max(0.2f, (now - lastSnappedAt) / 1000f);
            float speed = source.hasSpeed() ? Math.max(0f, source.getSpeed()) : 0f;
            float allowedTravel = Math.max(90f, speed * seconds * 3f + 40f);
            if (lastSnapped.distanceTo(snapped) > allowedTravel) { biasAt = 0L; selector.reset(); return biased(source); }
        }
        lastSnapped = new Location(snapped);
        lastSnappedAt = now;
        biasLat = snapped.getLatitude() - source.getLatitude();
        biasLon = snapped.getLongitude() - source.getLongitude();
        biasAt = now;
        if (road.fromTiles) MapFollowDiagnostics.matcherTileRoad();
        return new Match(snapped, true);
    }


    static double maximumSnapDistance(float accuracyMetres) {
        float accuracy = Math.max(0f, accuracyMetres);
        return Math.max(18d, Math.min(35d, accuracy * 1.5d + 8d));
    }

    // GPS error is strongly correlated in time: if the last fix had to be moved N metres sideways
    // onto the road, the next ones are usually off by about the same amount. When a fix cannot be
    // snapped (road without speed tag, junction), carry that offset over and let it fade out.
    private static final long BIAS_HOLD_MS = 8_000L;
    private double biasLat, biasLon;
    private long biasAt;

    private Match biased(Location source) {
        long age = SystemClock.elapsedRealtime() - biasAt;
        if (biasAt == 0L || age < 0L || age > BIAS_HOLD_MS) return raw(source);
        double factor = 1d - (double) age / BIAS_HOLD_MS;
        Location shifted = new Location(source);
        shifted.setLatitude(source.getLatitude() + biasLat * factor);
        shifted.setLongitude(source.getLongitude() + biasLon * factor);
        return new Match(shifted, false);
    }

    private static Match raw(Location source) {
        return new Match(new Location(source), false);
    }

    void cancelPending() {
        resultGate.cancel();
        pending = null;
        latest = null;
        View target = ui.get();
        if (target != null) target.removeCallbacks(fallback);
        fallbackScheduled = false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        cancelPending();
        // Closing the databases can wait for a running tile read (cold flash after standby). That
        // must never happen on the UI thread, so the worker closes them after its current job.
        try { worker.execute(this::closeResources); }
        catch (RejectedExecutionException e) { closeResources(); }
        worker.shutdown();
    }

    private void closeResources() {
        if (traffic != null) traffic.close();
        if (tiles != null) tiles.close();
    }

    private static final class Match {
        final Location location;
        final boolean snapped;
        Match(Location location, boolean snapped) {
            this.location = location;
            this.snapped = snapped;
        }
    }
}
