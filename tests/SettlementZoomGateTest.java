package de.ronny.pololauncher;

public final class SettlementZoomGateTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SettlementZoomGate gate = new SettlementZoomGate();

        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 0, 0)
                        == SettlementZoomGate.Signal.OUTSIDE,
                "startup can establish outside");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, true, 1_000, 20)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "look-ahead enters settlement before the current point crosses the boundary");

        // Short OSM holes and isolated outside hits inside town must never make the zoom flutter.
        check(gate.update(SettlementZoomGate.Signal.UNKNOWN, false, 5_000, 25)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "unknown OSM gap keeps settlement");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 5_500, 30)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "minimum hold (6 s) blocks an early false exit");

        // After the hold: two consecutive clear outside observations AND >= 60 m travel.
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 8_000, 30)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "first outside observation only starts the exit candidate");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 9_000, 25)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "second observation without 60 m still holds the inner zoom");
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 10_000, 40)
                        == SettlementZoomGate.Signal.OUTSIDE,
                "clear outside streak plus 65 m zooms out");

        // An UNKNOWN between outside observations breaks the streak.
        gate.reset();
        gate.update(SettlementZoomGate.Signal.SETTLEMENT, false, 0, 0);
        gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 7_000, 40);
        gate.update(SettlementZoomGate.Signal.UNKNOWN, false, 8_000, 40);
        check(gate.update(SettlementZoomGate.Signal.OUTSIDE, false, 9_000, 80)
                        == SettlementZoomGate.Signal.SETTLEMENT,
                "map gap breaks outside streak instead of causing 15-13-15 flutter");

        System.out.println("SettlementZoomGateTest: predictive entry and anti-flutter exit passed");
    }
}
