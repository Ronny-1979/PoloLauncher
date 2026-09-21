package de.ronny.pololauncher;

/** Plain-Java regression tests; execute main with assertions implemented below. */
public final class RangeEstimatorTest {
    private static final long START = 1_800_000_000_000L;
    private static void eq(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        android.os.SystemClock.now = 1_000_000L; // monotonic base used for freshness stamps
        RangeEstimator e = new RangeEstimator();
        eq(147, e.update(100_000, 12, 6.8, 2, START), "Initial range minus reserve");
        eq(142, e.update(100_005, 12, 6.8, 2, START + 300_000), "Countdown with unchanged whole litres");
        eq(142, e.update(100_005, 11, 6.8, 2, START + 301_000), "Normal one litre transition");
        eq(142, e.update(100_005, 12, 6.8, 2, START + 302_000), "One litre upward slosh ignored");
        eq(-1, e.update(100_005, Double.NaN, 6.8, 2, START + 303_000), "Stale fuel hidden");
        eq(-1, e.update(-1, 12, 6.8, 2, START + 304_000), "Stale odometer hidden");
        eq(-1, e.update(0, 12, 6.8, 2, START + 305_000), "Synthetic zero odometer rejected");
        eq(-1, e.update(99_000, 12, 6.8, 2, START + 306_000), "Rollback rejected");
        eq(-1, e.update(190_000, 12, 6.8, 2, START + 307_000), "Corrupt forward jump rejected");
        eq(142, e.update(100_005, 12, 6.8, 2, START + 308_000), "Recovery after corrupt fix");
        e.update(100_005, 30, 6.8, 2, START + 309_000);
        check(e.litres < 12, "Refill must not be accepted immediately");
        e.update(100_005, 12, 6.8, 2, START + 310_000);
        e.update(100_005, 30, 6.8, 2, START + 311_000);
        e.update(100_005, 30, 6.8, 2, START + 330_000);
        eq(411, e.update(100_005, 30, 6.8, 2, START + 342_000), "Persistent refill accepted");
        RangeEstimator restored = new RangeEstimator();
        restored.litres = e.litres;
        restored.odometer = e.odometer;
        restored.lastOdometerAt = e.lastOdometerAt;
        restored.lastTank = e.lastTank;
        eq(401, restored.update(100_015, 29, 6.8, 2, START + 942_000), "Distance across restart counted");
        eq(431, restored.update(100_015, 29, 6.8, 0, START + 943_000), "Reserve editable");
        RangeEstimator empty = new RangeEstimator();
        eq(0, empty.update(100_000, 1, 6.8, 2, START), "Range never negative");
        RangeEstimator down = new RangeEstimator();
        down.update(100_000, 12, 6.8, 2, START);
        int before = down.update(100_000, 8, 6.8, 2, START + 1_000);
        check(before >= 146, "Lower tank reading damped, not jumped");
        eq(-1, down.update(100_000, 8, Double.NaN, 2, START + 2_000), "Invalid consumption rejected");
        RangeEstimator stuckTank = new RangeEstimator();
        stuckTank.update(100_000, 12, 6.8, 2, START);
        eq(87, stuckTank.update(100_060, 12, 6.8, 2, START + 3_600_000), "Unchanged tank counts down");
        eq(87, stuckTank.update(100_060, 12, 6.8, 2, START + 3_640_000), "Unchanged tank is not a refill");
        int prior = 87;
        for (int i = 61; i <= 200; i++) {
            int next = stuckTank.update(100_000 + i, 12, 6.8, 2, START + i * 60_000L);
            check(next <= prior && next >= 0, "Constant tank must count down monotonically");
            prior = next;
        }
        eq(0, prior, "Countdown bottoms at zero");
        RangeEstimator gradual = new RangeEstimator();
        gradual.update(100_000, 12, 6.8, 2, START);
        gradual.update(100_000, 14, 6.8, 2, START + 1_000);
        gradual.update(100_000, 16, 6.8, 2, START + 2_000);
        gradual.update(100_000, 18, 6.8, 2, START + 3_000);
        eq(147, gradual.update(100_000, 18, 6.8, 2, START + 20_000), "Gradual fill not accepted prematurely");
        eq(235, gradual.update(100_000, 18, 6.8, 2, START + 34_000), "Gradual refill detected");
        RangeEstimator singleSteps = new RangeEstimator();
        singleSteps.update(100_000, 12, 6.8, 2, START);
        for (int i = 1; i <= 6; i++) singleSteps.update(100_000, 12 + i, 6.8, 2, START + i * 1_000);
        eq(235, singleSteps.update(100_000, 18, 6.8, 2, START + 40_000), "One litre refill steps detected");
        RangeEstimator transientRise = new RangeEstimator();
        transientRise.update(100_000, 12, 6.8, 2, START);
        transientRise.update(100_000, 18, 6.8, 2, START + 1_000);
        eq(147, transientRise.update(100_000, 12, 6.8, 2, START + 2_000), "Transient rise ignored");
        VehicleState live = VehicleRepository.mutable();
        live.odometerKm = 100_000;
        live.odometerUpdatedAtMs = VehicleRepository.now();
        live.fuelLiters = 12;
        live.fuelUpdatedAtMs = VehicleRepository.now();
        check(VehicleRepository.snapshot().odometerKm == 100_000, "Fresh odometer retained");
        live.odometerUpdatedAtMs = VehicleRepository.now() - 16_000L;
        check(VehicleRepository.snapshot().odometerKm == -1, "Stale odometer invalidated independently");
        check(VehicleRepository.snapshot().fuelLiters == 12, "Fresh fuel unaffected by stale odometer");
        System.out.println("RangeEstimatorTest: all cases passed");
    }
}
