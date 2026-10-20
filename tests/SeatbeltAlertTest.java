package de.ronny.pololauncher;

public final class SeatbeltAlertTest {
    static void check(boolean ok, String msg) { if (!ok) { System.err.println("SeatbeltAlertTest: FEHLER - " + msg); System.exit(1); } }

    static VehicleState state(double speed, boolean beltOpen, boolean beltKnown, int rpm, long now) {
        VehicleState s = new VehicleState();
        s.speedKmh = speed; s.speedUpdatedAtMs = now;
        s.seatbeltKnown = beltKnown; s.seatbeltOpen = beltOpen; s.seatbeltUpdatedAtMs = now;
        s.rpm = rpm;
        return s;
    }

    public static void main(String[] a) {
        long t = 100_000L;
        SeatbeltAlert alert = new SeatbeltAlert();
        check(!alert.update(state(0, true, true, 850, t), t), "Stand + offener Gurt: keine Warnung");
        t += 1000;
        check(!alert.update(state(3, true, true, 900, t), t), "Schleichfahrt unter 5 km/h: keine Warnung");
        t += 1000;
        check(alert.update(state(12, true, true, 1500, t), t), "Fahrt + offener Gurt: Warnung");
        t += 1000;
        check(alert.update(state(0, true, true, 850, t), t), "Warnung bleibt im Stand stehen, bis der Gurt zu ist");
        t += 1000;
        check(alert.update(state(40, true, true, 2000, t), t), "Warnung bleibt bei Fahrt");
        t += 1000;
        check(!alert.update(state(40, false, true, 2000, t), t), "Gurt angelegt: Warnung weg");
        t += 1000;
        check(!alert.update(state(40, false, true, 2000, t), t), "bleibt weg");
        t += 1000;
        check(alert.update(state(40, true, true, 2000, t), t), "Gurt wieder offen bei Fahrt: Warnung wieder da");
        t += 1000;
        // Gurtdaten fallen aus (unbekannt): Warnung bleibt, wird nicht fälschlich quittiert
        VehicleState unknown = state(40, false, false, 2000, t);
        check(alert.update(unknown, t), "unbekannter Gurtstatus quittiert nicht");
        t += 1000;
        // Motor aus
        check(!alert.update(state(0, true, true, 0, t), t), "Motor aus im Stand: Warnung beendet");
        t += 1000;
        check(!alert.update(state(0, true, true, 0, t), t), "Stand, Motor aus: erscheint nicht");
        // veraltete Geschwindigkeit löst nicht aus
        SeatbeltAlert fresh = new SeatbeltAlert();
        VehicleState stale = state(50, true, true, 2000, t);
        stale.speedUpdatedAtMs = t - 60_000L;
        check(!fresh.update(stale, t), "veraltete Geschwindigkeit löst nicht aus");
        System.out.println("SeatbeltAlertTest: all cases passed");
    }
}
