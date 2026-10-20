package de.ronny.pololauncher;

/**
 * Detects a head-unit quick-start wake even when proprietary standby firmware suppresses
 * Android's normal SCREEN_OFF/SCREEN_ON broadcasts.
 */
final class StandbyWakeDetector {
    static final long FALLBACK_GAP_MS = 15_000L;
    private static final long EXPLICIT_SCREEN_WAKE_GRACE_MS = 5_000L;

    private long lastHeartbeatMs = -1L;
    private long lastExplicitScreenOnMs = -1L;

    void screenOn(long nowMs) {
        lastExplicitScreenOnMs = nowMs;
    }

    boolean heartbeat(long nowMs) {
        long previous = lastHeartbeatMs;
        lastHeartbeatMs = nowMs;
        if (previous < 0L || nowMs < previous) return false;

        long gap = nowMs - previous;
        if (gap < FALLBACK_GAP_MS) return false;

        // If Android delivered SCREEN_ON normally, the existing path already rearms DAB.
        // Avoid a second reset after the same wake event.
        return lastExplicitScreenOnMs < 0L
                || nowMs - lastExplicitScreenOnMs > EXPLICIT_SCREEN_WAKE_GRACE_MS;
    }

    void reset(long nowMs) {
        lastHeartbeatMs = nowMs;
    }
}
