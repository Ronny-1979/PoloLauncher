package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Stores one bounded Java crash report so it survives the process restart on the radio. */
public final class CrashReporter {
    private static final String PREFS = "last_crash";
    private static final int MAX_STACK_CHARS = 24_000;
    private static boolean installed;

    private CrashReporter() {}

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { save(app, thread, error); } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
            else Runtime.getRuntime().halt(10);
        });
    }

    private static void save(Context context, Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(buffer));
        String stack = buffer.toString();
        if (stack.length() > MAX_STACK_CHARS)
            stack = stack.substring(0, MAX_STACK_CHARS) + "\n… [gekürzt]";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("time", System.currentTimeMillis())
                .putString("thread", thread == null ? "unbekannt" : thread.getName())
                .putString("type", error == null ? "unbekannt" : error.getClass().getName())
                .putString("message", error == null || error.getMessage() == null ? "" : error.getMessage())
                .putString("stack", stack).commit();
    }

    public static String summary(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long time = p.getLong("time", 0L);
        if (time <= 0L) return "Kein gespeicherter Java-Absturz";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(new Date(time))
                + " · " + p.getString("type", "unbekannt")
                + (p.getString("message", "").isEmpty() ? "" : ": " + p.getString("message", ""));
    }

    public static boolean hasCrash(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("time", 0L) > 0L;
    }

    public static String fullReport(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getLong("time", 0L) <= 0L) return summary(context);
        return summary(context) + "\nThread: " + p.getString("thread", "unbekannt")
                + "\n" + p.getString("stack", "");
    }
}
