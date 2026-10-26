package de.ronny.pololauncher;

import java.util.Locale;

/** Pure rules for the two fixed petrol stations: matching Tankerkönig list entries, formatting, comparison. */
final class FuelPriceLogic {
    /** Fixed targets, in display order. */
    static final String[] LABELS = {"MK-T", "HEM"};
    static final String[] PLACES = {"oschatz", "dahlen"};
    /** Search centre between Oschatz and Dahlen (Sachsen) and radius in km (API maximum is 25). */
    static final double CENTER_LAT = 51.30d, CENTER_LNG = 13.05d;
    static final int RADIUS_KM = 12;
    static final long PRICE_STALE_MS = 60L * 60L * 1000L;

    private FuelPriceLogic() {}

    static boolean matches(int target, String name, String brand, String place) {
        if (target < 0 || target >= LABELS.length) return false;
        String p = lower(place);
        if (!p.contains(PLACES[target])) return false;
        String n = lower(name) + " | " + lower(brand);
        if (target == 0) return n.contains("marktkauf");
        return java.util.regex.Pattern.compile("(^|[^a-zäöüß])hem([^a-zäöüß]|$)").matcher(n).find();
    }

    static String price(double euro) {
        if (!(euro > 0.5d) || euro > 9.999d) return "--";
        return String.format(Locale.GERMANY, "%.3f €", euro);
    }

    /** Index of the cheaper station among valid prices, or -1 if fewer than two valid prices / equal. */
    static int cheaper(double a, double b) {
        boolean va = a > 0.5d && a < 10d, vb = b > 0.5d && b < 10d;
        if (!va || !vb) return -1;
        if (Math.abs(a - b) < 0.0005d) return -1;
        return a < b ? 0 : 1;
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
