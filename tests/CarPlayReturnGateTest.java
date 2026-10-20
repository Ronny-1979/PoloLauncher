package de.ronny.pololauncher;

public final class CarPlayReturnGateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        CarPlayReturnGate gate = new CarPlayReturnGate();

        check(gate.update(0, true, false) == CarPlayReturnGate.Action.SCHEDULE,
                "automatic CarPlay foreground schedules one return");
        check(gate.update(1000, true, false) == CarPlayReturnGate.Action.NONE,
                "same foreground episode does not schedule twice");
        check(gate.confirmReturn(2_000, true, false, false) == CarPlayReturnGate.Result.WAIT,
                "not connected: keeps waiting");
        check(gate.confirmReturn(5_000, true, false, true) == CarPlayReturnGate.Result.WAIT,
                "connected but not yet stable: keeps waiting");
        check(gate.confirmReturn(6_000, true, false, false) == CarPlayReturnGate.Result.WAIT,
                "signal dropped: stable timer restarts");
        check(gate.confirmReturn(7_000, true, false, true) == CarPlayReturnGate.Result.WAIT,
                "connected again: timer starts");
        check(gate.confirmReturn(10_000, true, false, true) == CarPlayReturnGate.Result.RETURN,
                "return fires when CarPlay stays connected for 3 s");
        check(!gate.lastReturnTimedOut(), "normal return is not a timeout");
        check(gate.update(6000, true, false) == CarPlayReturnGate.Action.NONE,
                "handled episode does not fight CarPlay repeatedly");

        check(gate.update(7000, false, false) == CarPlayReturnGate.Action.NONE,
                "leaving CarPlay starts rearm timer");
        check(gate.update(66_999, false, false) == CarPlayReturnGate.Action.NONE,
                "not rearmed before one full minute absent");
        check(gate.update(67_000, false, false) == CarPlayReturnGate.Action.NONE,
                "rearms after one minute absent");
        check(gate.update(67_001, true, false) == CarPlayReturnGate.Action.SCHEDULE,
                "new automatic session can schedule again");

        gate.reset();
        check(gate.update(0, true, true) == CarPlayReturnGate.Action.NONE,
                "manual CarPlay launch never schedules auto-return");
        check(gate.confirmReturn(1_000, true, true, true) == CarPlayReturnGate.Result.ABORT,
                "manual launch cannot be confirmed as auto-return");
        check(gate.update(10_000, true, false) == CarPlayReturnGate.Action.NONE,
                "manual foreground episode remains handled even after suppression time changes");

        gate.reset();
        check(gate.update(0, true, false) == CarPlayReturnGate.Action.SCHEDULE,
                "pending return can be scheduled");
        check(gate.update(1_000, false, false) == CarPlayReturnGate.Action.CANCEL,
                "return is cancelled if CarPlay leaves before delay");

        gate.reset();
        check(gate.update(0, true, false) == CarPlayReturnGate.Action.SCHEDULE, "schedule for timeout case");
        check(gate.confirmReturn(89_999, true, false, false) == CarPlayReturnGate.Result.WAIT,
                "never connected: waits until the limit");
        check(gate.confirmReturn(90_000, true, false, false) == CarPlayReturnGate.Result.RETURN,
                "never connected: returns after the time limit");
        check(gate.lastReturnTimedOut(), "timeout return is flagged");
        gate.reset();
        check(gate.update(0, true, false) == CarPlayReturnGate.Action.SCHEDULE, "schedule for leave case");
        check(gate.confirmReturn(3_000, false, false, true) == CarPlayReturnGate.Result.ABORT,
                "CarPlay left the foreground: abort without returning");

        System.out.println("CarPlayReturnGateTest: all cases passed");
    }
}
