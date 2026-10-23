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
        long noon = Instant.parse("2026-10-23T11:00:00Z").toEpochMilli();
        long sunset = noon;
        while (SolarDaylight.elevationDegrees(sunset, latitude, longitude) >= -0.833d) sunset += 60_000L;
        check(!SolarDaylight.isNight(sunset - 31L * 60_000L, latitude, longitude), "31 min before sunset still day");
        check(SolarDaylight.isNight(sunset - 29L * 60_000L, latitude, longitude), "29 min before sunset is night");
        long sunrise = noon;
        while (SolarDaylight.elevationDegrees(sunrise, latitude, longitude) >= -0.833d) sunrise -= 60_000L;
        sunrise += 60_000L; // first minute of day
        check(SolarDaylight.isNight(sunrise - 5L * 60_000L, latitude, longitude), "morning: night until sunrise");
        check(!SolarDaylight.isNight(sunrise + 5L * 60_000L, latitude, longitude), "morning: day after sunrise");
        System.out.println("SolarDaylightTest passed");
    }
}
