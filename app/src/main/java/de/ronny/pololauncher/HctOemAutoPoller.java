package de.ronny.pololauncher;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Keeps the OEM 0x41 telemetry alive while Polo CAN is open.
 *
 * The original com.microntek.controlinfo application polls the CANBOX with read-only
 * command 0x90 and payload [0x41, mode]. The intervals recovered from the OEM APK are
 * roughly mode 1 = 1000 ms, mode 2 = 400 ms and mode 3 = 1500 ms.
 *
 * v0.15 uses the same vendor Binder service as android.microntek.canbus and never opens
 * /dev/ttyV0 itself. This prevents the dashboard from depending on another OEM screen
 * having been opened first. Only read requests are sent; no CAN configuration is changed.
 */
public final class HctOemAutoPoller {
    private static final long MODE1_MS = 1000L;
    private static final long MODE2_MS = 500L;   // close to OEM 400 ms, slightly gentler
    private static final long MODE3_MS = 1500L;
    private static final long TICK_MS = 100L;

    private static HandlerThread thread;
    private static Handler handler;
    private static Context appContext;
    private static boolean running;
    private static boolean enabled = true;
    private static long generation;
    private static Runnable tick;

    private static long next1, next2, next3;
    private static long sent1, sent2, sent3;
    private static long waitingForBinder;
    private static long errors;
    private static long lastErrorLogAt;
    private static String lastError = "";

    private HctOemAutoPoller() {}

    public static synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (running) return;
        running = true;
        final long token = ++generation;
        thread = new HandlerThread("PoloLauncher-OemPoll");
        thread.start();
        handler = new Handler(thread.getLooper());
        long now = SystemClock.uptimeMillis();
        next1 = now + 250L;
        next2 = now + 100L;
        next3 = now + 500L;
        DiagLog.add("AUTO-OEM-POLL gestartet: 0x41/1 ~1s, 0x41/2 ~0,5s, 0x41/3 ~1,5s (nur Leseabfragen über HCT-AIDL)");
        tick = new Runnable() {
            @Override public void run() {
                Context c;
                synchronized (HctOemAutoPoller.class) {
                    if (!running || generation != token) return;
                    c = appContext;
                }
                if (c == null) return;

                try {
                    HctCanbusBinderRuntime.ensureBound(c);
                    if (isEnabled()) {
                        long now = SystemClock.uptimeMillis();
                        if (!HctCanbusBinderRuntime.isConnected()) {
                            synchronized (HctOemAutoPoller.class) { waitingForBinder++; }
                        } else {
                            if (now >= next2) { send(c, 2); next2 = now + MODE2_MS; }
                            if (now >= next1) { send(c, 1); next1 = now + MODE1_MS; }
                            if (now >= next3) { send(c, 3); next3 = now + MODE3_MS; }
                        }
                    }
                } catch (Throwable t) {
                    onError(t);
                }

                Handler h;
                synchronized (HctOemAutoPoller.class) {
                    if (!running || generation != token) return;
                    h = handler;
                }
                if (h != null) h.postDelayed(this, TICK_MS);
            }
        };
        handler.post(tick);
    }

    public static synchronized void stop() {
        running = false;
        generation++;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) {
            try { thread.quitSafely(); } catch (Throwable ignored) {}
        }
        handler = null;
        thread = null;
        appContext = null;
        tick = null;
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        DiagLog.add("AUTO-OEM-POLL manuell: " + (value ? "AN" : "AUS"));
        if (value) {
            long now = SystemClock.uptimeMillis();
            next1 = next2 = next3 = now;
        }
    }

    public static synchronized boolean isEnabled() { return enabled; }
    public static synchronized long mode2Sent() { return sent2; }
    public static synchronized long errors() { return errors; }

    public static synchronized String summary() {
        return String.format(Locale.GERMANY,
                "%s M1=%d M2=%d M3=%d wait=%d err=%d%s",
                enabled ? "AN" : "AUS", sent1, sent2, sent3, waitingForBinder, errors,
                lastError.isEmpty() ? "" : " [" + lastError + "]");
    }

    private static void send(Context c, int mode) {
        try {
            boolean ok = HctCanbusBinderRuntime.sendCanbusData(c, HctCanbusBinderRuntime.buildCarInfoQuery(mode));
            if (!ok) {
                synchronized (HctOemAutoPoller.class) { waitingForBinder++; }
                return;
            }
            synchronized (HctOemAutoPoller.class) {
                if (mode == 1) sent1++;
                else if (mode == 2) sent2++;
                else if (mode == 3) sent3++;
            }
        } catch (Throwable t) {
            onError(t);
        }
    }

    private static void onError(Throwable t) {
        String e = HctCanbusBinderRuntime.root(t);
        long now = SystemClock.uptimeMillis();
        synchronized (HctOemAutoPoller.class) {
            errors++;
            lastError = e;
            if (now - lastErrorLogAt >= 5000L) {
                lastErrorLogAt = now;
                DiagLog.add("AUTO-OEM-POLL FEHLER: " + e);
            }
        }
    }
}
