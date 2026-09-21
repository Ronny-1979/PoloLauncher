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

/** Conservative, offline settlement approximation from rendered Mapsforge vector features.
 * Not a legal town-limit detector. A separate reader keeps disk IO off the UI/render thread.
 */
final class SettlementZoomAnalyzer {
    enum Area { UNKNOWN, SETTLEMENT, OUTSIDE }
    interface Listener { void onAreaChanged(Area area); }

    private final MapFile file;
    private final WeakReference<View> ui;
    private final WeakReference<Listener> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "settlement-map-reader");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed;
    private boolean inFlight;
    private long lastSampleAt;
    private Location lastSample;
    private Area pending = Area.UNKNOWN;
    private int confirmations;
    private Area area = Area.UNKNOWN;

    SettlementZoomAnalyzer(File map, View ui, Listener listener) {
        this.file = new MapFile(map);
        this.ui = new WeakReference<>(ui);
        this.listener = new WeakReference<>(listener);
    }

    Area current() { return area; }

    void accept(Location location) {
        if (closed || inFlight || location == null || !location.hasAccuracy()
                || location.getAccuracy() > 40f) return;
        LatLong center = new LatLong(location.getLatitude(), location.getLongitude());
        if (!file.boundingBox().contains(center)) {
            if (area != Area.UNKNOWN) {
                area = Area.UNKNOWN;
                Listener callback = listener.get();
                if (callback != null) callback.onAreaChanged(area);
            }
            pending = Area.UNKNOWN;
            confirmations = 0;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastSampleAt < 8_000L) return;
        // On first use, confirm a parked position after 30 seconds as well.
        if (lastSample != null && location.distanceTo(lastSample) < 55f
                && (area != Area.UNKNOWN || now - lastSampleAt < 30_000L)) return;
        lastSampleAt = now;
        lastSample = new Location(location);
        inFlight = true;
        try { worker.execute(() -> {
            Area found;
            try { found = inspect(center); }
            catch (Throwable e) { found = Area.UNKNOWN; }
            final Area result = found;
            View target = ui.get();
            if (target == null) return;
            target.post(() -> {
                inFlight = false;
                if (closed) return;
                if (result == Area.UNKNOWN) {
                    pending = Area.UNKNOWN;
                    confirmations = 0;
                    return;
                }
                confirmations = result == pending ? confirmations + 1 : 1;
                pending = result;
                if (confirmations >= 2 && result != area) {
                    area = result;
                    Listener callback = listener.get();
                    if (callback != null) callback.onAreaChanged(result);
                }
            });
        }); } catch (RejectedExecutionException ignored) { inFlight = false; }
    }

    private Area inspect(LatLong point) {
        // At zoom 14 the tile covers roughly 1–3 km and includes nearby ways.
        final byte zoom = 14;
        long max = (1L << zoom) - 1;
        long x = Math.max(0, Math.min(max, (long) Math.floor((point.longitude + 180) / 360 * (1L << zoom))));
        double latitude = Math.max(-85, Math.min(85, point.latitude));
        double radians = Math.toRadians(latitude);
        long y = Math.max(0, Math.min(max, (long) Math.floor((1 - Math.log(Math.tan(radians)
                + 1 / Math.cos(radians)) / Math.PI) / 2 * (1L << zoom))));
        // Tile expects int coordinates; at zoom 14 the clamped range is 0..16383.
        MapReadResult result = file.readMapData(new Tile(Math.toIntExact(x), Math.toIntExact(y), zoom, 256));
        if (result == null || result.ways == null) return Area.UNKNOWN;
        boolean inSettlement = false;
        boolean inCountryside = false;
        double nearestSettlementEdge = Double.POSITIVE_INFINITY;
        double closestRoad = Double.POSITIVE_INFINITY;
        boolean fastestRoad = false;
        for (Way way : result.ways) {
            if (way == null || way.tags == null || way.latLongs == null) continue;
            String landuse = tag(way, "landuse");
            String natural = tag(way, "natural");
            String highway = tag(way, "highway");
            if ("residential".equals(landuse) || "commercial".equals(landuse) || "retail".equals(landuse)) {
                for (LatLong[] ring : way.latLongs) {
                    if (ring == null || ring.length < 3) continue;
                    if (contains(ring, point)) inSettlement = true;
                    nearestSettlementEdge = Math.min(nearestSettlementEdge, nearest(ring, point));
                }
            }
            if ("farmland".equals(landuse) || "meadow".equals(landuse)
                    || "forest".equals(landuse) || "orchard".equals(landuse)
                    || "wood".equals(natural) || "heath".equals(natural)) {
                for (LatLong[] ring : way.latLongs) {
                    if (ring != null && ring.length >= 3 && contains(ring, point)) inCountryside = true;
                }
            }
            if (isRoad(highway)) {
                for (LatLong[] line : way.latLongs) {
                    if (line == null || line.length < 2) continue;
                    double metres = nearest(line, point);
                    if (metres < closestRoad) {
                        closestRoad = metres;
                        fastestRoad = "motorway".equals(highway) || "motorway_link".equals(highway)
                                || "trunk".equals(highway) || "trunk_link".equals(highway);
                    }
                }
            }
        }
        // We need a road match, not just an address or a polygon edge.
        if (closestRoad > 65) return Area.UNKNOWN;
        if (fastestRoad && closestRoad < 35) return Area.OUTSIDE;
        if (inSettlement) return Area.SETTLEMENT;
        // Keep prior zoom at settlement edges and unmapped gaps such as parks.
        if (nearestSettlementEdge < 140) return Area.UNKNOWN;
        return inCountryside ? Area.OUTSIDE : Area.UNKNOWN;
    }

    private static String tag(Way way, String key) {
        for (Tag tag : way.tags) if (key.equals(tag.key)) return tag.value;
        return "";
    }

    private static boolean isRoad(String type) {
        return "residential".equals(type) || "living_street".equals(type)
                || "tertiary".equals(type) || "secondary".equals(type)
                || "primary".equals(type) || "unclassified".equals(type)
                || "trunk".equals(type) || "trunk_link".equals(type)
                || "motorway".equals(type) || "motorway_link".equals(type);
    }

    private static boolean contains(LatLong[] polygon, LatLong p) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            LatLong a = polygon[i], b = polygon[j];
            if ((a.latitude > p.latitude) != (b.latitude > p.latitude)
                    && p.longitude < (b.longitude - a.longitude) * (p.latitude - a.latitude)
                    / (b.latitude - a.latitude) + a.longitude) inside = !inside;
        }
        return inside;
    }

    private static double nearest(LatLong[] line, LatLong p) {
        double smallest = Double.POSITIVE_INFINITY;
        double scaleX = 111_320 * Math.cos(Math.toRadians(p.latitude));
        for (int i = 1; i < line.length; i++) {
            double ax = (line[i - 1].longitude - p.longitude) * scaleX;
            double ay = (line[i - 1].latitude - p.latitude) * 111_320;
            double bx = (line[i].longitude - p.longitude) * scaleX;
            double by = (line[i].latitude - p.latitude) * 111_320;
            double dx = bx - ax, dy = by - ay;
            double t = dx * dx + dy * dy == 0 ? 0 : Math.max(0, Math.min(1,
                    -(ax * dx + ay * dy) / (dx * dx + dy * dy)));
            smallest = Math.min(smallest, Math.hypot(ax + t * dx, ay + t * dy));
        }
        return smallest;
    }

    void close() {
        if (closed) return;
        closed = true;
        worker.shutdownNow();
        Thread closer = new Thread(() -> {
            try { file.close(); } catch (Throwable ignored) {}
        }, "settlement-map-close");
        closer.setDaemon(true);
        closer.start();
    }
}
