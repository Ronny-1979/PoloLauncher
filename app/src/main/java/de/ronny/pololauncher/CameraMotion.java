package de.ronny.pololauncher;


/**
 * Short dead-reckoning lead for the follow camera. A GPS fix is already ~a second old when the
 * 1 s camera glide ends, so gliding to the raw fix makes the map trail behind the car. The glide
 * therefore targets the position the car will have reached when the glide ends.
 */
final class CameraMotion {
    static final float MIN_SPEED_MPS = 3f;
    static final double MAX_LEAD_METRES = 28d;

    private CameraMotion() {}

    /**
     * GPS jitter filter for the follow camera. The camera target is deliberately aimed AHEAD of
     * the car while driving, so comparing a new fix with it is only meaningful when no lead is
     * applied (standing / crawling). Driving, every fix must move the camera: otherwise the lead
     * (about one second of travel) swallows every other fix and the map stops, then jumps.
     */
    static boolean isJitter(float distanceToCameraMetres, float speedMps) {
        boolean driving = Float.isFinite(speedMps) && speedMps >= MIN_SPEED_MPS;
        return !driving && distanceToCameraMetres < 2f;
    }

    /** Returns {lat, lon}; unchanged when speed/bearing are unknown or untrustworthy. */
    static double[] project(double lat, double lon, float speedMps, float bearingDeg,
                            boolean bearingTrusted, long durationMs) {
        if (!bearingTrusted || !Float.isFinite(speedMps) || !Float.isFinite(bearingDeg)
                || speedMps < MIN_SPEED_MPS || durationMs <= 0L)
            return new double[] {lat, lon};
        double metres = Math.min(speedMps * (durationMs / 1000d), MAX_LEAD_METRES);
        double radians = Math.toRadians(bearingDeg);
        double dLat = Math.cos(radians) * metres / 111_320d;
        double cos = Math.max(0.1d, Math.cos(Math.toRadians(lat)));
        double dLon = Math.sin(radians) * metres / (111_320d * cos);
        return new double[] {lat + dLat, lon + dLon};
    }

}
