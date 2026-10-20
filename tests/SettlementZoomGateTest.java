package de.ronny.pololauncher;

public final class SettlementZoomGateTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SettlementZoomGate gate = new SettlementZoomGate();

        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 0, 0, false)
                        == SettlementZoomGate.Signal.OUTSIDE,
                "startup can establish outside");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, true, 1_000, 20, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "look-ahead enters settlement before the current point crosses the boundary");

        // Short OSM holes and isolated outside hits inside town must never make the zoom flutter.
        check(gate.update(SettlementZoomGate.Signal.UNKNOWN, false, 5_000, 25, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "unknown OSM gap keeps settlement");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 9_000, 30, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "minimum hold blocks an early false exit");

        // After hold: require three consecutive clear outside observations AND >=120 m travel.
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 12_000, 30, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "first stable outside observation only starts exit candidate");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 14_000, 55, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "second observation still holds inner zoom");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 16_000, 55, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "three observations without 120 m still hold");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 18_000, 20, false)
                        == SettlementZoomGate.Signal.OUTSIDE,
                "clear outside streak plus 130 m finally zooms out");

        // An UNKNOWN between outside observations breaks the streak.
        gate.reset();
        gate.update(SettlementZoomGate.Signal.SETTLEMENT, false, 0, 0, false);
        gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 11_000, 40, false);
        gate.update(SettlementZoomGate.Signal.UNKNOWN, false, 13_000, 40, false);
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 15_000, 80, false)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "map gap breaks outside streak instead of causing 15-13-15 flutter");

        // Direct mode still has hysteresis, just a shorter exit.
        gate.reset();
        gate.update(SettlementZoomGate.Signal.SETTLEMENT, false, 0, 0, true);
        gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 7_000, 20, true);
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 8_000, 30, true)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "direct mode does not exit before enough distance");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 9_000, 35, true)
                        == SettlementZoomGate.Signal.OUTSIDE,
                "direct mode exits after clear shorter confirmation");

        System.out.println("SettlementZoomGateTest: predictive entry and anti-flutter exit passed");
    }
}
