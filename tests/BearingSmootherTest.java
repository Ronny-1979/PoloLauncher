package de.ronny.pololauncher;

public class BearingSmootherTest {
    static void check(boolean ok, String msg) { if (!ok) { System.err.println("BearingSmootherTest: FEHLER - " + msg); System.exit(1); } }
    public static void main(String[] a) {
        BearingSmoother s = new BearingSmoother();
        double v = s.next(10, 10, 1000);
        check(Math.abs(v - 10) < 1e-9, "no change when already on target");
        // 90 degree turn over several 1 s fixes: strictly monotone, never above the rate cap, converges
        double prev = 10, total = 0;
        for (int i = 0; i < 12; i++) {
            v = s.next(prev, 100, 1000);
            double step = ((v - prev + 540) % 360) - 180;
            check(step >= 0, "turns the right way");
            check(step <= BearingSmoother.MAX_RATE_DEG_PER_S + 1e-9, "rate cap");
            total += step; prev = v;
        }
        check(Math.abs(total - 90) < 1.5, "converges on the target, got " + total);
        // wrap-around: 350 -> 10 must go through 0 (short way, increasing)
        BearingSmoother w = new BearingSmoother();
        double x = w.next(350, 10, 1000);
        double d = ((x - 350 + 540) % 360) - 180;
        check(d > 0 && d < 20, "short way round across north, d=" + d);
        // first step of a big turn is a part of it, not all of it
        BearingSmoother t = new BearingSmoother();
        double y = t.next(0, 90, 1000);
        check(y > 20 && y < 90, "first step is partial, y=" + y);
        // tiny difference snaps
        BearingSmoother u = new BearingSmoother();
        check(Math.abs(u.next(0, 0.3, 1000) - 0.3) < 1e-9, "tiny differences snap");
        System.out.println("BearingSmootherTest: all cases passed");
    }
}
