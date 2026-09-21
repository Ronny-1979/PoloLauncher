package de.ronny.pololauncher;

import java.util.Locale;

/** Display only: never substitutes a hourly rate for the range/average per-distance rate. */
public final class ConsumptionDisplay {
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
                return String.format(Locale.GERMANY, "%.1f l", s.obdFuelLitersPerHour);
            return "--";
        }
        return Double.isFinite(s.instConsumption) && s.instConsumption >= 0
                ? String.format(Locale.GERMANY, "%.1f l", s.instConsumption) : "--";
    }
}
