package de.ronny.pololauncher;

/**
 * Prevents auto-return loops and distinguishes automatic CarPlay foregrounding from a deliberate
 * launch by the user. The gate rearms only after CarPlay has been absent for one minute.
 * The return itself waits until CarPlay is really connected (stable for a few seconds) or a
 * generous timeout has passed, so a slow Bluetooth connection after standby is not cut short.
 */
final class CarPlayReturnGate {
    enum Action { NONE, SCHEDULE, CANCEL }
    enum Result { WAIT, RETURN, ABORT }

    /** Earliest return after CarPlay came to the foreground. */
    static final long MIN_WAIT_MS = 5_000L;
    /** The connection signal must stay true this long before the launcher is brought back. */
    static final long CONNECTED_STABLE_MS = 3_000L;
    /** Give up waiting for a connection signal and return anyway. */
    static final long MAX_WAIT_MS = 90_000L;

    private static final long REARM_AFTER_ABSENT_MS = 60_000L;

    private boolean handled;
    private boolean scheduled;
    private long absentSince = -1L;
    private long scheduledAt = -1L;
    private long connectedSince = -1L;
    private boolean lastReturnTimedOut;

    Action update(long nowMs, boolean carPlayForeground, boolean manualSuppressed) {
        if (carPlayForeground) {
            absentSince = -1L;
            if (manualSuppressed) {
                handled = true;
                if (scheduled) {
                    scheduled = false;
                    return Action.CANCEL;
                }
                return Action.NONE;
            }
            if (!handled && !scheduled) {
                scheduled = true;
                scheduledAt = nowMs;
                connectedSince = -1L;
                return Action.SCHEDULE;
            }
            return Action.NONE;
        }

        Action result = Action.NONE;
        if (scheduled) {
            scheduled = false;
            result = Action.CANCEL;
        }
        if (absentSince < 0L) absentSince = nowMs;
        if (handled && nowMs - absentSince >= REARM_AFTER_ABSENT_MS) handled = false;
        return result;
    }

    Result confirmReturn(long nowMs, boolean carPlayForeground, boolean manualSuppressed, boolean connected) {
        if (!scheduled) return Result.ABORT;
        if (!carPlayForeground || manualSuppressed) {
            scheduled = false;
            if (manualSuppressed) handled = true;
            return Result.ABORT;
        }
        if (connected) {
            if (connectedSince < 0L) connectedSince = nowMs;
        } else {
            connectedSince = -1L;
        }
        long waited = nowMs - scheduledAt;
        boolean stable = connectedSince >= 0L && nowMs - connectedSince >= CONNECTED_STABLE_MS
                && waited >= MIN_WAIT_MS;
        boolean timedOut = waited >= MAX_WAIT_MS;
        if (!stable && !timedOut) return Result.WAIT;
        lastReturnTimedOut = !stable;
        scheduled = false;
        handled = true;
        return Result.RETURN;
    }

    /** True if the last RETURN happened only because the maximum wait ran out. */
    boolean lastReturnTimedOut() { return lastReturnTimedOut; }

    void reset() {
        handled = false;
        scheduled = false;
        absentSince = -1L;
        scheduledAt = -1L;
        connectedSince = -1L;
    }
}
