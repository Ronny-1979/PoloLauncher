package de.ronny.pololauncher;

/**
 * Snapshot of everything the dashboard actually shows. Fields that were only decoded but never
 * displayed (coolant, OEM range, handbrake, bonnet, CAN consumption) were removed in
 * v0.30.4; the decoders still log them to the diagnostic buffer.
 *
 * All {@code *UpdatedAtMs} stamps use {@link VehicleRepository#now()} (monotonic), never wall time.
 */
public final class VehicleState {
    public int rpm = -1;
    public double speedKmh = Double.NaN;
    public long odometerKm = -1;
    public double fuelLiters = Double.NaN;
    public double fuelPercent = Double.NaN;
    public double instConsumption = Double.NaN;
    public double obdFuelLitersPerHour = Double.NaN;
    public double obdSpeedKmh = Double.NaN;
    public int obdRpm = -1;
    /** Separate adaptive consumption used only for distance-to-empty estimation. */
    public double rangeConsumption = Double.NaN;
    public double outsideC = Double.NaN;
    public double voltage = Double.NaN;
    public long fuelUpdatedAtMs;
    public long rpmUpdatedAtMs;
    /** Last raw rpm value and when it last changed: a running engine never reports the exact same value for seconds. */
    public int rpmRaw = -1;
    public long rpmChangedAtMs;
    public long speedUpdatedAtMs;
    public long instConsumptionUpdatedAtMs;
    public long odometerUpdatedAtMs;
    public long voltageUpdatedAtMs;
    public long outsideUpdatedAtMs;

    // HCT 0x41/3: on this Polo both oil + charging warnings together mean engine stopped.
    public boolean engineStoppedByCan;
    public boolean engineStateKnown;
    public long engineStateUpdatedAtMs;

    public boolean doorFL, doorFR, doorRL, doorRR, trunk;
    public boolean doorsKnown, trunkKnown;
    public long doorsUpdatedAtMs;
    public boolean seatbeltOpen, washerLow;
    public boolean seatbeltKnown, washerKnown;
    public long seatbeltUpdatedAtMs;
    public long washerUpdatedAtMs;

    public String source = "WARTE";
    public long updatedAtMs = 0;

    /** Stores a new rpm sample and tracks when the value last changed (see {@link VehicleRepository}). */
    public void noteRpm(int value, long nowMs) {
        if (value != rpmRaw) { rpmRaw = value; rpmChangedAtMs = nowMs; }
        rpm = value;
        rpmUpdatedAtMs = nowMs;
    }

    public VehicleState copy() {
        VehicleState s = new VehicleState();
        s.rpm=rpm; s.speedKmh=speedKmh; s.odometerKm=odometerKm;
        s.rpmUpdatedAtMs=rpmUpdatedAtMs; s.speedUpdatedAtMs=speedUpdatedAtMs;
        s.rpmRaw=rpmRaw; s.rpmChangedAtMs=rpmChangedAtMs;
        s.fuelLiters=fuelLiters; s.fuelPercent=fuelPercent;
        s.instConsumption=instConsumption; 
        s.rangeConsumption=rangeConsumption;
        s.obdFuelLitersPerHour=obdFuelLitersPerHour; s.obdSpeedKmh=obdSpeedKmh; s.obdRpm=obdRpm;
        s.outsideC=outsideC; s.voltage=voltage;
        s.fuelUpdatedAtMs=fuelUpdatedAtMs; s.instConsumptionUpdatedAtMs=instConsumptionUpdatedAtMs;
        s.voltageUpdatedAtMs=voltageUpdatedAtMs;
        s.odometerUpdatedAtMs=odometerUpdatedAtMs;
        s.outsideUpdatedAtMs=outsideUpdatedAtMs;
        s.engineStoppedByCan=engineStoppedByCan; s.engineStateKnown=engineStateKnown;
        s.engineStateUpdatedAtMs=engineStateUpdatedAtMs;
        s.doorFL=doorFL; s.doorFR=doorFR; s.doorRL=doorRL; s.doorRR=doorRR;
        s.trunk=trunk; s.doorsKnown=doorsKnown; s.trunkKnown=trunkKnown;
        s.doorsUpdatedAtMs=doorsUpdatedAtMs;
        s.seatbeltOpen=seatbeltOpen; s.washerLow=washerLow;
        s.seatbeltKnown=seatbeltKnown; s.washerKnown=washerKnown;
        s.seatbeltUpdatedAtMs=seatbeltUpdatedAtMs;
        s.washerUpdatedAtMs=washerUpdatedAtMs;
        s.source=source; s.updatedAtMs=updatedAtMs;
        return s;
    }
}
