package de.ronny.pololauncher;

/** Small, Android-independent helpers used by the optional road-position test. */
final class RoadGeometry {
    static final class Projection {
        final double latitude;
        final double longitude;
        final double distanceMetres;
        final double roadBearing;

        Projection(double latitude, double longitude, double distanceMetres, double roadBearing) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceMetres = distanceMetres;
            this.roadBearing = roadBearing;
        }
    }

    private RoadGeometry() {}

    static Projection project(double aLat, double aLon, double bLat, double bLon,
                              double pointLat, double pointLon) {
        double metresPerDegreeY = 111_320d;
        double metresPerDegreeX = Math.max(1d,
                metresPerDegreeY * Math.cos(Math.toRadians(pointLat)));
        double ax = (aLon - pointLon) * metresPerDegreeX;
        double ay = (aLat - pointLat) * metresPerDegreeY;
        double bx = (bLon - pointLon) * metresPerDegreeX;
        double by = (bLat - pointLat) * metresPerDegreeY;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double fraction = lengthSquared == 0d ? 0d
                : Math.max(0d, Math.min(1d, -(ax * dx + ay * dy) / lengthSquared));
        double x = ax + fraction * dx;
        double y = ay + fraction * dy;
        double bearing = normalize(Math.toDegrees(Math.atan2(dx, dy)));
        return new Projection(pointLat + y / metresPerDegreeY,
                pointLon + x / metresPerDegreeX, Math.hypot(x, y), bearing);
    }

    /** Difference to an unoriented road axis: 0 means parallel, 90 perpendicular. */
    static double axisDifference(double vehicleBearing, double roadBearing) {
        double difference = Math.abs((normalize(roadBearing) - normalize(vehicleBearing) + 540d) % 360d - 180d);
        return Math.min(difference, 180d - difference);
    }

    private static double normalize(double degrees) {
        return (degrees % 360d + 360d) % 360d;
    }
}
