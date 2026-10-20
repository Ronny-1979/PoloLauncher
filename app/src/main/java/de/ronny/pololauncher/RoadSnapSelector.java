package de.ronny.pololauncher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Chooses the road a vehicle is on from several nearby candidates, using the recent drive as
 * context (the idea behind HMM map matching, reduced to what a head unit can afford).
 *
 * A single fix is ambiguous next to parallel roads and at junctions. Without memory the nearest
 * segment flips from fix to fix and the dot jumps. Here a candidate that continues one of the
 * last few segments (shares an end point) is preferred; leaving that chain needs two consecutive
 * fixes that agree on the same new road, unless nothing connected is in range any more.
 *
 * Android-independent, so it is unit tested on the JVM.
 */
final class RoadSnapSelector {
    /** Extra metres a candidate that does not continue the current road has to win by. */
    static final double SWITCH_PENALTY_METRES = 10d;
    static final int CONFIRM_FIXES = 2;
    private static final double JOIN_METRES = 3d;
    private static final int HISTORY = 4;

    static final class Candidate {
        final double lat1, lon1, lat2, lon2;      // segment end points
        final double lat, lon;                    // projection of the fix onto the segment
        final double distanceMetres;
        final double score;                       // distance + heading penalty, lower is better
        final boolean fromTiles;                  // true: read from the map tiles, not the traffic DB

        Candidate(double lat1, double lon1, double lat2, double lon2, double lat, double lon,
                  double distanceMetres, double score, boolean fromTiles) {
            this.lat1 = lat1; this.lon1 = lon1; this.lat2 = lat2; this.lon2 = lon2;
            this.lat = lat; this.lon = lon; this.distanceMetres = distanceMetres;
            this.score = score; this.fromTiles = fromTiles;
        }

        boolean sameSegment(Candidate o) {
            return near(lat1, lon1, o.lat1, o.lon1) && near(lat2, lon2, o.lat2, o.lon2)
                    || near(lat1, lon1, o.lat2, o.lon2) && near(lat2, lon2, o.lat1, o.lon1);
        }

        boolean touches(Candidate o) {
            return near(lat1, lon1, o.lat1, o.lon1) || near(lat1, lon1, o.lat2, o.lon2)
                    || near(lat2, lon2, o.lat1, o.lon1) || near(lat2, lon2, o.lat2, o.lon2);
        }
    }

    private final ArrayDeque<Candidate> history = new ArrayDeque<>();
    private Candidate pending;
    private int pendingCount;

    /** Forget the drive so far (long gap without a match). */
    void reset() {
        history.clear();
        pending = null;
        pendingCount = 0;
    }

    /** @return the chosen candidate, or null if the list is empty. */
    Candidate choose(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (history.isEmpty()) return accept(lowest(candidates));

        ArrayList<Candidate> connected = new ArrayList<>();
        for (Candidate c : candidates) if (continuesRoad(c)) connected.add(c);
        if (connected.isEmpty()) return accept(lowest(candidates));   // left the chain

        Candidate bestConnected = lowest(connected);
        Candidate bestAny = lowest(candidates);
        if (continuesRoad(bestAny) || bestAny.score + SWITCH_PENALTY_METRES >= bestConnected.score) {
            pending = null;
            pendingCount = 0;
            return accept(bestConnected);
        }
        // A clearly better, unconnected road: switch only once it has been seen twice in a row.
        if (pending != null && pending.sameSegment(bestAny)) pendingCount++;
        else { pending = bestAny; pendingCount = 1; }
        if (pendingCount >= CONFIRM_FIXES) return accept(bestAny);
        return bestConnected;
    }

    private boolean continuesRoad(Candidate c) {
        for (Candidate h : history) if (h.sameSegment(c) || h.touches(c)) return true;
        return false;
    }

    private Candidate accept(Candidate chosen) {
        // Replace a repeat of the same segment instead of filling the history with it.
        for (Iterator<Candidate> it = history.iterator(); it.hasNext(); )
            if (it.next().sameSegment(chosen)) it.remove();
        history.addFirst(chosen);
        while (history.size() > HISTORY) history.removeLast();
        pending = null;
        pendingCount = 0;
        return chosen;
    }

    private static Candidate lowest(List<Candidate> list) {
        Candidate best = null;
        for (Candidate c : list) if (best == null || c.score < best.score) best = c;
        return best;
    }

    private static boolean near(double latA, double lonA, double latB, double lonB) {
        double dy = (latA - latB) * 111_320d;
        double dx = (lonA - lonB) * 111_320d * Math.cos(Math.toRadians(latA));
        return dx * dx + dy * dy <= JOIN_METRES * JOIN_METRES;
    }
}
