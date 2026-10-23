package de.ronny.pololauncher;

/**
 * Small, network-independent solar-position calculation for the automatic map theme.
 * Longitude is positive east of Greenwich. The apparent-sunset limit includes normal
 * atmospheric refraction, so the switch happens at actual sunrise/sunset rather than
 * only after the sky is completely dark.
 */
final class SolarDaylight {
    private static final double APPARENT_SUNSET_DEGREES = -0.833d;
    /** In the evening the night theme starts this long before the apparent sunset (not in the morning). */
    static final long EVENING_LEAD_MS = 30L * 60_000L;

    private SolarDaylight() {}

    static boolean isNight(long epochMillis, double latitude, double longitude) {
        if (!finite(latitude) || !finite(longitude)
                || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d) return false;
        double now = elevationDegrees(epochMillis, latitude, longitude);
        if (now < APPARENT_SUNSET_DEGREES) return true;
        // Sun still up: switch EVENING_LEAD_MS early, but only while the sun is going down.
        double soon = elevationDegrees(epochMillis + EVENING_LEAD_MS, latitude, longitude);
        return soon < APPARENT_SUNSET_DEGREES && soon < now;
    }

    static double elevationDegrees(long epochMillis, double latitude, double longitude) {
        // Solar coordinates from the standard low-cost astronomical approximation.
        double julianDay = epochMillis / 86_400_000d + 2_440_587.5d;
        double days = julianDay - 2_451_545.0d;
        double meanLongitude = normalize360(280.460d + 0.9856474d * days);
        double meanAnomaly = Math.toRadians(normalize360(357.528d + 0.9856003d * days));
        double eclipticLongitude = Math.toRadians(normalize360(meanLongitude
                + 1.915d * Math.sin(meanAnomaly) + 0.020d * Math.sin(2d * meanAnomaly)));
        double obliquity = Math.toRadians(23.439d - 0.0000004d * days);
        double rightAscension = Math.atan2(Math.cos(obliquity) * Math.sin(eclipticLongitude),
                Math.cos(eclipticLongitude));
        double declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLongitude));
        double sidereal = normalize360(280.46061837d
                + 360.98564736629d * (julianDay - 2_451_545.0d));
        double hourAngle = Math.toRadians(normalize180(sidereal + longitude
                - Math.toDegrees(rightAscension)));
        double lat = Math.toRadians(latitude);
        return Math.toDegrees(Math.asin(Math.sin(lat) * Math.sin(declination)
                + Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle)));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double normalize360(double value) {
        value %= 360d;
        return value < 0d ? value + 360d : value;
    }

    private static double normalize180(double value) {
        value = normalize360(value);
        return value > 180d ? value - 360d : value;
    }
}
