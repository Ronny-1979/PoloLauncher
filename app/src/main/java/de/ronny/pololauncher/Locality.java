package de.ronny.pololauncher;

import java.util.Locale;

/** Immutable result of a reverse-geocoding lookup. */
final class Locality {
    final String name;
    final double latitude;
    final double longitude;
    final long fetchedAtMs;

    Locality(String name, double latitude, double longitude, long fetchedAtMs) {
        this.name = name == null ? "" : name.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.fetchedAtMs = fetchedAtMs;
    }

    boolean usable() {
        return !name.isEmpty() && name.length() <= 120 && Double.isFinite(latitude)
                && latitude >= -90d && latitude <= 90d && Double.isFinite(longitude)
                && longitude >= -180d && longitude <= 180d && fetchedAtMs > 0L;
    }

    static boolean sameName(Locality first, Locality second) {
        return first != null && second != null
                && normalized(first.name).equals(normalized(second.name));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.GERMAN);
    }
}
