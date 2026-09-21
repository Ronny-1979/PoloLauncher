package de.ronny.pololauncher;

/** Learns a conservative long-term consumption from raw CAN litres and odometer.
 * Never use the range estimator's modelled litres here: that would be circular.
 */
final class ConsumptionLearner {
    static final long CONFIRM_MS = 30_000L;
    static final long MIN_DISTANCE_KM = 100L;
    static final double MIN_USED_LITRES = 5d;
    double learned = Double.NaN;
    long windows;
    long learnedDistance;
    double learnedFuel;
    long anchorKm = -1;
    double anchorTank = Double.NaN;
    long stableKm = -1;
    double stableTank = Double.NaN;
    long previousKm = -1;
    long previousAt;
    /** OBD model totals ({@link Obd2ConsumptionAverager}) when the current window started; NaN if unknown. */
    double anchorObdLitres = Double.NaN;
    double anchorObdKm = Double.NaN;
    /** Result of the most recently completed window, for {@link Obd2Calibration}; NaN if not applicable. */
    double windowUsedLitres = Double.NaN;
    double windowDistanceKm = Double.NaN;
    double windowObdLitres = Double.NaN;
    double windowObdKm = Double.NaN;
    private double nowObdLitres = Double.NaN;
    private double nowObdKm = Double.NaN;
    private double candidateTank = Double.NaN;
    private long candidateSince;
    private long candidateAt;
    private String status = "Warte auf stabile CAN-Tank- und Kilometerdaten";

    double value(double fallback) { return validRate(learned) ? learned : fallback; }
    String status() { return status; }

    boolean update(long km, double tank, long now, double fallback) {
        return update(km, tank, now, fallback, Double.NaN, Double.NaN);
    }

    /**
     * @param obdLitres running OBD model litres total (NaN when unknown)
     * @param obdKm     running OBD model kilometre total (NaN when unknown); both are only used to
     *                  measure what the OBD model integrated inside one learning window
     */
    boolean update(long km, double tank, long now, double fallback, double obdLitres, double obdKm) {
        nowObdLitres = obdLitres;
        nowObdKm = obdKm;
        if (km <= 0 || km >= 2_000_000 || !Double.isFinite(tank) || tank <= 0 || tank > 60
                || !validRate(fallback)) {
            clearCandidate();
            status = "Pausiert: keine frischen CAN-Tank-/Kilometerdaten";
            return false;
        }
        if (previousKm > 0) {
            long distance = km - previousKm;
            double maximum = Math.max(3d, Math.max(0L, now - previousAt) / 3_600_000d * 350d + 2d);
            if (now < previousAt || distance < 0 || distance > maximum) {
                clearWindow();
                status = "Lernabschnitt verworfen: unplausibler Kilometerstand/Zeitwechsel";
                return false;
            }
        }
        previousKm = km;
        previousAt = now;
        // A resume, reception gap or clock jump must not count as 30 seconds of
        // continuous confirmation. The distance anchor still survives trips.
        if (!Double.isFinite(candidateTank) || Math.abs(tank - candidateTank) > 0.1d
                || now < candidateAt || now - candidateAt > 15_000L) {
            candidateTank = tank;
            candidateSince = now;
            candidateAt = now;
            status = "Tankwert wird stabilisiert (30 Sekunden)";
            return false;
        }
        candidateAt = now;
        if (now - candidateSince < CONFIRM_MS) return false;
        if (!Double.isFinite(stableTank) || stableKm <= 0 || anchorKm <= 0) {
            startWindow(km, tank);
            return false;
        }
        if (tank >= stableTank + 3d) {
            // Includes gradual +1 L steps: the low baseline did not move up.
            startWindow(km, tank);
            status = "Nachtanken erkannt: neuer Lernabschnitt, Lernwert bleibt erhalten";
            return false;
        }
        if (tank > stableTank) {
            status = "Aufwärtsschwankung: kein Verbrauch wird daraus gelernt";
            return false;
        }
        if (Math.abs(tank - stableTank) <= 0.1d) {
            status = "Sammle Strecke und Tankabnahme (mindestens 100 km / 5 L)";
            return false; // Never repeatedly learn from a stuck tank reading.
        }
        long sinceStable = km - stableKm;
        double drop = stableTank - tank;
        if (sinceStable < 0 || drop > Math.max(2d, sinceStable * 0.20d + 1d)) {
            startWindow(km, tank);
            status = "Unplausibler Tankabfall: Lernabschnitt neu begonnen";
            return false;
        }
        stableTank = tank;
        stableKm = km;
        long distance = km - anchorKm;
        double used = anchorTank - tank;
        if (distance < MIN_DISTANCE_KM || used < MIN_USED_LITRES) {
            status = "Sammle Strecke und Tankabnahme (mindestens 100 km / 5 L)";
            return false;
        }
        double measured = used * 100d / distance;
        if (!validRate(measured) || distance > 2_000L) {
            startWindow(km, tank);
            status = "Unplausibler Abschnittsverbrauch: nicht übernommen";
            return false;
        }
        windowUsedLitres = used;
        windowDistanceKm = distance;
        windowObdLitres = finiteDifference(nowObdLitres, anchorObdLitres);
        windowObdKm = finiteDifference(nowObdKm, anchorObdKm);
        double current = value(fallback);
        // 25% new observation, max 0.5 L/100km change per independent window.
        learned = Math.max(3d, Math.min(20d,
                current + Math.max(-0.5d, Math.min(0.5d, (measured - current) * 0.25d))));
        windows++;
        learnedDistance += distance;
        learnedFuel += used;
        startWindow(km, tank);
        status = "Verbrauch aktualisiert; sammle nächsten unabhängigen Lernabschnitt";
        return true;
    }

    private static double finiteDifference(double end, double start) {
        double difference = end - start;
        return Double.isFinite(difference) && difference >= 0d ? difference : Double.NaN;
    }

    /** Marks the calibration data of the last window as consumed. */
    void clearCalibrationWindow() {
        windowUsedLitres = windowDistanceKm = windowObdLitres = windowObdKm = Double.NaN;
    }

    void clearWindow() {
        anchorObdLitres = anchorObdKm = Double.NaN;
        anchorKm = stableKm = previousKm = -1;
        anchorTank = stableTank = Double.NaN;
        previousAt = 0L;
        clearCandidate();
    }
    private void startWindow(long km, double tank) {
        anchorObdLitres = nowObdLitres;
        anchorObdKm = nowObdKm;
        anchorKm = stableKm = km;
        anchorTank = stableTank = tank;
        status = "Sammle Strecke und Tankabnahme (mindestens 100 km / 5 L)";
    }
    private void clearCandidate() {
        candidateTank = Double.NaN;
        candidateSince = candidateAt = 0L;
    }
    static boolean validRate(double rate) { return Double.isFinite(rate) && rate >= 3d && rate <= 20d; }
}
