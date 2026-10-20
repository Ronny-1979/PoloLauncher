package de.ronny.pololauncher;

public final class WeatherForecastTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        WeatherForecast.Day[] days = {
                new WeatherForecast.Day("2026-09-25", 2, 16, 9),
                new WeatherForecast.Day("2026-09-26", 0, 18, 8),
                new WeatherForecast.Day("2026-09-27", 63, 15, 10)
        };
        WeatherForecast forecast = new WeatherForecast(50.83, 12.92, 1L,
                14, 2, true, Double.NaN, Double.NaN, -1, "", "", days);
        check(forecast.usable(), "Complete current conditions and three days are usable");
        check("FR".equals(WeatherForecast.dayLabel("2026-09-25")), "German Friday label");
        check(WeatherForecast.IconKind.SUN_CLOUD == WeatherForecast.iconKind(2, true), "Partly cloudy kind");
        check(WeatherForecast.IconKind.MOON_CLOUD == WeatherForecast.iconKind(2, false), "Partly cloudy at night kind");
        check(WeatherForecast.IconKind.SUN == WeatherForecast.iconKind(0, true), "Clear day kind");
        check(WeatherForecast.IconKind.MOON == WeatherForecast.iconKind(0, false), "Clear night kind");
        check(WeatherForecast.IconKind.RAIN == WeatherForecast.iconKind(63, true), "Rain kind");
        check(WeatherForecast.IconKind.SNOW == WeatherForecast.iconKind(75, true), "Snow kind");
        check(WeatherForecast.IconKind.THUNDER == WeatherForecast.iconKind(95, true), "Thunderstorm kind");
        check(WeatherForecast.IconKind.FOG == WeatherForecast.iconKind(45, true), "Fog kind");
        check(WeatherForecast.IconKind.CLOUD == WeatherForecast.iconKind(3, true), "Overcast kind");
        check(WeatherForecast.IconKind.UNKNOWN == WeatherForecast.iconKind(-1, true), "Unrecognised code falls back");
        check("Teilweise bewölkt".equals(WeatherForecast.description(2)), "German description");
        check("--".equals(WeatherForecast.dayLabel("kaputt")), "Malformed date is safe");
        check(!new WeatherForecast(0, 0, 1, 10, 0, true, Double.NaN, Double.NaN, -1, "", "",
                new WeatherForecast.Day[0]).usable(), "Missing forecast days rejected");
        System.out.println("WeatherForecastTest: all cases passed");
    }
}
