package de.ronny.pololauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** Tankerkönig (MTS-K, CC BY 4.0) client; run off the UI thread. */
final class FuelPriceClient {
    static final class Result {
        final double e10;
        final boolean open;
        Result(double e10, boolean open) { this.e10 = e10; this.open = open; }
    }

    static final class KeyRejected extends IOException {
        KeyRejected(String m) { super(m); }
    }

    private static final String BASE = "https://creativecommons.tankerkoenig.de/json/";

    /** Finds the station ids of both targets near Oschatz/Dahlen. Entries stay "" when not found. */
    String[] resolveStations(String apiKey) throws IOException {
        String url = String.format(Locale.US, BASE + "list.php?lat=%.5f&lng=%.5f&rad=%d&sort=dist&type=all&apikey=%s",
                FuelPriceLogic.CENTER_LAT, FuelPriceLogic.CENTER_LNG, FuelPriceLogic.RADIUS_KM, enc(apiKey));
        JSONObject root = get(url);
        String[] ids = new String[FuelPriceLogic.LABELS.length];
        java.util.Arrays.fill(ids, "");
        try {
            JSONArray stations = root.getJSONArray("stations");
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < stations.length(); i++) {
                JSONObject s = stations.getJSONObject(i);
                String name = s.optString("name", ""), brand = s.optString("brand", ""),
                        place = s.optString("place", ""), id = s.optString("id", "");
                if (seen.length() < 600) seen.append(brand).append('/').append(name).append('/').append(place).append("; ");
                for (int t = 0; t < ids.length; t++)
                    if (ids[t].isEmpty() && !id.isEmpty() && FuelPriceLogic.matches(t, name, brand, place)) ids[t] = id;
            }
            DiagLog.add("Tankstellen: " + stations.length() + " Stationen im Umkreis, gefunden: "
                    + (ids[0].isEmpty() ? "Marktkauf nein" : "Marktkauf ja") + ", "
                    + (ids[1].isEmpty() ? "HEM nein" : "HEM ja")
                    + (ids[0].isEmpty() || ids[1].isEmpty() ? " – Umkreis: " + seen : ""));
        } catch (org.json.JSONException e) {
            throw new IOException("Tankerkönig-Antwort ungültig", e);
        }
        return ids;
    }

    /** E10 per station (null entry = no data for that id). */
    Result[] prices(String apiKey, String[] ids) throws IOException {
        StringBuilder list = new StringBuilder();
        for (String id : ids) if (!id.isEmpty()) list.append(list.length() > 0 ? "," : "").append(id);
        Result[] out = new Result[ids.length];
        if (list.length() == 0) return out;
        JSONObject root = get(BASE + "prices.php?ids=" + enc(list.toString()) + "&apikey=" + enc(apiKey));
        try {
            JSONObject prices = root.getJSONObject("prices");
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].isEmpty()) continue;
                JSONObject entry = prices.optJSONObject(ids[i]);
                if (entry == null) continue;
                boolean open = "open".equals(entry.optString("status", "open"));
                double e10 = entry.optDouble("e10", Double.NaN); // "false" -> NaN
                out[i] = new Result(e10, open);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("Tankerkönig-Antwort ungültig", e);
        }
        return out;
    }

    private JSONObject get(String endpoint) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(10_000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "PoloLauncher/" + BuildConfig.VERSION_NAME);
        try {
            int code = c.getResponseCode();
            if (code == 403 || code == 401) throw new KeyRejected("API-Key abgelehnt (HTTP " + code + ")");
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("Tankerkönig HTTP " + code);
            JSONObject root = new JSONObject(HttpText.readLimited(c, "Tankerkönig-Antwort zu groß"));
            if (!root.optBoolean("ok", false)) {
                String msg = root.optString("message", "unbekannt");
                if (msg.toLowerCase(Locale.ROOT).contains("apikey")) throw new KeyRejected("API-Key: " + msg);
                throw new IOException("Tankerkönig: " + msg);
            }
            return root;
        } catch (org.json.JSONException e) {
            throw new IOException("Tankerkönig-Antwort ungültig", e);
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
