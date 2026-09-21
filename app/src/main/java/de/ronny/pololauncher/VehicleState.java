package de.ronny.pololauncher;

/**
 * Snapshot of everything the dashboard actually shows. Fields that were only decoded but never
 * displayed (CAN speed, coolant, OEM range, handbrake, bonnet, CAN consumption) were removed in
 * v0.30.4; the decoders still log them to the diagnostic buffer.
 *
 * All {@code *UpdatedAtMs} stamps use {@link VehicleRepository#now()} (monotonic), never wall time.
 */
public final class VehicleState {
    public int rpm = -1;
    public long odometerKm = -1;
    public double fuelLiters = Double.NaN;
    public double fuelPercent = Double.NaN;
    public double instConsumption = Double.NaN;
    public double obdFuelLitersPerHour = Double.NaN;
    public double obdSpeedKmh = Double.NaN;
    public int obdRpm = -1;
    public double avgConsumption = Double.NaN;
    public double outsideC = Double.NaN;
    public double voltage = Double.NaN;
    public long fuelUpdatedAtMs;
    public long rpmUpdatedAtMs;
    public long instConsumptionUpdatedAtMs;
    public long odometerUpdatedAtMs;
    public long voltageUpdatedAtMs;
    public long outsideUpdatedAtMs;

    public boolean doorFL, doorFR, doorRL, doorRR, trunk;
    public boolean doorsKnown, trunkKnown;
    public long doorsUpdatedAtMs;
    public boolean seatbeltOpen, washerLow;
    public boolean seatbeltKnown, washerKnown;
    public long seatbeltUpdatedAtMs;
    public long washerUpdatedAtMs;

    public String source = "WARTE";
    public long updatedAtMs = 0;

    public VehicleState copy() {
        VehicleState s = new VehicleState();
        s.rpm=rpm; s.odometerKm=odometerKm; s.rpmUpdatedAtMs=rpmUpdatedAtMs;
        s.fuelLiters=fuelLiters; s.fuelPercent=fuelPercent;
        s.instConsumption=instConsumption; s.avgConsumption=avgConsumption;
        s.obdFuelLitersPerHour=obdFuelLitersPerHour; s.obdSpeedKmh=obdSpeedKmh; s.obdRpm=obdRpm;
        s.outsideC=outsideC; s.voltage=voltage;
        s.fuelUpdatedAtMs=fuelUpdatedAtMs; s.instConsumptionUpdatedAtMs=instConsumptionUpdatedAtMs;
        s.voltageUpdatedAtMs=voltageUpdatedAtMs;
        s.odometerUpdatedAtMs=odometerUpdatedAtMs;
        s.outsideUpdatedAtMs=outsideUpdatedAtMs;
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
