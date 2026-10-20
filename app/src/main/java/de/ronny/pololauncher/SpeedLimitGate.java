package de.ronny.pololauncher;

/** Stabilizes map-derived speed limits around intersections and short OSM gaps. */
final class SpeedLimitGate {
    private static final long UNKNOWN_HOLD_MS = 6_000L;
    private static final int CHANGE_CONFIRMATIONS = 2;

    private int current = SpeedLimitValue.UNKNOWN;
    private int candidate = SpeedLimitValue.UNKNOWN;
    private int candidateCount;
    private long lastKnownEvidenceMs;

    int update(int observation, long nowMs) {
        if (observation == SpeedLimitValue.UNKNOWN) {
            candidate = SpeedLimitValue.UNKNOWN;
            candidateCount = 0;
            if (current != SpeedLimitValue.UNKNOWN
                    && nowMs - lastKnownEvidenceMs > UNKNOWN_HOLD_MS) {
                current = SpeedLimitValue.UNKNOWN;
            }
            return current;
        }

        lastKnownEvidenceMs = nowMs;

        if (current == SpeedLimitValue.UNKNOWN) {
            current = observation;
            candidate = SpeedLimitValue.UNKNOWN;
            candidateCount = 0;
            return current;
        }

        if (observation == current) {
            candidate = SpeedLimitValue.UNKNOWN;
            candidateCount = 0;
            return current;
        }

        if (candidate == observation) candidateCount++;
        else {
            candidate = observation;
            candidateCount = 1;
        }

        if (candidateCount >= CHANGE_CONFIRMATIONS) {
            current = candidate;
            candidate = SpeedLimitValue.UNKNOWN;
            candidateCount = 0;
        }
        return current;
    }

    void reset() {
        current = SpeedLimitValue.UNKNOWN;
        candidate = SpeedLimitValue.UNKNOWN;
        candidateCount = 0;
        lastKnownEvidenceMs = 0L;
    }
}
