package de.ronny.pololauncher;

import android.content.Context;

/** Model calibration from CAN tank windows, and the fuel-cut heuristic. */
public final class Obd2CalibrationTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    public static void main(String[] args) {
        android.os.SystemClock.now = 1_000_000L;
        // --- pure functions
        check(Double.isNaN(Obd2Calibration.windowRatio(4, 100, 5, 100)), "Below 5 L of real fuel: not trusted");
        check(Double.isNaN(Obd2Calibration.windowRatio(6, 99, 6, 99)), "Below 100 km: not trusted");
        check(Double.isNaN(Obd2Calibration.windowRatio(6, 100, 6, 60)), "Adapter covered only 60% of the window");
        check(Double.isNaN(Obd2Calibration.windowRatio(6, 100, 1, 100)), "Model litres too small");
        check(Double.isNaN(Obd2Calibration.windowRatio(6, 100, 20, 100)), "Ratio 0.3 is implausible");
        check(near(Obd2Calibration.windowRatio(6, 100, 7.2, 100), 6 / 7.2), "Plain ratio");
        check(near(Obd2Calibration.nextScale(1, 0.8), 0.95), "25% of the error is corrected");
        check(near(Obd2Calibration.nextScale(0.61, 0.5), 0.6), "Clamped at 0.6");
        check(near(Obd2Calibration.nextScale(1.49, 2.0), 1.5), "Clamped at 1.5");
        check(near(Obd2Calibration.nextScale(1.2, Double.NaN), 1.2), "No ratio: unchanged");

        // --- learner window carries the OBD totals of exactly that window
        ConsumptionLearner learner = new ConsumptionLearner();
        long now = 1_800_000_000_000L;
        double litres = 10, km = 50;
        for (int i = 0; i < 40; i++) learner.update(100_000, 30, now + i * 1_000L, 6.8, litres, km);
        check(learner.anchorKm == 100_000 && near(learner.anchorObdLitres, 10) && near(learner.anchorObdKm, 50), "Window anchors OBD totals");
        now += 3_600_000L;
        boolean learned = false;
        for (int i = 0; i < 40; i++) learned |= learner.update(100_120, 24, now + i * 1_000L, 6.8, 19.6, 170);
        check(learned, "Window completes (120 km, 6 L)");
        check(near(learner.windowUsedLitres, 6) && near(learner.windowDistanceKm, 120)
                && near(learner.windowObdLitres, 9.6) && near(learner.windowObdKm, 120), "Window results exposed for calibration");
        check(near(learner.anchorObdLitres, 19.6), "Next window starts from the new totals");
        // A reset of the OBD average inside the window (totals fall) is not a usable window.
        ConsumptionLearner reset = new ConsumptionLearner();
        for (int i = 0; i < 40; i++) reset.update(100_000, 30, now + i * 1_000L, 6.8, 10, 50);
        boolean l2 = false;
        for (int i = 0; i < 40; i++) l2 |= reset.update(100_120, 24, now + 3_600_000L + i * 1_000L, 6.8, 0.5, 2);
        check(l2 && Double.isNaN(reset.windowObdLitres) && Double.isNaN(reset.windowObdKm), "Falling OBD totals => no calibration data");

        // --- RangeStore feeds the calibration end to end
        Context ctx = new Context();
        Obd2Client.preferences(ctx).edit().putLong("avg_weighted_liters_bits", Double.doubleToLongBits(10d))
                .putLong("avg_weighted_km_bits", Double.doubleToLongBits(50d)).apply();
        long[] clock = {1_800_000_000_000L};
        RangeStore store = new RangeStore(ctx, () -> clock[0]);
        VehicleState state = new VehicleState();
        state.odometerKm = 100_000; state.fuelLiters = 30; state.avgConsumption = 7;
        for (int i = 0; i < 40; i++) { store.update(state, ctx); clock[0] += 1_000; }
        check(near(Obd2Runtime.veScale(ctx), 1d), "Uncalibrated before the first window");
        // The OBD model counted 9.6 L over 120 km while the tank really dropped 6 L (model 60% too high).
        Obd2Client.preferences(ctx).edit().putLong("avg_weighted_liters_bits", Double.doubleToLongBits(19.6))
                .putLong("avg_weighted_km_bits", Double.doubleToLongBits(170d)).apply();
        clock[0] += 3_600_000L;
        state.odometerKm = 100_120; state.fuelLiters = 24;
        for (int i = 0; i < 40; i++) { store.update(state, ctx); clock[0] += 1_000; }
        double expected = 1 + (6 / 9.6 - 1) * 0.25;
        check(near(Obd2Runtime.veScale(ctx), expected), "Scale corrected by a quarter of the error: " + Obd2Runtime.veScale(ctx));
        check(near(Obd2Runtime.veScale(new Context()), 1d), "Other preference stores are unaffected");
        // Persistence: a new client instance restores the scale.
        check(near(new Obd2Client().veScale(ctx), expected), "Scale persisted");
        Obd2Runtime.resetCalibration(ctx);
        check(near(Obd2Runtime.veScale(ctx), 1d), "Reset returns to 1.000");

        // --- fuel-cut heuristic
        check(Obd2PidDecoder.fuelCutSuspected(22, 2500, 60), "Overrun: high rpm, deep vacuum, rolling");
        check(!Obd2PidDecoder.fuelCutSuspected(22, 900, 60), "Idle rpm is never overrun");
        check(!Obd2PidDecoder.fuelCutSuspected(22, 2500, 3), "Standing still is never overrun");
        check(!Obd2PidDecoder.fuelCutSuspected(45, 2500, 60), "Light cruise (45 kPa) still burns fuel");
        check(!Obd2PidDecoder.fuelCutSuspected(Double.NaN, 2500, 60) && !Obd2PidDecoder.fuelCutSuspected(22, 2500, Double.NaN), "Unknown input never assumed overrun");
        check("0C".equals(Obd2PidDecoder.echoedPid("41 0C 1A F8\r>")) && "00".equals(Obd2PidDecoder.echoedPid("SEARCHING...\r41 00 BE 3E B8 13>")),
                "Echoed PID incl. SEARCHING prefix");
        check(Obd2PidDecoder.echoedPid("NO DATA>") == null && Obd2PidDecoder.echoedPid(null) == null && Obd2PidDecoder.echoedPid(">") == null,
                "No echoed PID for NO DATA / empty");
        System.out.println("Obd2CalibrationTest: all cases passed");
    }
}
