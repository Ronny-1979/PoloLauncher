package de.ronny.pololauncher;

/** Holds a confirmed camera briefly through GPS/map gaps and clears stale warnings quickly. */
final class SpeedCameraGate {
    static final long HOLD_UNKNOWN_MS = 3_000L;

    static final class Result {
        final long key;
        final int distanceMetres;
        final long osmId;
        final double latitude;
        final double longitude;
        final String source;

        Result(long key, int distanceMetres) {
            this(key, distanceMetres, 0L, Double.NaN, Double.NaN, "");
        }

        Result(long key, int distanceMetres, long osmId,
               double latitude, double longitude, String source) {
            this.key = key;
            this.distanceMetres = distanceMetres;
            this.osmId = osmId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source == null ? "" : source;
        }

        static Result none() { return new Result(0L, -1); }
        boolean present() { return key != 0L && distanceMetres >= 0; }
    }

    private Result current = Result.none();
    private long lastSeenMs;

    Result update(Result observation, long nowMs) {
        if (observation != null && observation.present()) {
            current = observation;
            lastSeenMs = nowMs;
            return current;
        }
        if (current.present() && nowMs - lastSeenMs <= HOLD_UNKNOWN_MS)
            return current;
        current = Result.none();
        return current;
    }

    void reset() {
        current = Result.none();
        lastSeenMs = 0L;
    }

}
