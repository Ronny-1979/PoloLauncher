package de.ronny.pololauncher;

/**
 * Self-calibration of the speed-density model. The model's volumetric efficiency is an assumption;
 * the CAN tank level is the only real fuel measurement. Whenever {@link ConsumptionLearner}
 * completes an independent window (>= 100 km and >= 5 L of real tank drop), the litres the OBD
 * model integrated over the very same window are compared with the litres actually used, and the
 * model's efficiency factor is nudged towards the truth. No Android dependency.
 */
final class Obd2Calibration {
    static final double MIN_SCALE = 0.6d;
    static final double MAX_SCALE = 1.5d;
    /** Share of the observed error corrected per window, like the learner's 25% blend. */
    static final double BLEND = 0.25d;
    /** The OBD kilometres must cover the CAN window; otherwise the adapter was away too long. */
    static final double MIN_COVERAGE = 0.85d;
    static final double MAX_COVERAGE = 1.15d;
    static final double MIN_MODEL_LITRES = 2d;

    private Obd2Calibration() {}

    /** True fuel / modelled fuel for one window, or NaN when the window cannot be trusted. */
    static double windowRatio(double usedLitres, double distanceKm, double modelLitres, double modelKm) {
        if (!Double.isFinite(usedLitres) || !Double.isFinite(distanceKm) || !Double.isFinite(modelLitres)
                || !Double.isFinite(modelKm) || usedLitres < ConsumptionLearner.MIN_USED_LITRES
                || distanceKm < ConsumptionLearner.MIN_DISTANCE_KM || modelLitres < MIN_MODEL_LITRES
                || modelKm <= 0d) return Double.NaN;
        double coverage = modelKm / distanceKm;
        if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) return Double.NaN;
        double ratio = usedLitres / modelLitres;
        return ratio >= 0.5d && ratio <= 2d ? ratio : Double.NaN;
    }

    static double nextScale(double current, double ratio) {
        if (!Double.isFinite(current)) current = 1d;
        if (!Double.isFinite(ratio)) return clamp(current);
        return clamp(current * (1d + (ratio - 1d) * BLEND));
    }

    static double clamp(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}
