package de.ronny.pololauncher;

import android.location.Location;

/**
 * Decides where the follow camera aims for each GPS fix.
 *
 * On top of the lead of {@link CameraMotion} it guarantees that the aim never moves backwards
 * along the direction of travel while the car drives straight on, and it leads with the lower of
 * the last two speeds, so braking can no longer leave the camera (and the dot, which sits in the
 * middle of the map) ahead of the car and pull it back at the next fix. Re-centres that are not
 * triggered by a new fix (map shown again, style reloaded) reuse the last aim instead of jumping
 * to the raw fix, which used to throw the dot back by the length of the lead.
 */
final class CameraAim {
    static final double MAX_BACKWARD_HOLD_METRES = 40d;   // further back is a real jump, not jitter
    static final double MAX_BEARING_CHANGE_DEGREES = 60d; // turning: the old heading no longer applies
    private static final long STALE_MS = 5_000L;

    private boolean have;
    private double fixLat, fixLon, aimLat, aimLon;
    private long aimAt;
    private float lastSpeed = Float.NaN;
    private long lastSpeedAt;
    private float lastBearing = Float.NaN;

    /** Aim for a new fix; returns {lat, lon}. */
    double[] next(double lat, double lon, float speedMps, float bearingDeg, boolean bearingTrusted,
                  long glideMs, long nowMs) {
        float speed = Float.isFinite(speedMps) ? Math.max(0f, speedMps) : 0f;
        float leadSpeed = Float.isFinite(lastSpeed) && nowMs - lastSpeedAt < STALE_MS
                ? Math.min(speed, lastSpeed) : speed;
        lastSpeed = speed;
        lastSpeedAt = nowMs;

        boolean moving = bearingTrusted && Float.isFinite(bearingDeg) && speed >= CameraMotion.MIN_SPEED_MPS;
        double[] aim = CameraMotion.project(lat, lon, leadSpeed, bearingDeg, bearingTrusted, glideMs);

        if (moving && have && nowMs - aimAt < STALE_MS && sameHeading(bearingDeg)) {
            double cos = Math.max(0.1d, Math.cos(Math.toRadians(lat)));
            double dy = (aim[0] - aimLat) * 111_320d;
            double dx = (aim[1] - aimLon) * 111_320d * cos;
            double radians = Math.toRadians(bearingDeg);
            double bx = Math.sin(radians), by = Math.cos(radians);
            double along = dx * bx + dy * by;
            if (along < 0d && along > -MAX_BACKWARD_HOLD_METRES) {
                // Keep the sideways part (the road curves), drop only the backward part.
                MapFollowDiagnostics.aimHeld();
                aim = new double[] {aim[0] - along * by / 111_320d, aim[1] - along * bx / (111_320d * cos)};
            }
        }
        if (!moving) { have = false; lastBearing = Float.NaN; }
        else lastBearing = bearingDeg;
        remember(lat, lon, aim, nowMs, moving);
        return aim;
    }

    double[] next(Location loc, long glideMs, long nowMs) {
        boolean trusted = loc.hasSpeed() && loc.hasBearing()
                && (!loc.hasBearingAccuracy() || loc.getBearingAccuracyDegrees() <= 35f);
        return next(loc.getLatitude(), loc.getLongitude(), loc.hasSpeed() ? loc.getSpeed() : 0f,
                loc.hasBearing() ? loc.getBearing() : 0f, trusted, glideMs, nowMs);
    }

    /** Aim for a re-centre without a new fix: the last aim of that same fix, else the fix itself. */
    double[] hold(Location loc, long nowMs) {
        if (have && nowMs - aimAt < STALE_MS
                && Math.abs(loc.getLatitude() - fixLat) < 1e-9 && Math.abs(loc.getLongitude() - fixLon) < 1e-9)
            return new double[] {aimLat, aimLon};
        return new double[] {loc.getLatitude(), loc.getLongitude()};
    }

    private void remember(double lat, double lon, double[] aim, long nowMs, boolean keep) {
        fixLat = lat; fixLon = lon; aimLat = aim[0]; aimLon = aim[1]; aimAt = nowMs;
        have = keep;
    }

    private boolean sameHeading(float bearing) {
        if (!Float.isFinite(lastBearing)) return true;
        double diff = Math.abs((bearing - lastBearing + 540d) % 360d - 180d);
        return diff <= MAX_BEARING_CHANGE_DEGREES;
    }
}
