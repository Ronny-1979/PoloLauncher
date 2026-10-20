package de.ronny.pololauncher;

import java.util.ArrayList;
import java.util.List;

public final class RoadSnapSelectorTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }

    private static final double LAT = 51.0, LON = 13.0;
    private static double dLat(double m) { return m / 111_320d; }
    private static double dLon(double m) { return m / (111_320d * Math.cos(Math.toRadians(LAT))); }

    /** East-west segment [x1,x2] metres east of origin at y metres north. */
    private static RoadSnapSelector.Candidate seg(double x1, double x2, double y, double score) {
        return new RoadSnapSelector.Candidate(LAT + dLat(y), LON + dLon(x1), LAT + dLat(y), LON + dLon(x2),
                LAT + dLat(y), LON + dLon((x1 + x2) / 2), Math.abs(score), score, false);
    }

    private static List<RoadSnapSelector.Candidate> list(RoadSnapSelector.Candidate... c) {
        List<RoadSnapSelector.Candidate> l = new ArrayList<>();
        for (RoadSnapSelector.Candidate x : c) l.add(x);
        return l;
    }

    public static void main(String[] a) {
        RoadSnapSelector s = new RoadSnapSelector();
        check(s.choose(new ArrayList<>()) == null, "empty list gives null");

        // No history: nearest wins.
        RoadSnapSelector.Candidate road1 = seg(0, 50, 0, 6);
        RoadSnapSelector.Candidate parallel = seg(0, 50, 14, 4);   // parallel road, nominally closer
        check(s.choose(list(road1, parallel)) == parallel, "without history the best score wins");

        // Parallel road does not steal the car once the drive has history on road A.
        s = new RoadSnapSelector();
        RoadSnapSelector.Candidate a1 = seg(0, 50, 0, 5);
        s.choose(list(a1));
        RoadSnapSelector.Candidate a2 = seg(50, 100, 0, 8);          // continues road A
        RoadSnapSelector.Candidate b2 = seg(50, 100, 14, 3);         // parallel road B, closer by 5
        check(s.choose(list(a2, b2)) == a2, "connected road kept against a slightly closer parallel road");

        // A clearly better unconnected road needs two agreeing fixes.
        s = new RoadSnapSelector();
        s.choose(list(a1));
        RoadSnapSelector.Candidate far = seg(0, 50, 30, 2);          // unconnected, 6+ m better after penalty
        RoadSnapSelector.Candidate weak = seg(50, 100, 0, 20);       // connected but poor
        check(s.choose(list(weak, far)) == weak, "first fix on a new road does not switch yet");
        check(s.choose(list(weak, far)) == far, "second agreeing fix switches");

        // Nothing connected in range: switch at once.
        s = new RoadSnapSelector();
        s.choose(list(a1));
        RoadSnapSelector.Candidate elsewhere = seg(500, 550, 0, 6);
        check(s.choose(list(elsewhere)) == elsewhere, "left the chain: accept immediately");

        // reset forgets the drive.
        s = new RoadSnapSelector();
        s.choose(list(a1));
        s.reset();
        check(s.choose(list(a2, b2)) == b2, "reset clears history");

        // Candidates sharing an end point are connected (junction).
        check(a1.touches(seg(50, 100, 0, 1)) && !a1.touches(seg(500, 550, 0, 1)), "touch detection");
        check(a1.sameSegment(seg(50, 0, 0, 1)), "same segment regardless of direction");

        // Rendered-map road kinds.
        check(RoadKinds.ok("residential") && RoadKinds.ok("motorway")
                && !RoadKinds.ok("rail") && !RoadKinds.ok("path") && !RoadKinds.ok(null),
                "road kind filter");
        System.out.println("RoadSnapSelectorTest: road continuity, switch confirmation and kind filter");
    }
}
