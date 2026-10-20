package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the last resolved place visible across standby/restarts and avoids duplicate lookups. */
final class LocalityStore {
    private static final String PREFS = "locality_cache";
    private static final long MAX_CACHE_AGE_MS = 7L * 24L * 60L * 60L * 1_000L;
    private static final long NAN_BITS = Double.doubleToRawLongBits(Double.NaN);

    private LocalityStore() {}

    static Locality load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long fetchedAt = p.getLong("fetched_at", 0L);
        long age = System.currentTimeMillis() - fetchedAt;
        if (fetchedAt <= 0L || age < 0L || age > MAX_CACHE_AGE_MS) return null;
        Locality result = new Locality(p.getString("name", ""),
                Double.longBitsToDouble(p.getLong("lat", NAN_BITS)),
                Double.longBitsToDouble(p.getLong("lon", NAN_BITS)), fetchedAt);
        return result.usable() ? result : null;
    }

    static void save(Context context, Locality locality) {
        if (locality == null || !locality.usable()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", locality.name)
                .putLong("lat", Double.doubleToRawLongBits(locality.latitude))
                .putLong("lon", Double.doubleToRawLongBits(locality.longitude))
                .putLong("fetched_at", locality.fetchedAtMs).apply();
    }
}
