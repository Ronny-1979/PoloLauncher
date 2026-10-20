package de.ronny.pololauncher;

import android.os.SystemClock;

public final class VehicleRepository {
    public static final double POLO_6R_TANK_LITERS = 45.0;
    private static final long LIVE_VALUE_TTL_MS = 15_000L;
    // Der HCT-Poller fragt Mode 2 (enthält die Drehzahl) laut OEM-Timing alle ~400ms ab.
    // Eine kurze, eigene TTL nur für die Drehzahl sorgt dafür, dass ein beim Abstellen des
    // Motors "eingefrorener" alter Wert nicht noch bis zu 15 Sekunden lang stehen bleibt
    // (wie bei der allgemeinen LIVE_VALUE_TTL_MS), sondern binnen ~2 Sekunden auf 0 U/min
    // bzw. "--" wechselt - das Fünffache des normalen Abfrageintervalls, um vereinzelt
    // verzögerte Frames beim normalen Fahren nicht fälschlich als "Motor aus" zu werten.
    private static final long RPM_TTL_MS = 2_000L;
    // The CANBOX keeps sending the last rpm value after the engine was switched off (Mode-3 warnings
    // are not always set then). A running engine jitters by a few rpm from frame to frame, so a
    // value that has not changed for this long, while frames keep arriving, means "engine off".
    static final long RPM_FROZEN_MS = 4_000L;
    // HCT Mode 3 is normally polled roughly every 1.5 s. After 5 s without a fresh
    // Mode-3 decision its previous engine state is no longer authoritative.
    private static final long ENGINE_STATE_TTL_MS = 5_000L;
    private static final long TEMPERATURE_TTL_MS = 60_000L;
    private static final VehicleState STATE = new VehicleState();

    private VehicleRepository() {}

    /**
     * Monotonic time base for every freshness stamp in {@link VehicleState}. Head units set their
     * wall clock (GPS/NTP) shortly after start; that jump must never make fresh data look stale.
     */
    public static long now() { return SystemClock.elapsedRealtime(); }

    private static long lastRpmLogAtMs;
    private static int lastRpmLogged = Integer.MIN_VALUE;

    /**
     * Diagnostic only: logs rpm changes at standstill with their source (max. 1/s), so a log
     * shows whether a "stuck" rpm really is constant or flips between two sources.
     */
    static synchronized void logRpmAtStandstill(String source, int rpm, VehicleState s) {
        long now = now();
        if (s.speedKmh >= 2.0 || rpm == lastRpmLogged || now - lastRpmLogAtMs < 1_000L) return;
        lastRpmLogAtMs = now;
        lastRpmLogged = rpm;
        DiagLog.add("RPM-Verlauf (Stillstand): " + source + " -> " + rpm + " U/min");
    }

    public static synchronized VehicleState snapshot() {
        VehicleState copy = STATE.copy();
        long now = now();
        final double lastKnownSpeed = copy.speedKmh;
        if (!fresh(copy.speedUpdatedAtMs, now, LIVE_VALUE_TTL_MS)) copy.speedKmh = Double.NaN;

        // HCT Mode 3 is a stronger engine-state signal than a stale/frozen RPM sample, but
        // only while that status itself is fresh. Otherwise an old "Motor aus" frame could
        // pin the dashboard to 0 U/min forever after the CAN source disappears.
        boolean engineStateFresh = copy.engineStateKnown
                && fresh(copy.engineStateUpdatedAtMs, now, ENGINE_STATE_TTL_MS);
        if (engineStateFresh && copy.engineStoppedByCan) {
            copy.rpm = 0;
        } else if (!fresh(copy.rpmUpdatedAtMs, now, RPM_TTL_MS)) {
            // Drehzahl und Geschwindigkeit stammen aus demselben CAN-Frame (HCT Mode 2) und
            // werden dort gemeinsam aktualisiert, wenn der Wert plausibel ist. Kommt der Frame
            // weiterhin an (Geschwindigkeit bleibt frisch), aber die Drehzahl speziell nicht
            // mehr - typischerweise wenn der Motor aus ist und das Steuergerät keinen
            // plausiblen Drehzahlwert mehr liefert - ist das ein zuverlässiges "0 U/min"-Signal,
            // kein "keine Daten". Kommen gar keine Frames mehr an (auch Geschwindigkeit ist
            // abgelaufen), bleibt es bei "unbekannt" (-1 -> "--").
            // Speed and RPM come from the same Mode-2 frame. Use the same short freshness
            // window here, not the generic 15-s dashboard TTL; otherwise a completely stopped
            // CAN stream could look like "Motor aus" for many extra seconds.
            boolean framesStillArriving = fresh(copy.speedUpdatedAtMs, now, RPM_TTL_MS);
            // CANBOX goes silent with the ignition off: if the last known speed was ~standstill
            // (or the value was received before), that is "engine off" = 0, not "no data".
            boolean wasStanding = copy.rpmUpdatedAtMs > 0L && !(lastKnownSpeed >= 2.0);
            copy.rpm = (framesStillArriving || wasStanding) ? 0 : -1;
        } else if (copy.rpm > 0 && copy.rpmChangedAtMs > 0L && now - copy.rpmChangedAtMs > RPM_FROZEN_MS
                && !(copy.speedKmh >= 2.0)) { // never while the car is visibly moving
            copy.rpm = 0;
        }
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
            copy.rangeConsumption = Double.NaN;
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

    private static boolean fresh(long timestamp, long now, long ttl) {
        long age = now - timestamp;
        return timestamp > 0L && age >= 0L && age <= ttl;
    }
}
