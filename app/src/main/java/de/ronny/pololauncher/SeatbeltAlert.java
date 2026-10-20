package de.ronny.pololauncher;

/**
 * Entscheidet, ob die blinkende Gurt-Warnung auf der Karte gezeigt wird.
 * Sie erscheint erst, wenn das Auto fährt und der Gurt offen ist, und bleibt danach stehen,
 * bis der Gurt nachweislich angelegt ist (auch im Stand) oder der Motor aus ist.
 */
final class SeatbeltAlert {
    static final double MOVING_KMH = 5.0d;
    static final long FRESH_SPEED_MS = 5_000L;
    static final long FRESH_BELT_MS = 15_000L;

    private boolean active;

    boolean update(VehicleState s, long nowMs) {
        long speedAge = nowMs - s.speedUpdatedAtMs;
        boolean speedFresh = s.speedUpdatedAtMs > 0L && speedAge >= 0 && speedAge <= FRESH_SPEED_MS
                && s.speedKmh == s.speedKmh;
        long beltAge = nowMs - s.seatbeltUpdatedAtMs;
        boolean beltFresh = s.seatbeltKnown && beltAge >= 0 && beltAge <= FRESH_BELT_MS;
        boolean beltOpen = beltFresh && s.seatbeltOpen;
        boolean beltClosed = beltFresh && !s.seatbeltOpen;
        boolean moving = speedFresh && s.speedKmh >= MOVING_KMH;
        boolean engineOff = s.rpm == 0 && speedFresh && s.speedKmh < 1.0d;

        if (beltClosed || engineOff) {
            active = false;
        } else if (moving && beltOpen) {
            active = true;
        }
        return active;
    }
}
