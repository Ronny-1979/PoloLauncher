package de.ronny.pololauncher;

public final class CameraAimTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }

    private static final double LAT = 51.0, LON = 13.0;
    private static double north(double m) { return LAT + m / 111_320d; }
    private static double metresNorth(double[] p) { return (p[0] - LAT) * 111_320d; }
    private static double metresEast(double[] p) { return (p[1] - LON) * 111_320d * Math.cos(Math.toRadians(LAT)); }

    public static void main(String[] a) {
        // Steady drive north at 14 m/s: aim advances with the car, lead is speed * glide.
        CameraAim aim = new CameraAim();
        double[] a1 = aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        double[] a2 = aim.next(north(14), LON, 14f, 0f, true, 1000L, 2000L);
        check(Math.abs(metresNorth(a1) - 14d) < 0.2, "first aim leads by speed * glide");
        check(Math.abs(metresNorth(a2) - metresNorth(a1) - 14d) < 0.2, "aim moves with the car");

        // Braking hard: the second fix is only 6 m further and slow. Never aim behind the last aim.
        aim = new CameraAim();
        a1 = aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(6), LON, 3.5f, 0f, true, 1000L, 2000L);
        check(metresNorth(a2) >= metresNorth(a1) - 1e-6, "braking never pulls the aim backwards");
        // ...and the lead itself uses the lower speed, so it does not overshoot.
        CameraAim fresh = new CameraAim();
        fresh.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        double[] raw = CameraMotion.project(north(6), LON, 3.5f, 0f, true, 1000L);
        check(Math.abs(metresNorth(raw) - 9.5d) < 0.2, "sanity: unguarded aim would be 6 + 3.5 m");

        // Sideways movement is kept while the backward part is dropped (curve).
        aim = new CameraAim();
        aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(0), LON + 5d / (111_320d * Math.cos(Math.toRadians(LAT))), 3.5f, 0f, true, 1000L, 2000L);
        check(metresEast(a2) > 4.5d, "lateral shift survives the guard");

        // A real jump back (> 40 m) is not held.
        aim = new CameraAim();
        aim.next(north(100), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(0), LON, 14f, 0f, true, 1000L, 2000L);
        check(metresNorth(a2) < 30d, "large backward jump is accepted as real");

        // Turning more than 60 degrees: the old heading is no longer a reference.
        aim = new CameraAim();
        aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(-5), LON, 14f, 180f, true, 1000L, 2000L);
        check(metresNorth(a2) < 0d, "U-turn is not held to the old direction");

        // Standing still: no lead, no memory.
        aim = new CameraAim();
        aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(1), LON, 0.5f, 0f, true, 1000L, 2000L);
        check(Math.abs(metresNorth(a2) - 1d) < 0.05, "no lead when almost stopped");
        double[] a3 = aim.next(north(-20), LON, 14f, 0f, true, 1000L, 3000L);
        check(metresNorth(a3) < 0d + 1d, "stop forgets the old aim");

        // Stale memory (> 5 s) is ignored.
        aim = new CameraAim();
        aim.next(north(100), LON, 14f, 0f, true, 1000L, 1000L);
        a2 = aim.next(north(0), LON, 14f, 0f, true, 1000L, 8000L);
        check(Math.abs(metresNorth(a2) - 14d) < 0.2, "stale aim is not used as reference");

        // Untrusted bearing: no lead and no guard.
        aim = new CameraAim();
        a1 = aim.next(north(0), LON, 14f, 0f, false, 1000L, 1000L);
        check(Math.abs(metresNorth(a1)) < 1e-6, "untrusted bearing: raw position");

        // Re-centre without a new fix reuses the last aim of that fix, not the raw fix.
        aim = new CameraAim();
        a1 = aim.next(north(0), LON, 14f, 0f, true, 1000L, 1000L);
        android.location.Location fix = new android.location.Location("t");
        fix.setLatitude(north(0)); fix.setLongitude(LON);
        double[] held = aim.hold(fix, 1500L);
        check(Math.abs(held[0] - a1[0]) < 1e-12, "hold returns the last aim of the same fix");
        fix.setLatitude(north(50));
        check(Math.abs(aim.hold(fix, 1500L)[0] - north(50)) < 1e-12, "hold of another fix is the raw fix");
        fix.setLatitude(north(0));
        check(Math.abs(aim.hold(fix, 9000L)[0] - north(0)) < 1e-12, "hold ignores a stale aim");
        System.out.println("CameraAimTest: camera aim never moves backwards while driving straight");
    }
}
