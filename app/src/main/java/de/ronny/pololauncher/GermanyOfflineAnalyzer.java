package de.ronny.pololauncher;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Position-side companion of the Germany vector map. All driving data comes from the hidden
 * traffic indexes that were built while installing the same Germany package.
 */
final class GermanyOfflineAnalyzer implements AutoCloseable {
    private static final long MIN_SAMPLE_MS = 800L;
    private static final double MAX_DIRECTION_ERROR = 60d;

    private final Context context;
    private final WeakReference<View> ui;
    private final GermanyTrafficReader traffic;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "germany-offline-analyzer");
        t.setDaemon(true);
        return t;
    });
    private final SpeedLimitGate speedGate = new SpeedLimitGate();
    private final SpeedCameraGate cameraGate = new SpeedCameraGate();
    private final SettlementZoomGate areaGate = new SettlementZoomGate();

    private volatile boolean closed;
    private boolean inFlight;
    private Location pending;
    private long lastSampleAt;
    private Location lastDisplay;
    private Location lastAreaSample;
    private MapArea area;
    private int lastSpeed = SpeedLimitValue.UNKNOWN;
    private int lastCameraDistance = -1;
    private long lastCameraKey;

    private Consumer<Location> locationConsumer;
    private Consumer<MapArea> areaConsumer;
    private Consumer<Integer> speedConsumer;
    private Consumer<Integer> cameraConsumer;

    GermanyOfflineAnalyzer(Context context, View ui) {
        this.context = context.getApplicationContext();
        this.ui = new WeakReference<>(ui);
        this.traffic = GermanyTrafficReader.open(context);
        if (traffic == null) {
            SpeedLimitDiagnostics.capability(false, "Deutschland-Verkehrsdaten");
            DiagLog.add("Deutschland Offline: Verkehrsdaten nicht lesbar");
        } else {
            SpeedLimitDiagnostics.capability(true, "Deutschland-Verkehrsdaten");
            DiagLog.add("Deutschland Offline: " + traffic.readyRegionCount()
                    + " Verkehrsindizes bereit · regionale Aktivierung aktiv");
        }
    }

    Location currentLocation() { return lastDisplay == null ? null : new Location(lastDisplay); }

    void setLocationConsumer(Consumer<Location> value) { locationConsumer = value; }
    void setAreaConsumer(Consumer<MapArea> value) { areaConsumer = value; }
    void setSpeedConsumer(Consumer<Integer> value) { speedConsumer = value; }
    void setCameraConsumer(Consumer<Integer> value) { cameraConsumer = value; }

    void accept(Location location) {
        if (closed || traffic == null || location == null) return;
        if (!location.hasAccuracy() || !Float.isFinite(location.getAccuracy())
                || location.getAccuracy() > 55f) {
            publishSpeed(SpeedLimitValue.UNKNOWN);
            publishCamera(SpeedCameraGate.Result.none());
            return;
        }
        if (inFlight) {
            pending = new Location(location);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastSampleAt < MIN_SAMPLE_MS) return;
        lastSampleAt = now;
        Location source = new Location(location);
        inFlight = true;
        try {
            worker.execute(() -> compute(source));
        } catch (RejectedExecutionException ignored) {
            inFlight = false;
        }
    }

    private void compute(Location source) {
        TrafficDataStore.SpeedMatch speed = null;
        SpeedCameraGate.Result camera = SpeedCameraGate.Result.none();
        try {
            speed = traffic.findSpeed(source, Math.max(16d,
                    Math.min(32d, source.getAccuracy() * 1.3d + 7d)), MAX_DIRECTION_ERROR);
            if (MapPreferences.speedCameraAlerts(context)) camera = findCamera(source);
        } catch (Throwable e) {
            DiagLog.add("Deutschland Offline Analyse: " + e.getClass().getSimpleName());
        }
        final TrafficDataStore.SpeedMatch finalSpeed = speed;
        final SpeedCameraGate.Result finalCamera = camera;
        View target = ui.get();
        if (target == null) { inFlight = false; return; }
        target.post(() -> {
            inFlight = false;
            if (closed) return;
            publishRawPosition(source, finalSpeed);
            if (finalSpeed == null) {
                SpeedLimitDiagnostics.error("Deutschland-Index");
                publishSpeed(SpeedLimitValue.UNKNOWN);
            } else {
                SpeedLimitDiagnostics.lookup(finalSpeed.nearestRoadMetres,
                        Math.max(16d, Math.min(32d, source.getAccuracy() * 1.3d + 7d)),
                        finalSpeed.roadType, finalSpeed.rawSpeedTag,
                        finalSpeed.speed, finalSpeed.matchedRoad);
                publishSpeed(finalSpeed.speed);
            }
            publishCamera(finalCamera);
            Location next = pending;
            pending = null;
            if (next != null) accept(next);
        });
    }

    private void publishRawPosition(Location source, TrafficDataStore.SpeedMatch speed) {
        // v0.31.55 intentionally keeps only speed-tagged roads + cameras in the compact index.
        // Snapping to a speed-only subset would be wrong on roads without explicit maxspeed.
        lastDisplay = new Location(source);
        Consumer<Location> position = locationConsumer;
        if (position != null) position.accept(new Location(lastDisplay));

        MapArea observation = classifyArea(speed);
        long now = SystemClock.elapsedRealtime();
        double moved = lastAreaSample == null ? 0d : source.distanceTo(lastAreaSample);
        lastAreaSample = new Location(source);
        SettlementZoomGate.Signal stable = areaGate.update(areaSignal(observation), false, now, moved,
                MapPreferences.directBoundaryZoom(context));
        MapArea next = areaFromSignal(stable);
        if (next != MapArea.UNKNOWN && next != area) {
            area = next;
            Consumer<MapArea> c = areaConsumer;
            if (c != null) c.accept(next);
            DiagLog.add("Deutschland Ortszoom aus Tempodaten: "
                    + (next == MapArea.SETTLEMENT ? "innerorts" : "außerorts")
                    + (speed == null || speed.roadType.isEmpty() ? "" : " · " + speed.roadType)
                    + (MapPreferences.directBoundaryZoom(context) ? " · direkt" : " · stabil"));
        }
    }

    private static SettlementZoomGate.Signal areaSignal(MapArea value) {
        if (value == MapArea.SETTLEMENT) return SettlementZoomGate.Signal.SETTLEMENT;
        if (value == MapArea.OUTSIDE) return SettlementZoomGate.Signal.OUTSIDE;
        return SettlementZoomGate.Signal.UNKNOWN;
    }

    private static MapArea areaFromSignal(SettlementZoomGate.Signal value) {
        if (value == SettlementZoomGate.Signal.SETTLEMENT) return MapArea.SETTLEMENT;
        if (value == SettlementZoomGate.Signal.OUTSIDE) return MapArea.OUTSIDE;
        return MapArea.UNKNOWN;
    }

    private MapArea classifyArea(TrafficDataStore.SpeedMatch speed) {
        if (speed == null || !speed.matchedRoad) return null;
        if ("residential".equals(speed.roadType) || "living_street".equals(speed.roadType))
            return MapArea.SETTLEMENT;
        if ("motorway".equals(speed.roadType) || "motorway_link".equals(speed.roadType)
                || "trunk".equals(speed.roadType) || "trunk_link".equals(speed.roadType))
            return MapArea.OUTSIDE;
        if (speed.speed != SpeedLimitValue.UNKNOWN && speed.speed != SpeedLimitValue.UNRESTRICTED) {
            if (speed.speed <= 50) return MapArea.SETTLEMENT;
            if (speed.speed >= 70) return MapArea.OUTSIDE;
        }
        return null;
    }

    private void publishSpeed(int observation) {
        int shown = speedGate.update(observation, SystemClock.elapsedRealtime());
        if (shown == lastSpeed) return;
        lastSpeed = shown;
        Consumer<Integer> c = speedConsumer;
        if (c != null) c.accept(shown);
    }

    private SpeedCameraGate.Result findCamera(Location source) {
        if (!source.hasSpeed() || !Float.isFinite(source.getSpeed())
                || source.getSpeed() < SpeedCameraDecision.MIN_MOVING_SPEED_MPS
                || !source.hasBearing() || !Float.isFinite(source.getBearing())
                || (source.hasBearingAccuracy() && source.getBearingAccuracyDegrees() > 45f))
            return SpeedCameraGate.Result.none();

        TrafficDataStore.CameraPoint best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        List<TrafficDataStore.CameraPoint> points = traffic.camerasNear(
                source.getLatitude(), source.getLongitude(), SpeedCameraDecision.MAX_DISTANCE_METRES);
        for (TrafficDataStore.CameraPoint point : points) {
            float[] distance = new float[1];
            Location.distanceBetween(source.getLatitude(), source.getLongitude(),
                    point.lat, point.lon, distance);
            if (distance[0] > SpeedCameraDecision.MAX_DISTANCE_METRES) continue;
            Location camera = new Location("germany-speed-camera");
            camera.setLatitude(point.lat); camera.setLongitude(point.lon);
            float bearing = source.bearingTo(camera);
            double[] axes = traffic.roadAxesNear(point.lat, point.lon, 50d);
            boolean eligible = axes != null && axes.length > 0
                    ? SpeedCameraDecision.eligible(distance[0], bearing,
                        source.getBearing(), source.getSpeed(), axes)
                    : SpeedCameraDecision.eligible(distance[0], bearing,
                        source.getBearing(), source.getSpeed(), 0d, false);
            if (!eligible) continue;
            if (distance[0] < bestDistance) {
                bestDistance = distance[0];
                best = point;
            }
        }
        if (best == null) return SpeedCameraGate.Result.none();
        long key = best.id != 0L ? best.id
                : Double.doubleToLongBits(best.lat) ^ Double.doubleToLongBits(best.lon);
        return new SpeedCameraGate.Result(key, Math.max(0, (int) Math.round(bestDistance)),
                best.id, best.lat, best.lon, "Deutschland-Verkehrsdaten");
    }

    private void publishCamera(SpeedCameraGate.Result observation) {
        SpeedCameraGate.Result shown = cameraGate.update(observation, SystemClock.elapsedRealtime());
        int distance = shown.present() ? shown.distanceMetres : -1;
        if (distance == lastCameraDistance && (!shown.present() || shown.key == lastCameraKey)) return;
        if (shown.present() && shown.key != lastCameraKey) {
            DiagLog.add(String.format(Locale.GERMANY,
                    "Blitzer angezeigt | Quelle=%s | OSM-ID=%d | Entfernung=%d m | Position=%.6f,%.6f | Anzeige=JA",
                    shown.source, shown.osmId, shown.distanceMetres, shown.latitude, shown.longitude));
        } else if (!shown.present() && lastCameraDistance >= 0) {
            DiagLog.add("Blitzer ausgeblendet | kein bestätigter Treffer mehr");
        }
        lastCameraDistance = distance;
        lastCameraKey = shown.present() ? shown.key : 0L;
        Consumer<Integer> c = cameraConsumer;
        if (c != null) c.accept(distance);
    }

    @Override public void close() {
        closed = true;
        pending = null;
        // Never close the traffic DB on the UI thread: close() waits for a running query
        // (log v0.31.93: UI blocked 5 s in onDestroy). The worker closes it after its current job.
        final GermanyTrafficReader toClose = traffic;
        if (toClose != null) {
            try { worker.execute(toClose::close); }
            catch (RuntimeException rejected) { new Thread(toClose::close, "traffic-close").start(); }
        }
        worker.shutdown();
        speedGate.reset();
        cameraGate.reset();
        areaGate.reset();
        lastAreaSample = null;
    }
}
