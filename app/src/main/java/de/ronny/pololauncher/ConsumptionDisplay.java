package de.ronny.pololauncher;

import java.util.Locale;

/** Display only: the hourly rate replaces the instantaneous per-distance rate at standstill and when coasting; never the range/average. */
public final class ConsumptionDisplay {
    /** Below this value the car is coasting without fuel flow; the display switches to l/h. */
    static final double COASTING_L_PER_100KM = 0.05d;

    private ConsumptionDisplay() {}

    public static String instantaneous(VehicleState s) {
        long age = VehicleRepository.now() - s.instConsumptionUpdatedAtMs;
        // A newer, fresh CAN engine-off signal overrides an older OBD idle sample.
        long canAge = VehicleRepository.now() - s.rpmUpdatedAtMs;
        if (s.rpm == 0 && s.rpmUpdatedAtMs >= s.instConsumptionUpdatedAtMs
                && s.rpmUpdatedAtMs > 0 && canAge >= 0 && canAge <= 15_000L) return "--";
        if (s.instConsumptionUpdatedAtMs <= 0 || age < 0 || age > 15_000L) return "--";
        // Require a running engine for every unit, including a rolling car.
        if (s.obdRpm <= 0) return "--";
        if (Double.isFinite(s.obdSpeedKmh) && s.obdSpeedKmh >= 0 && s.obdSpeedKmh < 3.0) {
            if (s.obdRpm > 0 && Double.isFinite(s.obdFuelLitersPerHour) && s.obdFuelLitersPerHour >= 0)
                return String.format(Locale.GERMANY, "%.1f l/h", s.obdFuelLitersPerHour);
            return "--";
        }
        // Rolling without throttle (overrun / fuel cut): l per 100 km is then 0, which says nothing.
        // Show the real fuel flow in l/h instead, as at a standstill.
        if (Double.isFinite(s.instConsumption) && s.instConsumption >= 0 && s.instConsumption < COASTING_L_PER_100KM
                && Double.isFinite(s.obdFuelLitersPerHour) && s.obdFuelLitersPerHour >= 0)
            return String.format(Locale.GERMANY, "%.1f l/h", s.obdFuelLitersPerHour);
        return Double.isFinite(s.instConsumption) && s.instConsumption >= 0
                ? String.format(Locale.GERMANY, "%.1f l", s.instConsumption) : "--";
    }
}
