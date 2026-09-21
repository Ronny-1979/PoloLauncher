package de.ronny.pololauncher;

import android.location.Location;
import android.os.SystemClock;
import android.view.View;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Optional conservative map matcher. It only changes the displayed camera position;
 * raw GNSS fixes remain authoritative for area detection and course tracking.
 */
final class RoadPositionMatcher {
    interface Listener { void onPosition(Location source, Location display, boolean snapped); }

    private static final byte LOOKUP_ZOOM = 15;
    private static final float MAX_ACCURACY_METRES = 45f;
    private static final double MAX_DIRECTION_ERROR = 55d;

    private final MapFile file;
    private final WeakReference<View> ui;
    private final WeakReference<Listener> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "road-position-reader");
        t.setDaemon(true);
        return t;
    });
    private boolean inFlight;
    private Location pending;
    private Location latest;
    private long pendingRequest;
    private final PositionResultGate resultGate = new PositionResultGate();
    private boolean fallbackScheduled;
    private final Runnable fallback = this::deliverFallback;
    private void deliverFallback() {
        fallbackScheduled = false;
        Listener callback = listener.get();
        if (!closed && latest != null && resultGate.fallback() && callback != null)
            callback.onPosition(new Location(latest), new Location(latest), false);
    }
    private volatile boolean closed;
    private Location lastSnapped;
    private long lastSnappedAt;

    RoadPositionMatcher(File map, View ui, Listener listener) {
        file = new MapFile(map);
        this.ui = new WeakReference<>(ui);
        this.listener = new WeakReference<>(listener);
    }

    void accept(Location location) {
        if (closed || location == null) return;
        pending = new Location(location);
        latest = new Location(location);
        pendingRequest = resultGate.request();
        // Do not restart this deadline for newer fixes: a stuck reader must not
        // starve the camera when GPS arrives more frequently than the timeout.
        View target = ui.get();
        if (!fallbackScheduled && target != null) {
            fallbackScheduled = true;
            target.postDelayed(fallback, 350L);
        }
        if (!inFlight) processLatest();
    }

    private void processLatest() {
        if (closed || inFlight || pending == null) return;
        Location source = pending;
        long request = pendingRequest;
        pending = null;
        inFlight = true;
        try { worker.execute(() -> {
            Match match;
            try { match = find(source); }
            catch (Throwable e) { match = new Match(new Location(source), false); }
            Match result = match;
            View target = ui.get();
            if (target == null) return;
            target.post(() -> {
                inFlight = false;
                if (closed) return;
                Listener callback = listener.get();
                if (callback == null) return;
                if (resultGate.complete(request)) {
                    target.removeCallbacks(fallback);
                    fallbackScheduled = false;
                    callback.onPosition(new Location(source), new Location(result.location), result.snapped);
                }
                processLatest();
            });
        }); } catch (RejectedExecutionException ignored) { inFlight = false; }
    }

    private Match find(Location source) {
        if (!source.hasAccuracy() || !Float.isFinite(source.getAccuracy())
                || source.getAccuracy() > MAX_ACCURACY_METRES)
            return raw(source);
        LatLong point = new LatLong(source.getLatitude(), source.getLongitude());
        if (!file.boundingBox().contains(point)) return raw(source);

        long now = SystemClock.elapsedRealtime();
        if (lastSnapped != null && now - lastSnappedAt > 6_000L) lastSnapped = null;
        MapReadResult data = file.readMapData(tile(point));
        if (data == null || data.ways == null) return raw(source);

        float accuracy = Math.max(0f, source.getAccuracy());
        double maximumDistance = Math.max(18d, Math.min(35d, accuracy * 1.5d + 8d));
        boolean directionKnown = source.hasBearing() && source.hasSpeed() && source.getSpeed() >= 2f
                && (!source.hasBearingAccuracy() || source.getBearingAccuracyDegrees() <= 40f);
        Candidate best = null;
        for (Way way : data.ways) {
            if (way == null || way.tags == null || way.latLongs == null
                    || !isDrivable(tag(way, "highway"))) continue;
            for (LatLong[] line : way.latLongs) {
                if (line == null || line.length < 2) continue;
                for (int i = 1; i < line.length; i++) {
                    LatLong a = line[i - 1];
                    LatLong b = line[i];
                    RoadGeometry.Projection projection = RoadGeometry.project(
                            a.latitude, a.longitude, b.latitude, b.longitude,
                            point.latitude, point.longitude);
                    if (projection.distanceMetres > maximumDistance) continue;
                    double directionError = directionKnown
                            ? RoadGeometry.axisDifference(source.getBearing(), projection.roadBearing) : 0d;
                    if (directionKnown && directionError > MAX_DIRECTION_ERROR) continue;
                    // Direction has enough weight to avoid turning onto a crossing road,
                    // while distance still decides between parallel carriageways.
                    double score = projection.distanceMetres + directionError * 0.30d;
                    if (best == null || score < best.score)
                        best = new Candidate(projection, score);
                }
            }
        }
        if (best == null) return raw(source);

        Location snapped = new Location(source);
        snapped.setLatitude(best.projection.latitude);
        snapped.setLongitude(best.projection.longitude);
        if (lastSnapped != null) {
            float seconds = Math.max(0.2f, (now - lastSnappedAt) / 1000f);
            float speed = source.hasSpeed() ? Math.max(0f, source.getSpeed()) : 0f;
            float allowedTravel = Math.max(90f, speed * seconds * 3f + 40f);
            if (lastSnapped.distanceTo(snapped) > allowedTravel) return raw(source);
        }
        lastSnapped = new Location(snapped);
        lastSnappedAt = now;
        return new Match(snapped, true);
    }

    private static Tile tile(LatLong point) {
        long count = 1L << LOOKUP_ZOOM;
        long maximum = count - 1L;
        long x = Math.max(0L, Math.min(maximum,
                (long) Math.floor((point.longitude + 180d) / 360d * count)));
        double latitude = Math.max(-85d, Math.min(85d, point.latitude));
        double radians = Math.toRadians(latitude);
        long y = Math.max(0L, Math.min(maximum, (long) Math.floor((1d
                - Math.log(Math.tan(radians) + 1d / Math.cos(radians)) / Math.PI) / 2d * count)));
        return new Tile(Math.toIntExact(x), Math.toIntExact(y), LOOKUP_ZOOM, 256);
    }

    private static String tag(Way way, String key) {
        for (Tag tag : way.tags) if (key.equals(tag.key)) return tag.value;
        return "";
    }

    private static boolean isDrivable(String type) {
        return "motorway".equals(type) || "motorway_link".equals(type)
                || "trunk".equals(type) || "trunk_link".equals(type)
                || "primary".equals(type) || "primary_link".equals(type)
                || "secondary".equals(type) || "secondary_link".equals(type)
                || "tertiary".equals(type) || "tertiary_link".equals(type)
                || "residential".equals(type) || "living_street".equals(type)
                || "unclassified".equals(type) || "service".equals(type)
                || "road".equals(type);
    }

    private static Match raw(Location source) { return new Match(new Location(source), false); }

    void cancelPending() {
        resultGate.cancel();
        pending = null;
        latest = null;
        View target = ui.get();
        if (target != null) target.removeCallbacks(fallback);
        fallbackScheduled = false;
    }

    void close() {
        if (closed) return;
        closed = true;
        cancelPending();
        worker.shutdownNow();
        Thread closer = new Thread(() -> {
            try { file.close(); } catch (Throwable ignored) {}
        }, "road-position-close");
        closer.setDaemon(true);
        closer.start();
    }

    private static final class Candidate {
        final RoadGeometry.Projection projection;
        final double score;
        Candidate(RoadGeometry.Projection projection, double score) {
            this.projection = projection;
            this.score = score;
        }
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
