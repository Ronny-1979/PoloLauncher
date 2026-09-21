package de.ronny.pololauncher;

public final class DabStartupGateTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        DabStartupGate gate = new DabStartupGate();
        check(!gate.claim(false), "Disabled switch never launches");
        for (int i = 0; i < 100; i++)
            check(!gate.claim(true, false) && gate.pending(true), "Offline wait never consumes startup attempt");
        check(!gate.pending(false), "Disabling cancels waiting");
        check(gate.claim(true), "Enabling permits one startup attempt");
        check(!gate.claim(true), "Returning home cannot create a launch loop");
        check(!gate.pending(true), "No Internet polling after launch attempt");
        check(!gate.claim(false) && !gate.claim(true), "Toggling does not restart this process");
        check(new DabStartupGate().claim(true), "New process may launch again");
        gate.screenOff();
        check(gate.pending(true) && !gate.claim(true, false), "Standby rearms but still waits for Internet");
        check(gate.claim(true, true), "Wake permits another launch without process restart");
        check(!gate.claim(true, true), "Wake does not cause repeated home launches");
        System.out.println("DabStartupGateTest: all cases passed");
    }
}
