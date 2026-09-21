package de.ronny.pololauncher;

import android.location.Location;
import android.os.SystemClock;

/** Stabilizes the camera position only; never changes the GPS fix used for area analysis. */
final class GpsFollowFilter {
    private Location position;
    private long previousMs;
    private Location suspectedJump;
    private int jumpCount;

    Location update(Location fix) {
        long now = SystemClock.elapsedRealtime();
        Location result = new Location(fix);
        float accuracy = fix.hasAccuracy() ? Math.max(0f, fix.getAccuracy()) : 10f;
        if (!Double.isFinite(fix.getLatitude()) || !Double.isFinite(fix.getLongitude())
                || Math.abs(fix.getLatitude()) > 90d || Math.abs(fix.getLongitude()) > 180d
                || !Float.isFinite(accuracy) || accuracy >= 100f) {
            // With no previous valid fix there is nothing safe to display yet.
            return position == null ? null : new Location(position);
        }
        if (position == null) {
            position = result;
            previousMs = now;
            suspectedJump = null;
            jumpCount = 0;
            return result;
        }

        if (now - previousMs > 5_000L) {
            position = result;
            previousMs = now;
            suspectedJump = null;
            jumpCount = 0;
            return result;
        }
        float distance = position.distanceTo(fix);
        float speed = fix.hasSpeed() ? Math.max(0f, fix.getSpeed()) : -1f;
        float oldSpeed = position.hasSpeed() ? Math.max(0f, position.getSpeed()) : -1f;
        float seconds = Math.max(0.2f, (now - previousMs) / 1000f);

        // Vehicle stationary: no slow camera drift from GPS noise. Once driving,
        // GNSS speed releases the hold without waiting for a large position jump.
        if (speed >= 0f && speed < 1.3f
                && distance < Math.max(14f, Math.min(32f, accuracy * 2f))) {
            result.setLatitude(position.getLatitude());
            result.setLongitude(position.getLongitude());
        } else if (speed < 0f && distance < 3f) {
            result.setLatitude(position.getLatitude());
            result.setLongitude(position.getLongitude());
        } else {
            // Reject isolated implausible jumps, but re-acquire a persistent new
            // position (e.g. after driving out of a garage).
            float plausible = Math.max(35f, Math.max(speed, oldSpeed) * seconds * 3f + accuracy * 2f + 12f);
            if (distance > plausible) {
                if (suspectedJump != null && suspectedJump.distanceTo(fix) < 35f) jumpCount++;
                else jumpCount = 1;
                suspectedJump = new Location(fix);
                if (jumpCount < 3) {
                    result.setLatitude(position.getLatitude());
                    result.setLongitude(position.getLongitude());
                } else {
                    suspectedJump = null;
                    jumpCount = 0;
                }
            } else {
                suspectedJump = null;
                jumpCount = 0;
                // Blend a short velocity prediction with the latest measured fix.
                // Only use a trustworthy bearing; never extrapolate through a GPS gap.
                if (oldSpeed > 2f && position.hasBearing() && seconds <= 2.5f
                        && (!position.hasBearingAccuracy() || position.getBearingAccuracyDegrees() <= 35f)
                        && accuracy < 40f) {
                    double radians = Math.toRadians(position.getBearing());
                    double metres = Math.min(oldSpeed * seconds, 55f);
                    double lat = position.getLatitude() + Math.cos(radians) * metres / 111_320d;
                    double cos = Math.max(0.1d, Math.cos(Math.toRadians(lat)));
                    double lon = position.getLongitude() + Math.sin(radians) * metres / (111_320d * cos);
                    result.setLatitude(0.72d * fix.getLatitude() + 0.28d * lat);
                    result.setLongitude(0.72d * fix.getLongitude() + 0.28d * lon);
                }
            }
        }
        position = result;
        previousMs = now;
        return result;
    }
}
