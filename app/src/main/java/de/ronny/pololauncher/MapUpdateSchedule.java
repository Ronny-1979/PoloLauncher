package de.ronny.pololauncher;

/** Timing rules for the automatic weekly map-update check and its reminder popup. */
final class MapUpdateSchedule {
    static final long CHECK_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;
    /** After "Später" the popup returns after this time. */
    static final long LATER_MS = 24L * 60L * 60L * 1000L;
    /** Minimum gap between two automatic check attempts in one process (failed/offline checks). */
    static final long RETRY_MS = 30L * 60L * 1000L;
    /** The launcher must have been visible this long before the check/popup may appear. */
    static final long SETTLE_MS = 45_000L;

    private MapUpdateSchedule() {}

    /** True if the last complete check is a week old (or missing / in the future after a clock jump). */
    static boolean checkDue(long nowMs, long lastCompleteCheckMs) {
        return lastCompleteCheckMs <= 0L || lastCompleteCheckMs > nowMs
                || nowMs - lastCompleteCheckMs >= CHECK_INTERVAL_MS;
    }

    /** True if updates are known and the "Später" pause (if any) is over. */
    static boolean promptDue(long nowMs, int pendingRegions, long remindAfterMs) {
        if (pendingRegions <= 0) return false;
        if (remindAfterMs <= 0L || nowMs >= remindAfterMs) return true;
        return remindAfterMs - nowMs > 2L * LATER_MS; // clock moved backwards: do not wait forever
    }
}
