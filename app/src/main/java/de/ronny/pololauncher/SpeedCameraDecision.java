package de.ronny.pololauncher;

/** Pure filtering rules for map-derived fixed speed-camera candidates. */
final class SpeedCameraDecision {
    static final double MAX_DISTANCE_METRES = 700d;
    static final double MAX_AHEAD_ERROR_DEGREES = 55d;
    static final double MAX_ROAD_AXIS_ERROR_DEGREES = 45d;
    static final float MIN_MOVING_SPEED_MPS = 2.0f;

    private SpeedCameraDecision() {}

    static boolean eligible(double distanceMetres, double bearingToCamera,
                            float vehicleBearing, float speedMps,
                            double cameraRoadBearing, boolean roadBearingKnown) {
        if (!basicAheadCheck(distanceMetres, bearingToCamera, vehicleBearing, speedMps))
            return false;
        return !roadBearingKnown
                || roadAxisDifference(vehicleBearing, cameraRoadBearing)
                    <= MAX_ROAD_AXIS_ERROR_DEGREES;
    }

    static boolean eligible(double distanceMetres, double bearingToCamera,
                            float vehicleBearing, float speedMps,
                            double[] cameraRoadBearings) {
        if (!basicAheadCheck(distanceMetres, bearingToCamera, vehicleBearing, speedMps))
            return false;
        if (cameraRoadBearings == null || cameraRoadBearings.length == 0) return false;
        for (double roadBearing : cameraRoadBearings) {
            if (Double.isFinite(roadBearing)
                    && roadAxisDifference(vehicleBearing, roadBearing)
                        <= MAX_ROAD_AXIS_ERROR_DEGREES) return true;
        }
        return false;
    }

    private static boolean basicAheadCheck(double distanceMetres, double bearingToCamera,
                                           float vehicleBearing, float speedMps) {
        if (!Double.isFinite(distanceMetres) || distanceMetres < 0d
                || distanceMetres > MAX_DISTANCE_METRES
                || !Double.isFinite(bearingToCamera)
                || !Float.isFinite(vehicleBearing) || !Float.isFinite(speedMps)
                || speedMps < MIN_MOVING_SPEED_MPS) return false;
        return directionDifference(vehicleBearing, bearingToCamera)
                <= MAX_AHEAD_ERROR_DEGREES;
    }

    static double directionDifference(double a, double b) {
        double na = normalize(a);
        double nb = normalize(b);
        return Math.abs((na - nb + 540d) % 360d - 180d);
    }

    static double roadAxisDifference(double a, double b) {
        double directional = directionDifference(a, b);
        return Math.min(directional, 180d - directional);
    }

    private static double normalize(double value) {
        return (value % 360d + 360d) % 360d;
    }
}
