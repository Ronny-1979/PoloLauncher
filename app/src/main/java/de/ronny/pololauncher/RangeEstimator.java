package de.ronny.pololauncher;

/** Estimated tank contents between whole-litre CAN readings. No Android dependency. */
final class RangeEstimator {
    double litres = Double.NaN;
    long odometer = -1;
    long lastOdometerAt;
    double lastTank = Double.NaN;
    /** Longest standstill that widens the plausible odometer jump: a parked car can not have driven more. */
    static final double MAX_TOLERANCE_HOURS = 24d;
    /** An implausible odometer that stays consistent this long is believed and becomes the new baseline. */
    static final long REBASE_CONFIRM_MS = 30_000L;
    private long rebaseKm = -1;
    private long rebaseSince;
    private long lastUpdateAt;
    private long refillSince;
    private double refillLevel = Double.NaN;

    /** True once an implausible odometer has stayed consistent for {@link #REBASE_CONFIRM_MS}. */
    private boolean confirmRebase(long km, long now) {
        if (rebaseKm < 0 || km < rebaseKm || km - rebaseKm > 5 || now < rebaseSince) {
            rebaseKm = km;
            rebaseSince = now;
            return false;
        }
        rebaseKm = km;
        if (now - rebaseSince < REBASE_CONFIRM_MS) return false;
        odometer = km;
        lastOdometerAt = now;
        rebaseKm = -1;
        return true;
    }

    int update(long km, double tank, double consumption, double reserve, long now) {
        if (!Double.isFinite(consumption) || consumption < 3 || consumption > 20
                || !Double.isFinite(reserve) || reserve < 0 || reserve > 10
                || km <= 0 || km >= 2_000_000) {
            refillSince = 0;
            return -1;
        }
        boolean fuelValid = Double.isFinite(tank) && tank > 0 && tank <= 60;
        if (!Double.isFinite(litres) || odometer <= 0) {
            if (!fuelValid) return -1;
            litres = tank;
            odometer = km;
            lastOdometerAt = now;
            lastTank = tank;
        }
        long travelled = km - odometer;
        // Ignore a reset, corrupt kilometre count or physically impossible forward jump.
        double elapsedHours = Math.min(MAX_TOLERANCE_HOURS, Math.max(0, now - lastOdometerAt) / 3_600_000d);
        if (travelled < 0 || travelled > Math.max(3d, elapsedHours * 350d + 2d)) {
            refillSince = 0;
            // A single glitch frame is ignored (the baseline stays). But if the baseline itself was
            // wrong (an outlier accepted as first value, persisted), the odometer would be
            // rejected forever. A value that keeps counting consistently is therefore adopted.
            if (!confirmRebase(km, now)) return -1;
            travelled = 0;
        } else {
            rebaseKm = -1;
        }
        if (travelled > 0) {
            litres = Math.max(0d, litres - travelled * consumption / 100d);
            odometer = km;
            lastOdometerAt = now;
        }
        double seconds = lastUpdateAt == 0 ? 0 : Math.max(0, Math.min(2d, (now - lastUpdateAt) / 1000d));
        lastUpdateAt = now;
        if (!fuelValid) {
            refillSince = 0;
            return -1; // Never show a saved estimate as if the CAN data were live.
        }
        // A one-litre fluctuation must not reset the continuous distance countdown.
        // Accept a larger persistent rise as refuelling, not a single slosh reading.
        if (!Double.isFinite(lastTank)) lastTank = tank;
        // Require a real upward change in the reported tank value too. A sensor
        // stuck at the same litre value must never be mistaken for a refill.
        if (tank - lastTank >= 3d && tank - litres >= 2d) {
            if (refillSince == 0 || !Double.isFinite(refillLevel) || Math.abs(tank - refillLevel) > 1d) {
                refillSince = now;
                refillLevel = tank;
            } else if (now - refillSince >= 30_000L) {
                litres = tank;
                refillSince = 0;
                refillLevel = Double.NaN;
                lastTank = tank;
            }
        } else {
            refillSince = 0;
            refillLevel = Double.NaN;
            // Preserve the baseline through gradual upward samples; follow
            // downward readings so later real refills can still be detected.
            lastTank = Math.min(lastTank, tank);
        }
        // Downward reconciliation outside the one-litre uncertainty band is gradual.
        // This also catches extra consumption in traffic without guessing fuel from speed.
        double excess = litres - (tank + 1d);
        if (excess > 0d)
            litres -= Math.min(excess, travelled * 0.1d + seconds * 0.002d);
        return (int) Math.floor(Math.max(0d, litres - reserve) * 100d / consumption + 1e-7d);
    }
}
