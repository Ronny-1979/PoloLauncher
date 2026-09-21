package de.ronny.pololauncher;

public final class ConsumptionDisplayTest {
    public static void main(String[] args) {
        android.os.SystemClock.now = 1_000_000L; // monotonic base used for freshness stamps
        VehicleState s = new VehicleState();
        s.instConsumptionUpdatedAtMs = VehicleRepository.now();
        s.obdSpeedKmh = 0; s.obdRpm = 828; s.obdFuelLitersPerHour = 0.8;
        s.avgConsumption = 7.8;
        check("0,8 l".equals(ConsumptionDisplay.instantaneous(s)), "Running idle");
        check("0,8 l".equals(ConsumptionDisplay.instantaneous(s.copy())), "Snapshot fields copied");
        s.rpm = 0; s.rpmUpdatedAtMs = s.instConsumptionUpdatedAtMs;
        check("--".equals(ConsumptionDisplay.instantaneous(s.copy())), "Fresh CAN engine-off overrides cached OBD idle");
        s.rpmUpdatedAtMs = s.instConsumptionUpdatedAtMs - 1;
        check("0,8 l".equals(ConsumptionDisplay.instantaneous(s)), "Old CAN zero cannot hide newer running OBD sample");
        s.rpmUpdatedAtMs = 0;
        s.obdSpeedKmh = 2;
        check("0,8 l".equals(ConsumptionDisplay.instantaneous(s)), "Crawling");
        s.obdSpeedKmh = 3; s.instConsumption = 26.7;
        check("26,7 l".equals(ConsumptionDisplay.instantaneous(s)), "Driving threshold");
        s.obdSpeedKmh = 0; s.obdRpm = 0;
        check("--".equals(ConsumptionDisplay.instantaneous(s)), "Engine off hides hourly rate");
        s.obdSpeedKmh = 20; s.instConsumption = 4.5;
        check("--".equals(ConsumptionDisplay.instantaneous(s)), "Engine off also hides driving figure");
        s.obdRpm = -1;
        check("--".equals(ConsumptionDisplay.instantaneous(s)), "Unknown engine state never displays consumption");
        s.obdRpm = 1200;
        check("4,5 l".equals(ConsumptionDisplay.instantaneous(s)), "Running engine restores driving figure");
        s.obdSpeedKmh = 0;
        s.obdRpm = 828; s.obdFuelLitersPerHour = Double.NaN; s.instConsumption = Double.NaN;
        check("--".equals(ConsumptionDisplay.instantaneous(s)), "Missing fuel");
        s.obdFuelLitersPerHour = 0.8; s.instConsumptionUpdatedAtMs -= 16000;
        check("--".equals(ConsumptionDisplay.instantaneous(s)), "Stale sample");
        synchronized (VehicleRepository.class) {
            VehicleState live = VehicleRepository.mutable();
            live.obdFuelLitersPerHour = 0.8; live.obdSpeedKmh = 0; live.obdRpm = 828;
            live.instConsumptionUpdatedAtMs = s.instConsumptionUpdatedAtMs;
        }
        check(Double.isNaN(VehicleRepository.snapshot().obdFuelLitersPerHour), "Repository expires hourly rate");
        check(s.avgConsumption == 7.8, "Formatting never changes average");
        System.out.println("ConsumptionDisplayTest OK");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
