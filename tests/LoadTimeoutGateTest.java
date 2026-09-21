package de.ronny.pololauncher;
public final class LoadTimeoutGateTest {
    public static void main(String[] args) {
        LoadTimeoutGate gate = new LoadTimeoutGate();
        int schedules=0;
        // start() and resume() are called repeatedly by the 500ms dashboard tick.
        for(int tick=0;tick<100;tick++) for(int call=0;call<2;call++) if(gate.arm()) schedules++;
        if(schedules!=1)throw new AssertionError("Loading timeout postponed by ticks");
        gate.clear();
        if(!gate.arm() || gate.arm())throw new AssertionError("Pause/retry did not rearm exactly once");
        System.out.println("LoadTimeoutGateTest: all cases passed");
    }
}
