package de.ronny.pololauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Small HTTPS client for Open-Meteo; callers must execute it away from the UI thread. */
final class WeatherClient {

    WeatherForecast fetch(double latitude, double longitude) throws IOException {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IOException("Ungültige GPS-Position");
        String endpoint = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                        + "&timezone=auto&forecast_days=4", latitude, longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PoloLauncher/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new IOException("Wetterserver HTTP " + status);
            JSONObject root = new JSONObject(HttpText.readLimited(connection, "Wetterantwort zu groß"));
            JSONObject current = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            JSONArray dates = daily.getJSONArray("time");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray maximum = daily.getJSONArray("temperature_2m_max");
            JSONArray minimum = daily.getJSONArray("temperature_2m_min");
            JSONArray precipitation = daily.getJSONArray("precipitation_probability_max");
            JSONArray sunrise = daily.getJSONArray("sunrise");
            JSONArray sunset = daily.getJSONArray("sunset");
            if (dates.length() < 4 || codes.length() < 4 || maximum.length() < 4 || minimum.length() < 4
                    || precipitation.length() < 1 || sunrise.length() < 1 || sunset.length() < 1)
                throw new IOException("Wettervorhersage unvollständig");
            WeatherForecast.Day[] days = new WeatherForecast.Day[WeatherForecast.FORECAST_DAYS];
            for (int i = 0; i < days.length; i++) {
                int source = i + 1; // index 0 is today; the cache keeps the next three days as fallback data
                days[i] = new WeatherForecast.Day(dates.getString(source), codes.getInt(source),
                        maximum.getDouble(source), minimum.getDouble(source));
            }
            WeatherForecast result = new WeatherForecast(latitude, longitude,
                    System.currentTimeMillis(), current.getDouble("temperature_2m"),
                    current.getInt("weather_code"), current.optInt("is_day", 1) == 1,
                    maximum.getDouble(0), minimum.getDouble(0), precipitation.optInt(0, -1),
                    sunrise.optString(0, ""), sunset.optString(0, ""), days);
            if (!result.usable()) throw new IOException("Wetterdaten ungültig");
            return result;
        } catch (org.json.JSONException malformed) {
            throw new IOException("Wetterantwort ungültig", malformed);
        } finally { connection.disconnect(); }
    }
}
