package de.ronny.pololauncher;

/** Time-based dashboard-only smoothing; no artificial kilometres or fuel. */
final class AverageDisplayFilter {
    private double shown = Double.NaN;
    private long previousAt = -1;

    double update(double target, long now) {
        long elapsed = previousAt < 0 ? 0 : Math.max(0, Math.min(2000, now - previousAt));
        previousAt = now;
        if (!Double.isFinite(target) || target < 0 || target > 60) {
            shown = Double.NaN; // A reset must clear the previous figure immediately.
        } else if (!Double.isFinite(shown)) {
            shown = target;
        } else {
            shown += (target - shown) * (1d - Math.exp(-elapsed / 15000d));
        }
        return shown;
    }
}
