package de.ronny.pololauncher;

import java.time.Instant;

public final class SolarDaylightTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        double latitude = 51.2d;
        double longitude = 13.6d;
        check(!SolarDaylight.isNight(Instant.parse("2026-06-21T12:00:00Z").toEpochMilli(), latitude, longitude),
                "summer noon in Saxony is day");
        check(SolarDaylight.isNight(Instant.parse("2026-06-21T00:00:00Z").toEpochMilli(), latitude, longitude),
                "summer midnight in Saxony is night");
        check(!SolarDaylight.isNight(Instant.parse("2026-12-21T12:00:00Z").toEpochMilli(), latitude, longitude),
                "winter noon in Saxony is day");
        check(SolarDaylight.isNight(Instant.parse("2026-12-21T22:00:00Z").toEpochMilli(), latitude, longitude),
                "winter evening in Saxony is night");
        check(!SolarDaylight.isNight(Instant.parse("2026-06-21T12:00:00Z").toEpochMilli(), Double.NaN, longitude),
                "invalid position fails safely to day mode");
        System.out.println("SolarDaylightTest passed");
    }
}
