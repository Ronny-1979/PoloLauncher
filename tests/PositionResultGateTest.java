package de.ronny.pololauncher;
public final class PositionResultGateTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        PositionResultGate gate = new PositionResultGate();
        long first = gate.request();
        check(gate.complete(first) && !gate.fallback(), "Fast match suppresses duplicate raw fallback");
        long slow = gate.request(); long latest = gate.request();
        check(!gate.complete(slow), "Superseded match must not move camera backwards");
        check(gate.fallback() && !gate.complete(latest), "Timeout displays latest raw position and rejects late correction");
        for(int i=0;i<100;i++) { gate.request(); check(gate.fallback(), "GPS continues while reader is stuck"); }
        long cancelled = gate.request(); gate.cancel();
        check(!gate.complete(cancelled) && !gate.fallback(), "Closing/disabling cancels queued delivery");
        check(gate.complete(gate.request()), "Matching recovers after a cancellation");
        System.out.println("PositionResultGateTest: all cases passed");
    }
}
