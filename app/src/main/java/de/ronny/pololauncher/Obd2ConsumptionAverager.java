package de.ronny.pololauncher;

/**
 * Smooths instantaneous l/100km samples into a running average the driver can actually
 * read, instead of a number that jumps with every throttle movement. Distance-weighted so
 * a long steady stretch outweighs a few seconds at idle. No Android dependency.
 *
 * This accumulates indefinitely across ignition cycles/app restarts once the caller
 * persists and restores {@link #weightedLiters()}/{@link #weightedKm()} via {@link #restore}
 * (see Obd2Client) - it does not reset itself on a timer or distance limit. A driver who
 * wants a fresh figure (new season, after a tank of very different driving) resets it
 * explicitly (Einstellungen -> Verbrauch/OBD2-Adapter).
 */
final class Obd2ConsumptionAverager {
    static final double DISPLAY_MIN_KM = 1d;
    private double weightedLiters;
    private double weightedKm;

    /** Feeds one sample. lph = litres/hour just measured, speedKmh = speed during that sample. */
    synchronized void add(double lph, double speedKmh, double elapsedSeconds) {
        // Four responses at up to 1.5 s each plus the 1 s polling delay can exceed 5 s.
        // Keep those valid slow rounds, but never extrapolate over a long standby gap.
        if (!Double.isFinite(lph) || lph < 0 || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0 || elapsedSeconds > 10
                || !Double.isFinite(speedKmh) || speedKmh < 0 || speedKmh > 255) return;
        double hours = elapsedSeconds / 3600.0;
        weightedLiters += lph * hours;
        if (Double.isFinite(speedKmh) && speedKmh >= 0) weightedKm += speedKmh * hours;
    }

    /** Running average in l/100km, or NaN until enough distance has accumulated. */
    synchronized double value() {
        if (weightedKm < 0.05) return Double.NaN;
        double v = weightedLiters / weightedKm * 100.0;
        return v >= 0 && v <= 60 ? v : Double.NaN;
    }

    /** Accumulated litres so far, for persisting across restarts. */
    synchronized double weightedLiters() { return weightedLiters; }

    /** Accumulated kilometres so far, for persisting across restarts. */
    synchronized double weightedKm() { return weightedKm; }

    /** Dashboard maturity threshold only; never discards idle fuel or resets totals. */
    synchronized double reliableValue() { return weightedKm >= DISPLAY_MIN_KM ? value() : Double.NaN; }

    /** Restores previously persisted accumulator state. Invalid input is ignored (starts fresh). */
    synchronized void restore(double savedWeightedLiters, double savedWeightedKm) {
        if (!Double.isFinite(savedWeightedLiters) || !Double.isFinite(savedWeightedKm)
                || savedWeightedLiters < 0 || savedWeightedKm < 0) return;
        weightedLiters = savedWeightedLiters;
        weightedKm = savedWeightedKm;
    }

    synchronized void reset() {
        weightedLiters = 0;
        weightedKm = 0;
    }
}
