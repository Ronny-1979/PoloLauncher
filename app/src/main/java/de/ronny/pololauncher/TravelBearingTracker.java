package de.ronny.pololauncher;

import android.location.Location;

/** Course for both map renderers, independent of compass/magnetic interference. */
final class TravelBearingTracker {
    private Location previous;
    private double bearing;
    private boolean known;

    void update(Location fix) {
        if (fix == null) return;
        double reported;
        if (fix.hasBearing() && fix.hasSpeed() && fix.getSpeed() >= 2.5f
                && (!fix.hasAccuracy() || fix.getAccuracy() < 50f)
                && (!fix.hasBearingAccuracy() || fix.getBearingAccuracyDegrees() <= 35f)
                && (!fix.hasSpeedAccuracy() || fix.getSpeedAccuracyMetersPerSecond() < 5f)) {
            reported = fix.getBearing();
        } else if (previous != null && fix.hasAccuracy() && fix.getAccuracy() < 15f
                && (!previous.hasAccuracy() || previous.getAccuracy() < 15f)) {
            float seconds = secondsBetween(previous, fix);
            float metres = previous.distanceTo(fix);
            if (seconds <= 0f || seconds > 4f || metres <= Math.max(14f, fix.getAccuracy() * 2f)
                    || metres > 120f || metres / seconds < 2.5f) {
                previous = new Location(fix);
                return;
            }
            reported = previous.bearingTo(fix);
        } else {
            previous = new Location(fix);
            return;
        }
        previous = new Location(fix);
        if (!Double.isFinite(reported)) return;
        reported = normalize(reported);
        if (!known) {
            bearing = reported;
            known = true;
        } else {
            double delta = shortest(bearing, reported);
            bearing = normalize(bearing + Math.max(-45d, Math.min(45d, delta * 0.65d)));
        }
    }

    boolean known() { return known; }
    double bearing() { return bearing; }

    private static float secondsBetween(Location from, Location to) {
        long fromElapsed = from.getElapsedRealtimeNanos();
        long toElapsed = to.getElapsedRealtimeNanos();
        if (fromElapsed > 0L && toElapsed > fromElapsed)
            return (toElapsed - fromElapsed) / 1_000_000_000f;
        long fromWall = from.getTime();
        long toWall = to.getTime();
        if (fromWall > 0L && toWall > fromWall) return (toWall - fromWall) / 1000f;
        return -1f;
    }

    static double normalize(double degrees) { return (degrees % 360d + 360d) % 360d; }
    static double shortest(double from, double to) { return (to - from + 540d) % 360d - 180d; }
}
