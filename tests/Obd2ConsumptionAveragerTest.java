package de.ronny.pololauncher;

public final class Obd2ConsumptionAveragerTest {
    private static void eq(double expected, double actual, double tol, String message) {
        if (!Double.isFinite(expected) || !Double.isFinite(actual) || Math.abs(expected - actual) > tol) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void nan(double actual, String message) {
        if (!Double.isNaN(actual)) throw new AssertionError(message + ": expected NaN, got " + actual);
    }

    public static void main(String[] args) {
        boolean rejectedNaN = false;
        try { eq(6d, Double.NaN, 0.001, "NaN regression"); }
        catch (AssertionError expected) { rejectedNaN = true; }
        if (!rejectedNaN) throw new AssertionError("Numeric comparison must reject NaN");
        Obd2ConsumptionAverager slow = new Obd2ConsumptionAverager();
        slow.add(6d, 100d, 7d);
        eq(6d * 7d / 3600d, slow.weightedLiters(), 1e-12, "Slow valid polling retains fuel");
        eq(100d * 7d / 3600d, slow.weightedKm(), 1e-12, "Slow valid polling retains distance");
        slow.add(6d, 100d, 60d);
        eq(6d * 7d / 3600d, slow.weightedLiters(), 1e-12, "Standby gap is not integrated");
        Obd2ConsumptionAverager empty = new Obd2ConsumptionAverager();
        nan(empty.value(), "No samples yet");

        // Constant 6 l/h at a constant 100 km/h for one simulated hour (3600 x 1s samples)
        // must converge on 6 l/100km, the definition of the figure.
        Obd2ConsumptionAverager steady = new Obd2ConsumptionAverager();
        for (int i = 0; i < 3600; i++) steady.add(6.0, 100.0, 1.0);
        eq(6.0d, steady.value(), 1e-6, "Steady 6 l/h at 100 km/h converges to 6 l/100km");

        // A stationary idle period (0 km/h) must not corrupt the distance-weighted average:
        // no distance travelled, so it must not count toward the l/100km figure at all.
        Obd2ConsumptionAverager idleThenDrive = new Obd2ConsumptionAverager();
        for (int i = 0; i < 600; i++) idleThenDrive.add(1.0, 0.0, 1.0); // 10 min idle burning fuel
        nan(idleThenDrive.value(), "Idle-only burn with zero distance stays NaN, not a huge number");
        for (int i = 0; i < 3600; i++) idleThenDrive.add(6.0, 100.0, 1.0);
        // idle fuel is still counted in the numerator even though it added no distance,
        // so the result must sit at or above the pure-driving 6.0 baseline.
        double afterIdle = idleThenDrive.value();
        boolean atLeastBaseline = afterIdle >= 6.0d - 1e-6;
        if (!atLeastBaseline) throw new AssertionError("Idle fuel must raise, never lower, the average: " + afterIdle);

        // Implausible/garbage samples must be ignored rather than corrupting the average.
        Obd2ConsumptionAverager guarded = new Obd2ConsumptionAverager();
        guarded.add(6.0, Double.NaN, 1.0);
        guarded.add(6.0, -1, 1.0);
        eq(0, guarded.weightedLiters(), 0, "Missing speed cannot accumulate fuel without distance");
        guarded.add(Double.NaN, 100.0, 1.0);
        guarded.add(-5.0, 100.0, 1.0);
        guarded.add(6.0, 100.0, -1.0);
        guarded.add(6.0, 100.0, 999.0);
        nan(guarded.value(), "All-garbage input never produces a value");
        for (int i = 0; i < 100; i++) guarded.add(6.0, 100.0, 1.0);
        eq(6.0d, guarded.value(), 1e-6, "Valid samples still average correctly after garbage was skipped");

        Obd2ConsumptionAverager resettable = new Obd2ConsumptionAverager();
        for (int i = 0; i < 100; i++) resettable.add(6.0, 100.0, 1.0);
        resettable.reset();
        nan(resettable.value(), "Reset clears the accumulated average");

        // Persistence round-trip (Obd2Client saves/restores exactly these two numbers).
        Obd2ConsumptionAverager beforeRestart = new Obd2ConsumptionAverager();
        for (int i = 0; i < 1800; i++) beforeRestart.add(6.0, 100.0, 1.0); // half an hour driving
        double savedLiters = beforeRestart.weightedLiters();
        double savedKm = beforeRestart.weightedKm();
        eq(6.0d, beforeRestart.value(), 1e-6, "Value before simulated restart");

        Obd2ConsumptionAverager afterRestart = new Obd2ConsumptionAverager();
        nan(afterRestart.value(), "Fresh instance has no value before restore");
        afterRestart.restore(savedLiters, savedKm);
        eq(6.0d, afterRestart.value(), 1e-6, "Restored instance reproduces the same average");
        // Continuing to drive after restore must accumulate onto the restored total, not
        // start over - this is the whole point of persisting across restarts.
        for (int i = 0; i < 1800; i++) afterRestart.add(6.0, 100.0, 1.0);
        eq(2.0d * savedKm, afterRestart.weightedKm(), 1e-6, "Distance accumulates onto the restored total");
        eq(6.0d, afterRestart.value(), 1e-6, "Average stays correct after resuming past a restore");

        // Invalid/corrupt persisted data must not be applied (start fresh instead of crashing
        // or producing a bogus average from garbage SharedPreferences content).
        Obd2ConsumptionAverager guardedRestore = new Obd2ConsumptionAverager();
        guardedRestore.restore(Double.NaN, 100.0);
        nan(guardedRestore.value(), "NaN saved litres rejected, not applied");
        guardedRestore.restore(50.0, -10.0);
        nan(guardedRestore.value(), "Negative saved km rejected, not applied");
        guardedRestore.restore(50.0, 1000.0);
        eq(5.0d, guardedRestore.value(), 1e-6, "Valid restore still works after rejected attempts");

        System.out.println("Obd2ConsumptionAveragerTest: all cases passed");
    }
}
