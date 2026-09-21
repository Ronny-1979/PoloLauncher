package de.ronny.pololauncher;

/** Display-only filter. Its result must never enter fuel/consumption learning. */
final class RangeDisplayFilter {
    private double shown;
    private long previousAt = -1;
    private long pendingSince;
    private int pendingDirection;

    RangeDisplayFilter(int saved) {
        shown = saved >= 0 && saved <= 2000 ? saved : Double.NaN;
    }

    int update(int target, long now) {
        long elapsed = previousAt < 0 ? 0 : Math.max(0, Math.min(2000, now - previousAt));
        previousAt = now;
        if (target < 0 || target > 2000) {
            pendingDirection = 0;
            return -1; // Caller keeps last-known cache, not a fabricated live result.
        }
        double delta = target - shown;
        if (!Double.isFinite(shown) || target <= 20 || Math.abs(delta) <= 3) {
            shown = target;
            pendingDirection = 0;
        } else {
            int direction = delta > 0 ? 1 : -1;
            if (pendingDirection != direction || now < pendingSince) {
                pendingDirection = direction;
                pendingSince = now;
            } else if (now - pendingSince >= 5000) {
                double step = elapsed / 1000d * 2d;
                shown += Math.copySign(Math.min(Math.abs(delta), step), delta);
            }
        }
        return (int) Math.round(shown);
    }
}
