package de.ronny.pololauncher;

/**
 * Low-pass filter for the heading of the follow camera. The GPS course jumps by a few degrees per
 * fix; turning the map by exactly that jump once per second looks like stepping. The shown heading
 * therefore only covers a part of the remaining difference per fix (exponential approach), never
 * turns faster than {@link #MAX_RATE_DEG_PER_S}, and always takes the short way round.
 */
final class BearingSmoother {
    static final double TIME_CONSTANT_MS = 900d;
    static final double MAX_RATE_DEG_PER_S = 120d;
    /** Differences below this are followed exactly (no endless creeping). */
    static final double SNAP_DEG = 0.5d;

    private double shown;
    private boolean have;

    void reset() { have = false; }

    /** @param current heading the camera has right now (used when no heading has been shown yet) */
    double next(double current, double target, long dtMs) {
        double t = (target % 360d + 360d) % 360d;
        if (!have) { have = true; shown = (current % 360d + 360d) % 360d; }
        double delta = (t - shown + 540d) % 360d - 180d;
        if (Math.abs(delta) <= SNAP_DEG || dtMs <= 0L) {
            shown = t;
            return shown;
        }
        double factor = 1d - Math.exp(-dtMs / TIME_CONSTANT_MS);
        double step = delta * factor;
        double limit = MAX_RATE_DEG_PER_S * dtMs / 1000d;
        if (Math.abs(step) > limit) step = Math.signum(step) * limit;
        shown = ((shown + step) % 360d + 360d) % 360d;
        return shown;
    }
}
