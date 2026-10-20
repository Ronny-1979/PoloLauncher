package de.ronny.pololauncher;

public final class DabReturnGateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError("DabReturnGateTest: " + message);
    }

    public static void main(String[] args) {
        DabReturnGate g = new DabReturnGate();
        check(g.update(0, true, true, true) == DabReturnGate.Result.ABORT, "not started: abort");

        g.start(0);
        check(g.update(1_000, true, false, false) == DabReturnGate.Result.WAIT, "DAB not yet in front: wait");
        check(g.update(2_000, true, true, false) == DabReturnGate.Result.WAIT, "in front, not playing: wait");
        check(g.update(3_000, true, true, true) == DabReturnGate.Result.WAIT, "playing just started: wait");
        check(g.update(4_999, true, true, true) == DabReturnGate.Result.WAIT, "playing less than 2 s: wait");
        check(g.update(5_000, true, true, true) == DabReturnGate.Result.RETURN, "playing 2 s in front: return");
        check(!g.timedOut(), "normal return is no timeout");

        g.start(0);
        check(g.update(1_000, true, true, true) == DabReturnGate.Result.WAIT, "playing starts");
        check(g.update(2_000, true, true, false) == DabReturnGate.Result.WAIT, "playing stops: timer restarts");
        check(g.update(3_500, true, true, true) == DabReturnGate.Result.WAIT, "playing again");
        check(g.update(5_500, true, true, true) == DabReturnGate.Result.RETURN, "stable again: return");

        g.start(0);
        check(g.update(1_000, true, true, false) == DabReturnGate.Result.WAIT, "seen in front");
        check(g.update(5_000, true, false, true) == DabReturnGate.Result.ABORT, "user left DAB: abort, no forced return");

        g.start(0);
        check(g.update(9_999, true, false, false) == DabReturnGate.Result.WAIT, "never in front: still in grace");
        check(g.update(10_000, true, false, false) == DabReturnGate.Result.RETURN, "never in front: old behaviour");

        g.start(0);
        check(g.update(44_999, true, true, false) == DabReturnGate.Result.WAIT, "no playing: waits");
        check(g.update(45_000, true, true, false) == DabReturnGate.Result.RETURN, "no playing: returns after limit");
        check(g.timedOut(), "limit return is flagged");

        g.start(0);
        check(g.update(1_000, false, false, true) == DabReturnGate.Result.WAIT, "foreground unknown, playing starts");
        check(g.update(3_000, false, false, true) == DabReturnGate.Result.RETURN, "foreground unknown: playing alone suffices");

        System.out.println("DabReturnGateTest: all cases passed");
    }
}
