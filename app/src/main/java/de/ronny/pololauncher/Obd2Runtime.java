package de.ronny.pololauncher;

import android.content.Context;

/** Keeps the OBD2 client alive exactly as long as VendorRuntime keeps the CAN receivers alive. */
final class Obd2Runtime {
    private static final Obd2Client CLIENT = new Obd2Client();
    private static int refs;

    private Obd2Runtime() {}

    static synchronized void acquire(Context context) {
        refs++;
        if (refs == 1) CLIENT.start(context.getApplicationContext());
    }

    static synchronized void release(Context context) {
        refs = Math.max(0, refs - 1);
        if (refs == 0) CLIENT.stop();
    }

    /** Called when the head unit wakes from standby: drop the old Bluetooth link and reconnect now. */
    static void requestReconnect() { CLIENT.requestReconnect(); }
    static synchronized boolean isConnected() { return CLIENT.isConnected(); }
    static synchronized String status() { return CLIENT.status(); }
    static synchronized String diagnostics(Context context) { return CLIENT.diagnostics(context); }
    /** Persisted rolling 100-km driving profile (dashboard "Ø 100 KM" and range). */
    static synchronized double rangeAverage(Context context) { return CLIENT.rangeAverage(context); }
}
