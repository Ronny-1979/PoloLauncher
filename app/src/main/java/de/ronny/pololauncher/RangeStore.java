package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.function.LongSupplier;

final class RangeStore {
    private final SharedPreferences prefs;
    private final RangeEstimator estimator = new RangeEstimator();
    private final LongSupplier clock;
    private ConsumptionLearner learner;
    private long loadedRevision;
    private long savedAt;

    RangeStore(Context context) {
        this(context, System::currentTimeMillis);
    }

    RangeStore(Context context, LongSupplier clock) {
        this.clock = clock;
        prefs = preferences(context);
        estimator.litres = Double.longBitsToDouble(prefs.getLong("estimated_litres", Double.doubleToLongBits(Double.NaN)));
        if (!Double.isFinite(estimator.litres) || estimator.litres < 0 || estimator.litres > 60)
            estimator.litres = Double.NaN;
        estimator.odometer = prefs.getLong("last_km", -1);
        estimator.lastOdometerAt = prefs.getLong("last_km_at", 0);
        estimator.lastTank = Double.longBitsToDouble(prefs.getLong("last_tank", Double.doubleToLongBits(Double.NaN)));
        reloadLearner();
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("cockpit_range", Context.MODE_PRIVATE);
    }

    static double consumption(Context context) {
        return consumption(context, null);
    }

    /**
     * Effective consumption rate for the range countdown. When {@link #includeObd2InRange}
     * is enabled, prefers the OBD2 running average (state.avgConsumption) over the
     * CAN-learned/manual rate whenever it is itself plausible - it reflects actual current
     * driving directly, rather than the slowly-adapting CAN-learned long-term value - and
     * restores the cumulative OBD average whenever OBD2 isn't available (adapter not
     * connected, ignition off, or not enough distance yet for a meaningful OBD2 average).
     * When the setting is disabled, the CAN-learned/manual rate is used unconditionally,
     * regardless of what OBD2 reports - v0.8.0: this is now the user's own explicit choice
     * (Einstellungen -> Tank und Reichweite), not an automatic switch, so the range display
     * never changes basis on its own mid-drive.
     * state may be null (e.g. a settings screen with no live snapshot yet), which always
     * restores the cumulative OBD average, same as the single-arg overload.
     * Without any plausible OBD baseline, combined mode pauses instead of using CAN-only.
     */
    static double consumption(Context context, VehicleState state) {
        SharedPreferences p = preferences(context);
        double learned = number(p, "learned_consumption", Double.NaN);
        double canRate = automaticLearning(context) && ConsumptionLearner.validRate(learned)
                ? learned : manualConsumption(context);
        if (!includeObd2InRange(context)) return canRate;
        return combinedConsumption(context, state);
    }

    /** Whether the range countdown may use the OBD2 running average - see {@link #consumption}. */
    static boolean includeObd2InRange(Context context) {
        return preferences(context).getBoolean("include_obd2_in_range", true);
    }

    static void setIncludeObd2InRange(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("include_obd2_in_range", enabled).apply();
    }

    private static double combinedConsumption(Context context, VehicleState state) {
        double live = state == null ? Double.NaN : state.avgConsumption;
        double rate = ConsumptionLearner.validRate(live) ? live : Obd2Runtime.rangeAverage(context);
        return ConsumptionLearner.validRate(rate) ? rate : Double.NaN;
    }

    static double manualConsumption(Context context) {
        return Math.max(3d, Math.min(20d, preferences(context).getInt("consumption_tenths", 68) / 10d));
    }

    static boolean automaticLearning(Context context) {
        return preferences(context).getBoolean("automatic_learning", true);
    }

    static void setAutomaticLearning(Context context, boolean enabled) {
        SharedPreferences p = preferences(context);
        SharedPreferences.Editor edit = p.edit().putBoolean("automatic_learning", enabled)
                .putLong("learning_revision", p.getLong("learning_revision", 0L) + 1L);
        clearSavedWindow(edit);
        edit.putString("learning_status", enabled ? "Neuer Lernabschnitt beginnt mit stabilen CAN-Daten"
                : "Automatische Lernfunktion ausgeschaltet").apply();
    }

    static void resetLearning(Context context) {
        SharedPreferences p = preferences(context);
        SharedPreferences.Editor edit = p.edit().remove("learned_consumption")
                .remove("learn_windows").remove("learn_distance").remove("learn_fuel")
                .putLong("learning_revision", p.getLong("learning_revision", 0L) + 1L)
                .putString("learning_status", "Lernwerte zurückgesetzt; starte mit Grundverbrauch");
        clearSavedWindow(edit);
        edit.apply();
    }

    private static void clearSavedWindow(SharedPreferences.Editor edit) {
        for (String key : new String[]{"learn_anchor_km", "learn_anchor_tank", "learn_stable_km",
                "learn_stable_tank", "learn_previous_km", "learn_previous_at",
                "learn_anchor_obd_litres", "learn_anchor_obd_km"}) edit.remove(key);
    }

    static String learningStatus(Context context) {
        return preferences(context).getString("learning_status", "Warte auf stabile CAN-Daten");
    }

    static long learnedWindows(Context context) {
        return Math.max(0L, preferences(context).getLong("learn_windows", 0L));
    }

    private static double number(SharedPreferences p, String key, double fallback) {
        return Double.longBitsToDouble(p.getLong(key, Double.doubleToLongBits(fallback)));
    }

    private void reloadLearner() {
        learner = new ConsumptionLearner();
        loadedRevision = prefs.getLong("learning_revision", 0L);
        learner.learned = number(prefs, "learned_consumption", Double.NaN);
        if (!ConsumptionLearner.validRate(learner.learned)) learner.learned = Double.NaN;
        learner.windows = Math.max(0L, prefs.getLong("learn_windows", 0L));
        learner.learnedDistance = Math.max(0L, prefs.getLong("learn_distance", 0L));
        learner.learnedFuel = number(prefs, "learn_fuel", 0d);
        if (!Double.isFinite(learner.learnedFuel) || learner.learnedFuel < 0) learner.learnedFuel = 0d;
        learner.anchorKm = prefs.getLong("learn_anchor_km", -1L);
        learner.anchorTank = number(prefs, "learn_anchor_tank", Double.NaN);
        learner.stableKm = prefs.getLong("learn_stable_km", -1L);
        learner.stableTank = number(prefs, "learn_stable_tank", Double.NaN);
        learner.previousKm = prefs.getLong("learn_previous_km", -1L);
        learner.previousAt = prefs.getLong("learn_previous_at", 0L);
        learner.anchorObdLitres = finiteOrNaN(number(prefs, "learn_anchor_obd_litres", Double.NaN));
        learner.anchorObdKm = finiteOrNaN(number(prefs, "learn_anchor_obd_km", Double.NaN));
        if (learner.anchorKm <= 0 || learner.anchorKm >= 2_000_000 || learner.stableKm < learner.anchorKm
                || learner.stableKm >= 2_000_000 || learner.previousKm < learner.stableKm
                || learner.previousKm >= 2_000_000 || !Double.isFinite(learner.anchorTank)
                || !Double.isFinite(learner.stableTank) || learner.anchorTank <= 0 || learner.anchorTank > 60
                || learner.stableTank <= 0 || learner.stableTank > learner.anchorTank) learner.clearWindow();
    }

    private static double finiteOrNaN(double value) {
        return Double.isFinite(value) && value >= 0d ? value : Double.NaN;
    }

    private void refreshLearnerConfig() {
        if (loadedRevision != prefs.getLong("learning_revision", 0L)) reloadLearner();
    }

    static double reserve(Context context) {
        return Math.max(0d, Math.min(10d, preferences(context).getInt("reserve_tenths", 20) / 10d));
    }

    int update(VehicleState state, Context context) {
        refreshLearnerConfig();
        long now = clock.getAsLong();
        boolean learnedNow = false;
        if (automaticLearning(context)) {
            double[] obd = Obd2Runtime.totals(context);
            learnedNow = learner.update(state.odometerKm, state.fuelLiters, now,
                    manualConsumption(context), obd[0], obd[1]);
            if (learnedNow) calibrateObdModel(context);
            String status = learner.status();
            if (!status.equals(prefs.getString("learning_status", "")))
                prefs.edit().putString("learning_status", status).apply();
        } else learner.clearWindow();
        double canRate = automaticLearning(context) ? learner.value(manualConsumption(context))
                : manualConsumption(context);
        // v0.8.0: only prefer the OBD2 running average when the user has explicitly turned
        // that on (Einstellungen -> Tank und Reichweite) - see includeObd2InRange/consumption
        // above. No longer an automatic switch: with the setting off, CAN is used
        // unconditionally, so the range basis never changes on its own mid-drive.
        double rate = includeObd2InRange(context) ? combinedConsumption(context, state) : canRate;
        int km = estimator.update(state.odometerKm, state.fuelLiters,
                rate, reserve(context), now);
        if (learnedNow || now - savedAt >= 10_000L) save();
        return km;
    }

    /**
     * The CAN tank drop of a finished learning window is real fuel; the OBD model integrated its
     * own estimate over the same window. Correct the model's efficiency factor by the difference.
     */
    private void calibrateObdModel(Context context) {
        double ratio = Obd2Calibration.windowRatio(learner.windowUsedLitres, learner.windowDistanceKm,
                learner.windowObdLitres, learner.windowObdKm);
        learner.clearCalibrationWindow();
        if (Double.isFinite(ratio)) Obd2Runtime.calibrate(context, ratio);
    }

    void save() {
        refreshLearnerConfig();
        savedAt = clock.getAsLong();
        SharedPreferences.Editor edit = prefs.edit();
        if (Double.isFinite(estimator.litres) && estimator.odometer > 0)
            edit.putLong("estimated_litres", Double.doubleToLongBits(estimator.litres))
                .putLong("last_km", estimator.odometer)
                .putLong("last_tank", Double.doubleToLongBits(estimator.lastTank))
                .putLong("last_km_at", estimator.lastOdometerAt);
        edit.putLong("learned_consumption", Double.doubleToLongBits(learner.learned))
                .putLong("learn_windows", learner.windows).putLong("learn_distance", learner.learnedDistance)
                .putLong("learn_fuel", Double.doubleToLongBits(learner.learnedFuel))
                .putLong("learn_anchor_km", learner.anchorKm)
                .putLong("learn_anchor_tank", Double.doubleToLongBits(learner.anchorTank))
                .putLong("learn_stable_km", learner.stableKm)
                .putLong("learn_stable_tank", Double.doubleToLongBits(learner.stableTank))
                .putLong("learn_previous_km", learner.previousKm).putLong("learn_previous_at", learner.previousAt)
                .putLong("learn_anchor_obd_litres", Double.doubleToLongBits(learner.anchorObdLitres))
                .putLong("learn_anchor_obd_km", Double.doubleToLongBits(learner.anchorObdKm)).apply();
    }
}
