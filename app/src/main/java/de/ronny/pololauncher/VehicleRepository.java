package de.ronny.pololauncher;

import android.os.SystemClock;

public final class VehicleRepository {
    public static final double POLO_6R_TANK_LITERS = 45.0;
    private static final long LIVE_VALUE_TTL_MS = 15_000L;
    private static final long TEMPERATURE_TTL_MS = 60_000L;
    private static final VehicleState STATE = new VehicleState();

    private VehicleRepository() {}

    /**
     * Monotonic time base for every freshness stamp in {@link VehicleState}. Head units set their
     * wall clock (GPS/NTP) shortly after start; that jump must never make fresh data look stale.
     */
    public static long now() { return SystemClock.elapsedRealtime(); }

    public static synchronized VehicleState snapshot() {
        VehicleState copy = STATE.copy();
        long now = now();
        if (!fresh(copy.odometerUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) copy.odometerKm = -1;
        if (!fresh(copy.fuelUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) {
            copy.fuelLiters = Double.NaN;
            copy.fuelPercent = Double.NaN;
        }
        if (!fresh(copy.instConsumptionUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) {
            copy.instConsumption = Double.NaN;
            copy.obdFuelLitersPerHour = Double.NaN;
            copy.obdSpeedKmh = Double.NaN;
            copy.obdRpm = -1;
            copy.avgConsumption = Double.NaN;
        }
        if (!fresh(copy.voltageUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) copy.voltage = Double.NaN;
        if (!fresh(copy.outsideUpdatedAtMs, now, TEMPERATURE_TTL_MS)) copy.outsideC = Double.NaN;
        if (!fresh(copy.doorsUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) {
            copy.doorsKnown = false;
            copy.trunkKnown = false;
            copy.doorFL = copy.doorFR = copy.doorRL = copy.doorRR = false;
            copy.trunk = false;
        }
        return copy;
    }

    /** Raw shared state. Callers must hold {@code synchronized (VehicleRepository.class)}. */
    public static synchronized VehicleState mutable() { return STATE; }

    public static synchronized void touch(String source) {
        STATE.source = source;
        STATE.updatedAtMs = now();
    }

    public static synchronized boolean isRecent() {
        return STATE.updatedAtMs > 0 && now() - STATE.updatedAtMs < 5000;
    }

    private static boolean fresh(long timestamp, long now, long ttl) {
        long age = now - timestamp;
        return timestamp > 0L && age >= 0L && age <= ttl;
    }
}
