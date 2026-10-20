package de.ronny.pololauncher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Distance-weighted fuel average over the most recent 100 km.
 *
 * Samples are collected in small distance buckets. Once the window exceeds 100 km, the
 * oldest bucket (or the required fraction of it) is removed immediately. Fuel burned while
 * stationary is attached to the current bucket: it raises the average, travels with the
 * surrounding driving section and eventually leaves the window instead of remaining forever.
 */
final class Obd2ConsumptionAverager {
    static final double DISPLAY_MIN_KM = 1d;
    static final double WINDOW_KM = 100d;
    // Half-kilometre buckets keep the persisted window compact (~200 entries) while the
    // oldest bucket can still be trimmed fractionally for an exact 100-km total.
    private static final double BUCKET_KM = 0.5d;
    private static final String SERIAL_PREFIX = "v1|";
    private static final int MAX_SERIAL_BUCKETS = 1200;
    private static final double EPSILON = 1e-9;

    private static final class Bucket {
        double km;
        double liters;
        Bucket(double km, double liters) { this.km = km; this.liters = liters; }
    }

    private final ArrayDeque<Bucket> buckets = new ArrayDeque<>();
    private double weightedLiters;
    private double weightedKm;

    /** Feeds one measured interval. */
    synchronized void add(double lph, double speedKmh, double elapsedSeconds) {
        // Valid ELM rounds can take several seconds. Never extrapolate over standby gaps.
        if (!Double.isFinite(lph) || lph < 0 || !Double.isFinite(elapsedSeconds)
                || elapsedSeconds <= 0 || elapsedSeconds > 10
                || !Double.isFinite(speedKmh) || speedKmh < 0 || speedKmh > 255) return;

        double hours = elapsedSeconds / 3600d;
        double liters = lph * hours;
        double km = speedKmh * hours;
        if (km <= EPSILON) {
            Bucket bucket = writableBucket();
            bucket.liters += liters;
            weightedLiters += liters;
            return;
        }

        double litersPerKm = liters / km;
        double remainingKm = km;
        while (remainingKm > EPSILON) {
            Bucket bucket = writableBucket();
            double capacity = Math.max(0d, BUCKET_KM - bucket.km);
            if (capacity <= EPSILON) {
                bucket = new Bucket(0d, 0d);
                buckets.addLast(bucket);
                capacity = BUCKET_KM;
            }
            double partKm = Math.min(remainingKm, capacity);
            double partLiters = litersPerKm * partKm;
            bucket.km += partKm;
            bucket.liters += partLiters;
            weightedKm += partKm;
            weightedLiters += partLiters;
            remainingKm -= partKm;
        }
        trimToWindow();
    }

    private Bucket writableBucket() {
        Bucket last = buckets.peekLast();
        if (last == null || last.km >= BUCKET_KM - EPSILON) {
            last = new Bucket(0d, 0d);
            buckets.addLast(last);
        }
        return last;
    }

    private void trimToWindow() {
        double excess = weightedKm - WINDOW_KM;
        while (excess > EPSILON && !buckets.isEmpty()) {
            Bucket first = buckets.peekFirst();
            if (first.km <= excess + EPSILON) {
                buckets.removeFirst();
                weightedKm -= first.km;
                weightedLiters -= first.liters;
                excess -= first.km;
            } else {
                double fraction = excess / first.km;
                double removedLiters = first.liters * fraction;
                first.km -= excess;
                first.liters -= removedLiters;
                weightedKm -= excess;
                weightedLiters -= removedLiters;
                excess = 0d;
            }
        }
        if (Math.abs(weightedKm - WINDOW_KM) < EPSILON) weightedKm = WINDOW_KM;
        if (weightedKm < EPSILON) weightedKm = 0d;
        if (weightedLiters < EPSILON) weightedLiters = 0d;
    }

    /** Current rolling average in l/100km. */
    synchronized double value() {
        if (weightedKm < 0.05d) return Double.NaN;
        double value = weightedLiters / weightedKm * 100d;
        return value >= 0d && value <= 60d ? value : Double.NaN;
    }

    synchronized double weightedLiters() { return weightedLiters; }
    synchronized double weightedKm() { return weightedKm; }
    synchronized double reliableValue() { return weightedKm >= DISPLAY_MIN_KM ? value() : Double.NaN; }

    /**
     * Imports the two-number accumulator used by versions up to 0.30.21. If it contains more
     * than 100 km, its average is retained as a 100-km seed and is then replaced progressively.
     */
    synchronized void restore(double savedWeightedLiters, double savedWeightedKm) {
        if (!Double.isFinite(savedWeightedLiters) || !Double.isFinite(savedWeightedKm)
                || savedWeightedLiters < 0d || savedWeightedKm < 0d) return;
        reset();
        if (savedWeightedKm > WINDOW_KM) {
            savedWeightedLiters *= WINDOW_KM / savedWeightedKm;
            savedWeightedKm = WINDOW_KM;
        }
        if (savedWeightedKm > 0d || savedWeightedLiters > 0d) {
            buckets.add(new Bucket(savedWeightedKm, savedWeightedLiters));
            weightedKm = savedWeightedKm;
            weightedLiters = savedWeightedLiters;
        }
    }

    /** Compact, locale-independent persistence of the actual rolling window. */
    synchronized String snapshot() {
        StringBuilder out = new StringBuilder(SERIAL_PREFIX);
        for (Bucket bucket : buckets) {
            out.append(Double.toHexString(bucket.km)).append(',')
                    .append(Double.toHexString(bucket.liters)).append(';');
        }
        return out.toString();
    }

    /** Restores a rolling window atomically. Corrupt data leaves the existing state untouched. */
    synchronized boolean restoreSnapshot(String serialized) {
        if (serialized == null || !serialized.startsWith(SERIAL_PREFIX)) return false;
        String body = serialized.substring(SERIAL_PREFIX.length());
        List<Bucket> parsed = new ArrayList<>();
        double kmTotal = 0d;
        double literTotal = 0d;
        if (!body.isEmpty()) {
            String[] entries = body.split(";", -1);
            for (String entry : entries) {
                if (entry.isEmpty()) continue;
                if (parsed.size() >= MAX_SERIAL_BUCKETS) return false;
                int comma = entry.indexOf(',');
                if (comma <= 0 || comma == entry.length() - 1) return false;
                try {
                    double km = Double.valueOf(entry.substring(0, comma));
                    double liters = Double.valueOf(entry.substring(comma + 1));
                    if (!Double.isFinite(km) || !Double.isFinite(liters)
                            || km < 0d || km > WINDOW_KM || liters < 0d || liters > 100d)
                        return false;
                    parsed.add(new Bucket(km, liters));
                    kmTotal += km;
                    literTotal += liters;
                    if (!Double.isFinite(kmTotal) || !Double.isFinite(literTotal)) return false;
                } catch (NumberFormatException badData) {
                    return false;
                }
            }
        }
        reset();
        buckets.addAll(parsed);
        weightedKm = kmTotal;
        weightedLiters = literTotal;
        trimToWindow();
        return true;
    }

    synchronized void reset() {
        buckets.clear();
        weightedLiters = 0d;
        weightedKm = 0d;
    }
}
