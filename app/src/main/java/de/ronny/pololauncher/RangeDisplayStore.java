package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Display-only last-known values. Never feed these back into the CAN learner. */
final class RangeDisplayStore {
    private static final String TANK = "display_last_tank_litres";
    private static final String RANGE = "display_last_range_km";

    static double tankLitres(Context context) {
        double value = Double.longBitsToDouble(RangeStore.preferences(context).getLong(TANK,
                Double.doubleToLongBits(Double.NaN)));
        return validTank(value) ? value : Double.NaN;
    }

    static int remainingKm(Context context) {
        int value = RangeStore.preferences(context).getInt(RANGE, -1);
        return value >= 0 && value <= 2_000 ? value : -1;
    }

    static void record(Context context, double freshTank, int computedRange) {
        SharedPreferences.Editor edit = null;
        if (validTank(freshTank) && Double.compare(freshTank, tankLitres(context)) != 0) {
            edit = RangeStore.preferences(context).edit();
            edit.putLong(TANK, Double.doubleToLongBits(freshTank));
        }
        if (computedRange >= 0 && computedRange <= 2_000 && computedRange != remainingKm(context)) {
            if (edit == null) edit = RangeStore.preferences(context).edit();
            edit.putInt(RANGE, computedRange);
        }
        // Persist only actual display changes, not twice per second when nothing changed.
        if (edit != null) edit.apply();
    }

    private static boolean validTank(double value) {
        return Double.isFinite(value) && value >= 0d && value <= 60d;
    }

    private RangeDisplayStore() {}
}
