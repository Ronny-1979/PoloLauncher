package de.ronny.pololauncher;

public final class SpeedCameraDecisionTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(SpeedCameraDecision.eligible(500, 5, 0, 13.9f, 2, true),
                "camera 500 m ahead on same road is eligible");
        check(!SpeedCameraDecision.eligible(720, 0, 0, 13.9f, 0, true),
                "camera beyond 700 m is ignored");
        check(!SpeedCameraDecision.eligible(300, 80, 0, 13.9f, 0, true),
                "side-road camera outside ahead cone is ignored");
        check(!SpeedCameraDecision.eligible(300, 10, 0, 13.9f, 90, true),
                "perpendicular-road camera is ignored");
        check(SpeedCameraDecision.eligible(300, 0, 0, 13.9f, 180, true),
                "same road stored in reverse OSM node order remains eligible");
        check(SpeedCameraDecision.roadAxisDifference(0, 180) == 0d,
                "road-axis comparison treats 0 and 180 degrees as the same road");
        check(SpeedCameraDecision.eligible(250, 0, 0, 13.9f,
                        new double[] {90, 180}),
                "intersection camera accepts any matching equally-near road axis");
        check(!SpeedCameraDecision.eligible(250, 0, 0, 13.9f,
                        new double[] {90, 92}),
                "intersection camera rejects when none of the nearby road axes match");

        check(!SpeedCameraDecision.eligible(300, 0, 0, 1.0f, 0, true),
                "stationary/creeping vehicle does not warn");
        check(SpeedCameraDecision.eligible(150, 350, 0, 10f, Double.NaN, false),
                "unknown road bearing can still use strong ahead evidence");

        System.out.println("SpeedCameraDecisionTest: distance, heading and road-axis filters passed");
    }
}
