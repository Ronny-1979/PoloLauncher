package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Finds out WHY Android shows "Polo Launcher reagiert nicht". A daemon thread posts a heartbeat
 * to the main thread twice a second; if the main thread does not answer for 2.5 s, the watchdog
 * records what the main thread is executing right now (its stack) in the diagnostic log and keeps
 * the last report in SharedPreferences, so it survives the app being closed after the ANR dialog.
 * No effect on the UI: it only reads the main thread's stack from another thread.
 */
final class UiStallWatchdog {
    private static final String PREFS = "ui_stall";
    private static final long CHECK_MS = 500L;
    private static final long STALL_MS = 2_500L;
    private static final int MAX_REPORTS_PER_STALL = 3;
    private static final int FRAMES = 16;

    private static boolean started;
    private static volatile long lastBeat;

    private UiStallWatchdog() {}

    static synchronized void start(Context context) {
        if (started) return;
        started = true;
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final Thread mainThread = Looper.getMainLooper().getThread();
        lastBeat = SystemClock.elapsedRealtime();
        final Runnable beat = () -> lastBeat = SystemClock.elapsedRealtime();
        Thread watchdog = new Thread(() -> {
            int reports = 0;
            boolean stalled = false;
            long stallStart = 0L;
            while (!Thread.currentThread().isInterrupted()) {
                main.post(beat);
                try { Thread.sleep(CHECK_MS); }
                catch (InterruptedException e) { return; }
                long late = SystemClock.elapsedRealtime() - lastBeat;
                if (late >= STALL_MS) {
                    if (!stalled) { stalled = true; stallStart = SystemClock.elapsedRealtime() - late; reports = 0; }
                    long expected = STALL_MS * (reports + 1);
                    if (reports < MAX_REPORTS_PER_STALL && late >= expected) {
                        reports++;
                        StackTraceElement[] stack = mainThread.getStackTrace();
                        // Main thread parked in its message loop = idle (e.g. process frozen in
                        // standby), not blocked: no report.
                        if (stack.length > 0 && "nativePollOnce".equals(stack[0].getMethodName())) continue;
                        report(app, late, stack);
                    }
                } else if (stalled) {
                    stalled = false;
                    long total = SystemClock.elapsedRealtime() - stallStart;
                    DiagLog.add("UI-Thread wieder frei nach ca. " + (total / 1000L) + " s");
                }
            }
        }, "ui-stall-watchdog");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.NORM_PRIORITY);
        watchdog.start();
    }

    private static void report(Context app, long lateMs, StackTraceElement[] stack) {
        StringBuilder frames = new StringBuilder();
        int shown = 0;
        for (StackTraceElement e : stack) {
            if (shown++ >= FRAMES) break;
            if (frames.length() > 0) frames.append(" <- ");
            frames.append(shortClass(e.getClassName())).append('.').append(e.getMethodName())
                    .append(':').append(e.getLineNumber());
        }
        String text = "UI-Thread blockiert seit " + (lateMs / 1000.0d) + " s: " + frames;
        DiagLog.add(text);
        try {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("time", System.currentTimeMillis())
                    .putString("report", text).apply();
        } catch (RuntimeException ignored) {}
    }

    private static String shortClass(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** One line for the diagnosis screen. */
    static String summary(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long time = p.getLong("time", 0L);
        if (time <= 0L) return "Keine UI-Blockade aufgezeichnet";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(new Date(time))
                + " · " + p.getString("report", "");
    }
}
