package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Tankerkönig key, resolved station ids and the last known E10 prices (survives restarts / no network). */
final class FuelPriceStore {
    private static final String PREFS = "fuel_prices";

    private FuelPriceStore() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String apiKey(Context c) { return p(c).getString("api_key", "").trim(); }

    static void setApiKey(Context c, String key) {
        SharedPreferences.Editor e = p(c).edit().putString("api_key", key == null ? "" : key.trim());
        for (int i = 0; i < FuelPriceLogic.LABELS.length; i++)
            e.remove("id_" + i).remove("price_" + i).remove("at_" + i).remove("open_" + i);
        e.remove("note").apply();
    }

    static String stationId(Context c, int i) { return p(c).getString("id_" + i, ""); }

    static void setStationId(Context c, int i, String id) { p(c).edit().putString("id_" + i, id).apply(); }

    static void savePrice(Context c, int i, double e10, boolean open) {
        p(c).edit().putString("price_" + i, Double.toString(e10)).putBoolean("open_" + i, open)
                .putLong("at_" + i, System.currentTimeMillis()).apply();
    }

    /** NaN if never fetched. */
    static double price(Context c, int i) {
        try { return Double.parseDouble(p(c).getString("price_" + i, "")); }
        catch (Throwable ignored) { return Double.NaN; }
    }

    static boolean open(Context c, int i) { return p(c).getBoolean("open_" + i, true); }

    static long fetchedAt(Context c, int i) { return p(c).getLong("at_" + i, 0L); }

    static String note(Context c) { return p(c).getString("note", ""); }

    static void setNote(Context c, String note) { p(c).edit().putString("note", note == null ? "" : note).apply(); }
}
