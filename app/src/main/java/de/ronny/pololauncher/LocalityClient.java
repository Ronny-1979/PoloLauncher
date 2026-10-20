package de.ronny.pololauncher;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reverse geocoding through the public OSM Nominatim endpoint. Run only off the UI thread. */
final class LocalityClient {
    private static final String USER_AGENT =
            "PoloLauncher/" + BuildConfig.VERSION_NAME + " (https://github.com/Ronny-1979/PoloLauncher)";

    Locality fetch(double latitude, double longitude) throws IOException {
        if (!Double.isFinite(latitude) || latitude < -90d || latitude > 90d
                || !Double.isFinite(longitude) || longitude < -180d || longitude > 180d)
            throw new IOException("Ungültige GPS-Position");
        String endpoint = String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2"
                        + "&lat=%.5f&lon=%.5f&zoom=14&addressdetails=1&accept-language=%s",
                latitude, longitude, URLEncoder.encode("de", StandardCharsets.UTF_8.name()));
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "de");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://github.com/Ronny-1979/PoloLauncher");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new IOException("Ortsdienst HTTP " + status);
            JSONObject root = new JSONObject(HttpText.readLimited(connection, "Ortsantwort zu groß"));
            JSONObject address = root.optJSONObject("address");
            String[] keys = {"city", "town", "village", "hamlet", "municipality", "suburb", "county"};
            String name = address == null ? "" : firstNonBlank(address, keys);
            if (name.isEmpty()) name = firstDisplayPart(root.optString("display_name", ""));
            Locality result = new Locality(name, latitude, longitude, System.currentTimeMillis());
            if (!result.usable()) throw new IOException("Kein Ortsname gefunden");
            // Volle Adress-Hierarchie mitloggen: bei Ausreißern an Ortsgrenzen sieht man so,
            // welche(s) Feld(er) Nominatim tatsächlich befüllt hat und woher der gewählte
            // Name kam, statt nur das Endergebnis zu sehen.
            DiagLog.add("Ortsdienst " + String.format(Locale.US, "(%.5f,%.5f)", latitude, longitude)
                    + " -> \"" + name + "\" [" + addressDebug(address, keys) + "]"
                    + (address == null ? " (aus display_name, kein address-Objekt)" : ""));
            return result;
        } catch (org.json.JSONException malformed) {
            throw new IOException("Ortsantwort ungültig", malformed);
        } finally { connection.disconnect(); }
    }

    /** Alle in "keys" tatsächlich befüllten Adressfelder, zur Diagnose von Grenzfällen. */
    private static String addressDebug(JSONObject address, String[] keys) {
        if (address == null) return "";
        StringBuilder b = new StringBuilder();
        for (String key : keys) {
            String value = address.optString(key, "").trim();
            if (value.isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(key).append('=').append(value);
        }
        return b.toString();
    }

    private static String firstNonBlank(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String firstDisplayPart(String displayName) {
        if (displayName == null) return "";
        int comma = displayName.indexOf(',');
        return (comma < 0 ? displayName : displayName.substring(0, comma)).trim();
    }
}
