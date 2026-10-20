package de.ronny.pololauncher;

public final class RpmFrozenTest {
    static void check(boolean ok, String msg) { if (!ok) { System.err.println("RpmFrozenTest: FEHLER - " + msg); System.exit(1); } }
    public static void main(String[] a) {
        android.os.SystemClock.now = 100_000L;
        long now = VehicleRepository.now();
        // engine off, CANBOX keeps repeating 856 rpm: the same value for > 4 s -> 0
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.engineStateKnown = false;
            s.speedUpdatedAtMs = now; s.speedKmh = 0;
            s.rpmRaw = -1; s.rpmChangedAtMs = 0L;
            s.noteRpm(856, now - 6_000L);
            s.noteRpm(856, now - 3_000L);
            s.noteRpm(856, now);
        }
        check(VehicleRepository.snapshot().rpm == 0, "frozen 856 rpm shows 0");
        // idling engine jitters: stays visible
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.rpmRaw = -1; s.rpmChangedAtMs = 0L;
            s.noteRpm(850, now - 6_000L);
            s.noteRpm(853, now - 2_000L);
            s.noteRpm(851, now);
        }
        check(VehicleRepository.snapshot().rpm == 851, "jittering rpm stays shown, got " + VehicleRepository.snapshot().rpm);
        // a fresh change after a long constant stretch is shown again (engine started)
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.rpmRaw = -1; s.rpmChangedAtMs = 0L;
            s.noteRpm(856, now - 9_000L);
            s.noteRpm(1200, now);
        }
        check(VehicleRepository.snapshot().rpm == 1200, "restart shows the new rpm");
        // frozen value but the car is moving (speed >= 2 km/h): never forced to 0
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.speedKmh = 60; s.speedUpdatedAtMs = now;
            s.rpmRaw = -1; s.rpmChangedAtMs = 0L;
            s.noteRpm(2000, now - 6_000L);
            s.noteRpm(2000, now);
        }
        check(VehicleRepository.snapshot().rpm == 2000, "moving car keeps constant rpm");
        // CAN stream silent (ignition off), last speed 0 -> 0 rpm, not "--"
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.speedKmh = 0; s.speedUpdatedAtMs = now - 20_000L;
            s.rpm = 850; s.rpmUpdatedAtMs = now - 20_000L;
            s.engineStateKnown = false;
        }
        check(VehicleRepository.snapshot().rpm == 0, "silent CAN at standstill shows 0");
        // silent CAN while last speed was 80 km/h (dropout) -> unknown
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.speedKmh = 80; s.speedUpdatedAtMs = now - 20_000L;
        }
        check(VehicleRepository.snapshot().rpm == -1, "dropout while driving stays unknown");
        System.out.println("RpmFrozenTest: frozen rpm -> 0, jitter and restart unaffected");
    }
}
