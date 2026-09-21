package de.ronny.pololauncher;

import android.content.Context;
import java.util.Set;

/** Keeps the passive HCT receivers/services alive while dashboard/diagnostics are open. */
public final class VendorRuntime {
    private static final SystemCanReceiver RECEIVER = new SystemCanReceiver();
    private static int refs;

    private VendorRuntime() {}

    public static synchronized void acquire(Context c) {
        refs++;
        if (refs == 1) {
            Context app = c.getApplicationContext();
            RECEIVER.start(app);
            HctCanbusBinderRuntime.acquire(app);
            HctOemAutoPoller.start(app);
            Obd2Runtime.acquire(app);
        }
    }

    public static synchronized void release(Context c) {
        refs = Math.max(0, refs - 1);
        if (refs == 0) {
            Context app = c.getApplicationContext();
            Obd2Runtime.release(app);
            HctOemAutoPoller.stop();
            HctCanbusBinderRuntime.release(app);
            RECEIVER.stop(app);
        }
    }

    public static synchronized void addActions(Context c, Set<String> actions) {
        RECEIVER.addActions(c.getApplicationContext(), actions);
    }

    public static synchronized int actionCount() { return RECEIVER.actionCount(); }
    public static synchronized String syncSummary() { return RECEIVER.syncSummary(); }
    public static synchronized long syncPacketCount() { return RECEIVER.syncPacketCount(); }
    public static synchronized int lastDoorMask() { return RECEIVER.lastDoorMask(); }
}
