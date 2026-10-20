package de.ronny.pololauncher;

public final class SpeedLimitGateTest {
    private static void eq(int expected, int actual, String message) {
        if (expected != actual)
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        SpeedLimitGate gate = new SpeedLimitGate();

        eq(50, gate.update(50, 0), "first confident value shows immediately");
        eq(50, gate.update(70, 1_000), "one crossing-road sample cannot change sign");
        eq(70, gate.update(70, 2_000), "second matching sample confirms new limit");

        eq(70, gate.update(SpeedLimitValue.UNKNOWN, 4_000),
                "short map gap keeps last known limit");
        eq(70, gate.update(SpeedLimitValue.UNKNOWN, 7_999),
                "limit is held for six seconds after known evidence");
        eq(SpeedLimitValue.UNKNOWN, gate.update(SpeedLimitValue.UNKNOWN, 8_001),
                "long unknown gap hides stale sign");

        gate.reset();
        eq(100, gate.update(100, 0), "numeric limit restored");
        eq(100, gate.update(SpeedLimitValue.UNRESTRICTED, 1_000),
                "unrestricted also needs confirmation");
        eq(SpeedLimitValue.UNRESTRICTED, gate.update(SpeedLimitValue.UNRESTRICTED, 2_000),
                "confirmed unrestricted hides numeric sign cleanly");

        System.out.println("SpeedLimitGateTest: anti-flicker and stale handling passed");
    }
}
