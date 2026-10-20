package de.ronny.pololauncher;

public final class StandbyWakeDetectorTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        StandbyWakeDetector detector = new StandbyWakeDetector();
        check(!detector.heartbeat(1_000), "first heartbeat only establishes baseline");
        check(!detector.heartbeat(1_500), "normal 500 ms heartbeat is not standby");
        check(!detector.heartbeat(10_000), "ordinary delay below threshold is ignored");
        check(detector.heartbeat(30_000), "long freeze without SCREEN_ON is quick-start wake");
        check(!detector.heartbeat(30_500), "same wake is emitted only once");

        detector = new StandbyWakeDetector();
        check(!detector.heartbeat(1_000), "new detector baseline");
        detector.screenOn(30_000);
        check(!detector.heartbeat(30_200),
                "normal Android SCREEN_ON suppresses duplicate fallback wake");
        check(!detector.heartbeat(30_700), "normal heartbeat after SCREEN_ON remains quiet");

        detector = new StandbyWakeDetector();
        detector.reset(100_000);
        check(!detector.heartbeat(100_500), "service restart/reset cannot look like standby");

        System.out.println("StandbyWakeDetectorTest: all cases passed");
    }
}
