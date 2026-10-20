package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the latest forecast so a temporary connection loss does not empty the tile. */
final class WeatherStore {
    private static final String PREFS = "weather_cache";
    private static final long MAX_CACHE_AGE_MS = 12L * 60L * 60L * 1_000L;

    private WeatherStore() {}

    static long wallTimeMs() { return System.currentTimeMillis(); }

    static WeatherForecast load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long fetchedAt = p.getLong("fetched_at", 0L);
        long age = System.currentTimeMillis() - fetchedAt;
        if (fetchedAt <= 0L || age < 0L || age > MAX_CACHE_AGE_MS) return null;
        WeatherForecast.Day[] days = new WeatherForecast.Day[WeatherForecast.FORECAST_DAYS];
        for (int i = 0; i < days.length; i++) {
            days[i] = new WeatherForecast.Day(p.getString("date_" + i, ""),
                    p.getInt("code_" + i, -1), longToDouble(p.getLong("max_" + i, NAN_BITS)),
                    longToDouble(p.getLong("min_" + i, NAN_BITS)));
        }
        WeatherForecast result = new WeatherForecast(longToDouble(p.getLong("lat", NAN_BITS)),
                longToDouble(p.getLong("lon", NAN_BITS)), fetchedAt,
                longToDouble(p.getLong("current", NAN_BITS)), p.getInt("current_code", -1),
                p.getBoolean("daylight", true),
                longToDouble(p.getLong("today_max", NAN_BITS)),
                longToDouble(p.getLong("today_min", NAN_BITS)),
                p.getInt("today_precip", -1), p.getString("sunrise", ""), p.getString("sunset", ""), days);
        return result.usable() ? result : null;
    }

    static void save(Context context, WeatherForecast forecast) {
        if (forecast == null || !forecast.usable()) return;
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("fetched_at", forecast.fetchedAtMs)
                .putLong("lat", Double.doubleToRawLongBits(forecast.latitude))
                .putLong("lon", Double.doubleToRawLongBits(forecast.longitude))
                .putLong("current", Double.doubleToRawLongBits(forecast.currentC))
                .putInt("current_code", forecast.currentCode)
                .putBoolean("daylight", forecast.daylight)
                .putLong("today_max", Double.doubleToRawLongBits(forecast.todayMaximumC))
                .putLong("today_min", Double.doubleToRawLongBits(forecast.todayMinimumC))
                .putInt("today_precip", forecast.todayPrecipitationProbability)
                .putString("sunrise", forecast.sunrise)
                .putString("sunset", forecast.sunset);
        for (int i = 0; i < forecast.days.length; i++) {
            WeatherForecast.Day day = forecast.days[i];
            e.putString("date_" + i, day.date).putInt("code_" + i, day.weatherCode)
                    .putLong("max_" + i, Double.doubleToRawLongBits(day.maximumC))
                    .putLong("min_" + i, Double.doubleToRawLongBits(day.minimumC));
        }
        e.apply();
    }

    private static final long NAN_BITS = Double.doubleToRawLongBits(Double.NaN);
    private static double longToDouble(long bits) { return Double.longBitsToDouble(bits); }
}
