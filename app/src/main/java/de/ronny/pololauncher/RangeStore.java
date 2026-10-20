package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.function.LongSupplier;

final class RangeStore {
    private final SharedPreferences prefs;
    private final RangeEstimator estimator = new RangeEstimator();
    private final LongSupplier clock;
    private long savedAt;

    RangeStore(Context context, LongSupplier clock) {
        this.clock = clock;
        prefs = preferences(context);
        // One fixed range model: CAN tank/odometer plus a rolling OBD2 100-km profile.
        // Remove every preference of the retired CAN consumption learner as well as
        // the old mode selector. None of them may influence the combined model again.
        SharedPreferences.Editor migration = prefs.edit().remove("include_obd2_in_range")
                .remove("automatic_learning").remove("consumption_tenths")
                .remove("learning_revision").remove("learning_status")
                .remove("learned_consumption").remove("learn_windows")
                .remove("learn_distance").remove("learn_fuel");
        clearRetiredLearningWindow(migration);
        migration.apply();
        estimator.litres = Double.longBitsToDouble(prefs.getLong("estimated_litres", Double.doubleToLongBits(Double.NaN)));
        if (!Double.isFinite(estimator.litres) || estimator.litres < 0 || estimator.litres > 60)
            estimator.litres = Double.NaN;
        estimator.odometer = prefs.getLong("last_km", -1);
        estimator.lastOdometerAt = prefs.getLong("last_km_at", 0);
        estimator.lastTank = Double.longBitsToDouble(prefs.getLong("last_tank", Double.doubleToLongBits(Double.NaN)));
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("cockpit_range", Context.MODE_PRIVATE);
    }

    /**
     * Effective consumption rate for the fixed combined range model. The current OBD2
     * rolling 100-km range profile is preferred; while the adapter is temporarily unavailable the
     * persisted profile is restored. Without either value the calculation
     * pauses instead of inventing a CAN-only substitute.
     */
    static double consumption(Context context, VehicleState state) {
        return combinedConsumption(context, state);
    }

    private static double combinedConsumption(Context context, VehicleState state) {
        double live = state == null ? Double.NaN : state.rangeConsumption;
        double rate = validConsumption(live) ? live : Obd2Runtime.rangeAverage(context);
        return validConsumption(rate) ? rate : Double.NaN;
    }

    static boolean validConsumption(double value) {
        return Double.isFinite(value) && value >= 3d && value <= 20d;
    }

    private static void clearRetiredLearningWindow(SharedPreferences.Editor edit) {
        for (String key : new String[]{"learn_anchor_km", "learn_anchor_tank", "learn_stable_km",
                "learn_stable_tank", "learn_previous_km", "learn_previous_at",
                "learn_anchor_obd_litres", "learn_anchor_obd_km"}) edit.remove(key);
    }

    static double reserve(Context context) {
        return Math.max(0d, Math.min(10d, preferences(context).getInt("reserve_tenths", 20) / 10d));
    }

    int update(VehicleState state, Context context) {
        long now = clock.getAsLong();
        double rate = combinedConsumption(context, state);
        int km = estimator.update(state.odometerKm, state.fuelLiters,
                rate, reserve(context), now);
        if (now - savedAt >= 10_000L) save();
        return km;
    }

    void save() {
        savedAt = clock.getAsLong();
        SharedPreferences.Editor edit = prefs.edit();
        if (Double.isFinite(estimator.litres) && estimator.odometer > 0)
            edit.putLong("estimated_litres", Double.doubleToLongBits(estimator.litres))
                .putLong("last_km", estimator.odometer)
                .putLong("last_tank", Double.doubleToLongBits(estimator.lastTank))
                .putLong("last_km_at", estimator.lastOdometerAt);
        edit.apply();
    }
}
