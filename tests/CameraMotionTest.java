package de.ronny.pololauncher;

public final class CameraMotionTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    public static void main(String[] a) {
        double[] north = CameraMotion.project(51.0, 13.0, 20f, 0f, true, 1000L);
        check(Math.abs((north[0] - 51.0) * 111_320d - 20d) < 0.1, "20 m/s north for 1 s = 20 m");
        check(Math.abs(north[1] - 13.0) < 1e-9, "no lateral drift heading north");
        double[] east = CameraMotion.project(51.0, 13.0, 20f, 90f, true, 1000L);
        check(Math.abs(east[0] - 51.0) < 1e-9 && east[1] > 13.0, "east moves longitude only");
        double[] slow = CameraMotion.project(51.0, 13.0, 1f, 0f, true, 1000L);
        check(slow[0] == 51.0 && slow[1] == 13.0, "no lead when nearly stopped");
        double[] untrusted = CameraMotion.project(51.0, 13.0, 30f, 0f, false, 1000L);
        check(untrusted[0] == 51.0 && untrusted[1] == 13.0, "no lead without trusted bearing");
        double[] fast = CameraMotion.project(51.0, 13.0, 60f, 0f, true, 2000L);
        check(Math.abs((fast[0] - 51.0) * 111_320d - CameraMotion.MAX_LEAD_METRES) < 0.1, "lead is capped");
        double[] nan = CameraMotion.project(51.0, 13.0, Float.NaN, 0f, true, 1000L);
        check(nan[0] == 51.0, "NaN speed ignored");
        check(CameraMotion.isJitter(1.2f, 0f), "standing: 1 m wobble is jitter");
        check(CameraMotion.isJitter(1.2f, Float.NaN), "unknown speed: small wobble is jitter");
        check(!CameraMotion.isJitter(1.3f, 16.7f), "driving 60 km/h: fix 1.3 m from the lead-shifted camera still moves it");
        check(!CameraMotion.isJitter(0.5f, 8f), "driving: never swallowed");
        check(!CameraMotion.isJitter(5f, 0f), "standing: a real 5 m step moves the camera");
        System.out.println("CameraMotionTest: camera lead projection bounded and trust-gated");
    }
}
