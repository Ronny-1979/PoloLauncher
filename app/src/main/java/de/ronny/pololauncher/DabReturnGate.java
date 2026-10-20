package de.ronny.pololauncher;

/**
 * Decides when the launcher may come back after the automatic DABdream+ start: only once DABdream+
 * is in the foreground and the station is really playing (stable for a moment). If the user leaves
 * DABdream+ meanwhile, nothing is forced; after a long wait the launcher returns anyway.
 */
final class DabReturnGate {
    enum Result { WAIT, RETURN, ABORT }

    static final long PLAYING_STABLE_MS = 2_000L;
    static final long MAX_WAIT_MS = 45_000L;
    /** DABdream+ must reach the foreground within this time, otherwise the old fixed return applies. */
    static final long FOREGROUND_GRACE_MS = 10_000L;

    private long startedAt = -1L;
    private long playingSince = -1L;
    private boolean seenForeground;
    private boolean timedOut;

    void start(long nowMs) {
        startedAt = nowMs;
        playingSince = -1L;
        seenForeground = false;
        timedOut = false;
    }

    /**
     * @param foregroundKnown false if the foreground app cannot be read (no usage access)
     * @param dabForeground   DABdream+ is the foreground app (ignored if unknown)
     * @param playing         DABdream+ MediaSession reports playing/buffering
     */
    Result update(long nowMs, boolean foregroundKnown, boolean dabForeground, boolean playing) {
        if (startedAt < 0L) return Result.ABORT;
        long waited = nowMs - startedAt;
        if (foregroundKnown) {
            if (dabForeground) seenForeground = true;
            else if (seenForeground) return Result.ABORT; // user went elsewhere: leave them alone
            else if (waited >= FOREGROUND_GRACE_MS) return Result.RETURN; // never came up: old behaviour
        }
        boolean visible = !foregroundKnown || dabForeground;
        if (visible && playing) {
            if (playingSince < 0L) playingSince = nowMs;
        } else {
            playingSince = -1L;
        }
        if (playingSince >= 0L && nowMs - playingSince >= PLAYING_STABLE_MS) return Result.RETURN;
        if (waited >= MAX_WAIT_MS) {
            timedOut = true;
            return Result.RETURN;
        }
        return Result.WAIT;
    }

    boolean timedOut() { return timedOut; }
}
