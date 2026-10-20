package de.ronny.pololauncher;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** Immutable current conditions plus today's details and the next three calendar days. */
final class WeatherForecast {
    static final int FORECAST_DAYS = 3;

    static final class Day {
        final String date;
        final int weatherCode;
        final double maximumC;
        final double minimumC;

        Day(String date, int weatherCode, double maximumC, double minimumC) {
            this.date = date == null ? "" : date;
            this.weatherCode = weatherCode;
            this.maximumC = maximumC;
            this.minimumC = minimumC;
        }
    }

    final double latitude;
    final double longitude;
    final long fetchedAtMs;
    final double currentC;
    final int currentCode;
    final boolean daylight;
    final double todayMaximumC;
    final double todayMinimumC;
    final int todayPrecipitationProbability;
    final String sunrise;
    final String sunset;
    final Day[] days;

    WeatherForecast(double latitude, double longitude, long fetchedAtMs, double currentC,
                    int currentCode, boolean daylight, double todayMaximumC,
                    double todayMinimumC, int todayPrecipitationProbability,
                    String sunrise, String sunset, Day[] days) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.fetchedAtMs = fetchedAtMs;
        this.currentC = currentC;
        this.currentCode = currentCode;
        this.daylight = daylight;
        this.todayMaximumC = todayMaximumC;
        this.todayMinimumC = todayMinimumC;
        this.todayPrecipitationProbability = todayPrecipitationProbability;
        this.sunrise = clockPart(sunrise);
        this.sunset = clockPart(sunset);
        this.days = days == null ? new Day[0] : days.clone();
    }

    boolean usable() {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || !Double.isFinite(currentC) || fetchedAtMs <= 0L
                || days.length != FORECAST_DAYS) return false;
        for (Day day : days) {
            if (day == null || day.date.isEmpty() || !Double.isFinite(day.maximumC)
                    || !Double.isFinite(day.minimumC)) return false;
        }
        return true;
    }

    boolean hasTodayDetails() {
        return Double.isFinite(todayMaximumC) && Double.isFinite(todayMinimumC);
    }

    static String dayLabel(String isoDate) {
        try {
            String label = LocalDate.parse(isoDate).getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                    .replace(".", "");
            return label.toUpperCase(Locale.GERMAN);
        } catch (RuntimeException malformed) { return "--"; }
    }

    private static String clockPart(String value) {
        if (value == null) return "";
        String clean = value.trim();
        int t = clean.indexOf('T');
        if (t >= 0 && clean.length() >= t + 6) return clean.substring(t + 1, t + 6);
        return clean.matches("\\d{2}:\\d{2}") ? clean : "";
    }

    /**
     * Which weather pictogram fits an Open-Meteo weather code. Kept as a plain enum (no
     * android.R reference) so this class stays testable without an Android build; the actual
     * drawable resource for each kind is picked in MainActivity, where R is available.
     */
    enum IconKind { SUN, MOON, SUN_CLOUD, MOON_CLOUD, CLOUD, FOG, RAIN, SNOW, THUNDER, UNKNOWN }

    static IconKind iconKind(int code, boolean daylight) {
        if (code == 0) return daylight ? IconKind.SUN : IconKind.MOON;
        if (code == 1 || code == 2) return daylight ? IconKind.SUN_CLOUD : IconKind.MOON_CLOUD;
        if (code == 3) return IconKind.CLOUD;
        if (code == 45 || code == 48) return IconKind.FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return IconKind.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return IconKind.SNOW;
        if (code >= 95 && code <= 99) return IconKind.THUNDER;
        return IconKind.UNKNOWN;
    }

    static String description(int code) {
        if (code == 0) return "Klar";
        if (code == 1) return "Meist klar";
        if (code == 2) return "Teilweise bewölkt";
        if (code == 3) return "Bewölkt";
        if (code == 45 || code == 48) return "Nebel";
        if (code >= 51 && code <= 57) return "Nieselregen";
        if (code >= 61 && code <= 67) return "Regen";
        if (code >= 71 && code <= 77) return "Schnee";
        if (code >= 80 && code <= 82) return "Regenschauer";
        if (code == 85 || code == 86) return "Schneeschauer";
        if (code >= 95 && code <= 99) return "Gewitter";
        return "Wetter";
    }
}
