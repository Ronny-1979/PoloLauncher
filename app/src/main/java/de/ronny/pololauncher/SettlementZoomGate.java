package de.ronny.pololauncher;

/**
 * Adaptive inner/outer-town state gate.
 *
 * Entry is deliberately fast: a current or look-ahead settlement hit immediately switches
 * to SETTLEMENT. Exit needs two consecutive OUTSIDE observations, a minimum travelled
 * distance and a minimum hold time, so a single map gap never zooms out.
 */
final class SettlementZoomGate {
    enum Signal { UNKNOWN, SETTLEMENT, OUTSIDE }

    /** Direct switching at the town boundary (the only mode): short hold, two clear exits, 60 m. */
    private static final long EXIT_MIN_HOLD_MS = 6_000L;
    private static final int EXIT_CONFIRMATIONS = 2;
    private static final double EXIT_DISTANCE_M = 60d;

    private Signal stable = Signal.UNKNOWN;
    private long settlementSinceMs;
    private int outsideConfirmations;
    private double outsideDistanceM;

    Signal update(Signal currentObservation, boolean lookAheadSettlement,
                  long nowMs, double movedSincePreviousSampleM) {
        Signal current = currentObservation == null ? Signal.UNKNOWN : currentObservation;

        // Enter fast, including the predicted point in driving direction.
        if (current == Signal.SETTLEMENT || lookAheadSettlement) {
            outsideConfirmations = 0;
            outsideDistanceM = 0d;
            if (stable != Signal.SETTLEMENT) {
                stable = Signal.SETTLEMENT;
                settlementSinceMs = nowMs;
            }
            return stable;
        }

        if (stable == Signal.SETTLEMENT) {
            // UNKNOWN is normally a map gap/park/GPS edge. It must never make us zoom out.
            // It also breaks an OUTSIDE streak because leaving should be unambiguous.
            if (current != Signal.OUTSIDE) {
                outsideConfirmations = 0;
                outsideDistanceM = 0d;
                return stable;
            }

            if (nowMs - settlementSinceMs < EXIT_MIN_HOLD_MS) {
                outsideConfirmations = 0;
                outsideDistanceM = 0d;
                return stable;
            }

            if (outsideConfirmations == 0) {
                outsideConfirmations = 1;
                outsideDistanceM = 0d;
            } else {
                outsideConfirmations++;
                outsideDistanceM += Math.max(0d, movedSincePreviousSampleM);
            }

            if (outsideConfirmations >= EXIT_CONFIRMATIONS
                    && outsideDistanceM >= EXIT_DISTANCE_M) {
                stable = Signal.OUTSIDE;
                outsideConfirmations = 0;
                outsideDistanceM = 0d;
            }
            return stable;
        }

        // Outside/unknown start-up may follow the current observation directly.
        // The strong hysteresis is only needed while we are already inside a settlement.
        if (current == Signal.OUTSIDE) stable = Signal.OUTSIDE;
        return stable;
    }

    void reset() {
        stable = Signal.UNKNOWN;
        settlementSinceMs = 0L;
        outsideConfirmations = 0;
        outsideDistanceM = 0d;
    }
}
