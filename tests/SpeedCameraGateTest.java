package de.ronny.pololauncher;

public final class SpeedCameraGateTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SpeedCameraGate gate = new SpeedCameraGate();
        SpeedCameraGate.Result cam = new SpeedCameraGate.Result(123L, 420,
                4711L, 51.0503d, 13.73755d, "Verkehrsdatenbank");

        SpeedCameraGate.Result shown = gate.update(cam, 0);
        check(shown.present(), "camera shows immediately");
        check(shown.osmId == 4711L, "camera metadata keeps OSM id through gate");
        check("Verkehrsdatenbank".equals(shown.source), "camera metadata keeps source through gate");
        check(gate.update(SpeedCameraGate.Result.none(), 2_000).present(),
                "short GPS/map gap keeps current warning");
        check(!gate.update(SpeedCameraGate.Result.none(), 3_001).present(),
                "warning clears after the short hold");
        gate.update(cam, 10_000);
        gate.reset();
        check(!gate.update(SpeedCameraGate.Result.none(), 10_100).present(), "reset hides camera even inside the hold window");

        System.out.println("SpeedCameraGateTest: short-gap hold and stale clearing passed");
    }
}
