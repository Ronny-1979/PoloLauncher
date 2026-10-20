package de.ronny.pololauncher;

import android.content.Context;
import android.location.Location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Regional reader for the hidden per-extract traffic databases of the Germany package.
 *
 * Only traffic indexes near the current GPS position stay open. At a state border, nearby
 * neighbour extracts are preloaded as well, so speed limits and cameras have no hand-over gap.
 * The map MBTiles remain on disk and are independent of this reader.
 */
final class GermanyTrafficReader implements AutoCloseable {
    private static final double PRELOAD_METRES = 20_000d;
    private static final double KEEP_METRES = 25_000d;
    private static final double RESCAN_AFTER_METRES = 5_000d;

    private static final class Slot {
        final String region;
        TrafficDataStore.Reader reader;
        TrafficDataStore.Bounds bounds;
        Slot(String region) { this.region = region; }
    }

    private final Context context;
    private final ArrayList<Slot> slots = new ArrayList<>();
    private double lastScanLat = Double.NaN;
    private double lastScanLon = Double.NaN;
    private String lastActiveSignature = "";
    private boolean closed;

    private GermanyTrafficReader(Context context) {
        this.context = context.getApplicationContext();
        for (String region : GermanyOfflineStore.REGION_KEYS) {
            if (TrafficDataManager.status(context, region).ready) slots.add(new Slot(region));
        }
    }

    static GermanyTrafficReader open(Context context) {
        GermanyTrafficReader result = new GermanyTrafficReader(context);
        if (result.slots.isEmpty()) return null;
        return result;
    }

    synchronized int readyRegionCount() { return slots.size(); }

    synchronized int regionCount() {
        int count = 0;
        for (Slot slot : slots) if (slot.reader != null) count++;
        return count;
    }

    synchronized String activeRegionSummary() {
        ArrayList<String> names = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.reader != null) names.add(GermanyOfflineStore.regionName(slot.region));
        }
        return names.isEmpty() ? "keine" : String.join(" + ", names);
    }

    private void ensureRegions(double lat, double lon) {
        synchronized (this) {
            if (closed) return;
            if (Double.isFinite(lastScanLat)) {
                float[] moved = new float[1];
                Location.distanceBetween(lastScanLat, lastScanLon, lat, lon, moved);
                if (moved[0] < RESCAN_AFTER_METRES) return;
            }

            for (Slot slot : slots) {
                if (slot.reader != null) {
                    boolean keep = false;
                    try { keep = slot.reader.hasTrafficNear(lat, lon, KEEP_METRES); }
                    catch (Throwable ignored) {}
                    if (!keep) closeSlot(slot);
                }
            }

            for (Slot slot : slots) {
                if (slot.reader != null) continue;
                if (slot.bounds != null && !slot.bounds.mightContain(lat, lon, PRELOAD_METRES)) continue;
                TrafficDataStore.Reader candidate = TrafficDataStore.open(context, slot.region);
                if (candidate == null) continue;
                slot.bounds = candidate.bounds();
                boolean near = false;
                try { near = candidate.hasTrafficNear(lat, lon, PRELOAD_METRES); }
                catch (Throwable ignored) {}
                if (near) slot.reader = candidate;
                else try { candidate.close(); } catch (Throwable ignored) {}
            }

            lastScanLat = lat;
            lastScanLon = lon;
            logActiveRegionsIfChanged();
        }
    }

    private void logActiveRegionsIfChanged() {
        StringBuilder signature = new StringBuilder();
        for (Slot slot : slots) {
            if (slot.reader == null) continue;
            if (signature.length() > 0) signature.append('|');
            signature.append(slot.region);
        }
        String next = signature.toString();
        if (next.equals(lastActiveSignature)) return;
        lastActiveSignature = next;
        DiagLog.add("Deutschland Verkehr aktiv: " + activeRegionSummary()
                + " · " + regionCount() + "/" + readyRegionCount() + " Indizes geöffnet");
    }

    private static void closeSlot(Slot slot) {
        if (slot.reader == null) return;
        try { slot.reader.close(); } catch (Throwable ignored) {}
        slot.reader = null;
    }

    TrafficDataStore.SpeedMatch findSpeed(Location source, double maxDistance, double maxDirectionError) {
        ensureRegions(source.getLatitude(), source.getLongitude());
        TrafficDataStore.SpeedMatch best = null;
        double nearest = Double.POSITIVE_INFINITY;
        synchronized (this) {
            for (Slot slot : slots) {
                TrafficDataStore.Reader r = slot.reader;
                if (r == null) continue;
                TrafficDataStore.SpeedMatch m = r.findSpeed(source, maxDistance, maxDirectionError);
                if (Double.isFinite(m.nearestRoadMetres)) nearest = Math.min(nearest, m.nearestRoadMetres);
                if (!m.matchedRoad) continue;
                if (best == null || m.nearestRoadMetres < best.nearestRoadMetres) best = m;
            }
        }
        if (best != null) return best;
        return new TrafficDataStore.SpeedMatch(SpeedLimitValue.UNKNOWN,
                Double.isFinite(nearest) ? nearest : Double.NaN, "", "", false);
    }

    List<RoadSnapSelector.Candidate> findRoadCandidates(Location source, double maxDistance,
                                                        double maxDirectionError, int limit) {
        ensureRegions(source.getLatitude(), source.getLongitude());
        ArrayList<RoadSnapSelector.Candidate> all = new ArrayList<>();
        synchronized (this) {
            for (Slot slot : slots) {
                TrafficDataStore.Reader r = slot.reader;
                if (r == null) continue;
                all.addAll(r.findRoadCandidates(source, maxDistance, maxDirectionError, limit));
            }
        }
        return RoadSnapCandidates.best(all, limit);
    }

    List<TrafficDataStore.CameraPoint> camerasNear(double lat, double lon, double metres) {
        ensureRegions(lat, lon);
        ArrayList<TrafficDataStore.CameraPoint> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        synchronized (this) {
            for (Slot slot : slots) {
                TrafficDataStore.Reader r = slot.reader;
                if (r == null) continue;
                for (TrafficDataStore.CameraPoint p : r.camerasNear(lat, lon, metres)) {
                    String key = p.id != 0L ? "id:" + p.id
                            : "xy:" + Math.round(p.lat * 1e7) + ':' + Math.round(p.lon * 1e7);
                    if (seen.add(key)) out.add(p);
                }
            }
        }
        return out;
    }

    double[] roadAxesNear(double lat, double lon, double metres) {
        ArrayList<Double> axes = new ArrayList<>();
        synchronized (this) {
            for (Slot slot : slots) {
                TrafficDataStore.Reader r = slot.reader;
                if (r == null) continue;
                for (double axis : r.roadAxesNear(lat, lon, metres)) {
                    boolean duplicate = false;
                    for (double existing : axes) {
                        if (SpeedCameraDecision.roadAxisDifference(existing, axis) < 8d) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) axes.add(axis);
                    if (axes.size() >= 8) break;
                }
                if (axes.size() >= 8) break;
            }
        }
        double[] result = new double[axes.size()];
        for (int i = 0; i < axes.size(); i++) result[i] = axes.get(i);
        return result;
    }

    @Override public synchronized void close() {
        closed = true;
        for (Slot slot : slots) closeSlot(slot);
        lastActiveSignature = "";
    }
}
