package de.ronny.pololauncher;

/** A wrong odometer baseline (glitch frame, persisted) must not disable the range for good. */
public final class RangeEstimatorRecoveryTest {
    static final long T0 = 1_800_000_000_000L;
    static void eq(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        // 1) First frame is an outlier that is inside the accepted range and becomes the baseline.
        RangeEstimator e1 = new RangeEstimator();
        eq(411, e1.update(1_999_000, 30, 6.8, 2, T0), "Outlier accepted as first baseline (no reference yet)");
        long t = T0;
        for (int i = 1; i <= 29; i++) eq(-1, e1.update(100_000 + i / 10, 30, 6.8, 2, t += 1_000), "Rejected while unconfirmed");
        eq(-1, e1.update(100_003, 30, 6.8, 2, t += 1_000), "Still inside the confirmation window");
        int recovered = e1.update(100_003, 30, 6.8, 2, t += 2_000);
        check(recovered > 0 && e1.odometer == 100_003, "Consistent odometer adopted as new baseline after 30 s: " + recovered);
        check(e1.litres == 30, "Fuel estimate is kept when only the odometer is rebased");

        // 2) Baseline fine, but a single outlier frame appears after two weeks parked.
        RangeEstimator e2 = new RangeEstimator();
        e2.update(100_000, 30, 6.8, 2, T0);
        long later = T0 + 14L * 24 * 3_600_000;
        eq(-1, e2.update(150_000, 30, 6.8, 2, later), "Outlier after long standstill is rejected (tolerance capped at 24 h)");
        int normal = e2.update(100_000, 30, 6.8, 2, later + 1_000);
        check(normal > 0 && e2.litres == 30 && e2.odometer == 100_000, "One glitch frame changes neither baseline nor fuel");
        eq(normal, e2.update(100_000, 30, 6.8, 2, later + 2_000), "Range continues undisturbed");
        check(e2.update(100_010, 30, 6.8, 2, later + 60_000) > 0, "Normal driving after the glitch");

        // 3) Alternating garbage never rebases (each frame restarts the confirmation).
        RangeEstimator e3 = new RangeEstimator();
        e3.update(100_000, 30, 6.8, 2, T0);
        for (int i = 1; i <= 100; i++) {
            long km = (i % 2 == 0) ? 150_000 : 90_000;
            eq(-1, e3.update(km, 30, 6.8, 2, T0 + i * 1_000L), "Alternating garbage stays rejected");
        }
        check(e3.odometer == 100_000, "Baseline untouched by alternating garbage");

        // 4) A long trip that was really driven while the app saw nothing (within 24 h tolerance).
        RangeEstimator e4 = new RangeEstimator();
        e4.update(100_000, 40, 6.8, 2, T0);
        int afterTrip = e4.update(100_300, 40, 6.8, 2, T0 + 5 * 3_600_000L);
        check(afterTrip >= 0 && e4.odometer == 100_300, "Unobserved but plausible trip is accepted directly");
        System.out.println("RangeEstimatorRecoveryTest: all cases passed");
    }
}
