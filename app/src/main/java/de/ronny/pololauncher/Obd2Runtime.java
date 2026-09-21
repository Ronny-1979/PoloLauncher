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

    /** {litres, km} accumulated by the OBD average (running or persisted). */
    static synchronized double[] totals(Context context) { return CLIENT.totals(context); }
    static synchronized void calibrate(Context context, double windowRatio) { CLIENT.calibrate(context, windowRatio); }
    static synchronized void resetCalibration(Context context) { CLIENT.resetCalibration(context); }
    static synchronized double veScale(Context context) { return CLIENT.veScale(context); }
    static synchronized boolean isConnected() { return CLIENT.isConnected(); }
    static synchronized String status() { return CLIENT.status(); }
    static synchronized String diagnostics(Context context) { return CLIENT.diagnostics(context); }
    static synchronized void resetAverageConsumption(Context context) { CLIENT.resetAverageConsumption(context); }
    static synchronized double displayAverage(Context context) { return CLIENT.displayAverage(context); }
    static synchronized double reliableDisplayAverage(Context context) { return CLIENT.reliableDisplayAverage(context); }
    /** Cumulative consumption baseline, not a new measurement or freshness signal. */
    static synchronized double rangeAverage(Context context) { return CLIENT.displayAverage(context); }
}
