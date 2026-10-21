package de.ronny.pololauncher;

/** Look of the position marker on the map. Pure values, stored as a short id in the preferences. */
final class MarkerStyle {
    static final String STANDARD = "standard";
    static final String PUMPKIN = "kuerbis";
    static final String SLEIGH = "schlitten";

    private static final String[] ALL = {STANDARD, PUMPKIN, SLEIGH};
    private static final String[] LABELS = {"Standard (Punkt)", "Halloween-Kürbis", "Weihnachtsschlitten"};

    private MarkerStyle() {}

    /** Unknown or missing ids fall back to the standard dot, so a bad value can never hide the marker. */
    static String normalize(String id) {
        if (id != null) for (String s : ALL) if (s.equals(id)) return s;
        return STANDARD;
    }

    static String[] ids() { return ALL.clone(); }

    static String label(String id) {
        String n = normalize(id);
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(n)) return LABELS[i];
        return LABELS[0];
    }

    /** Marker size in dp (width, height). */
    static int[] sizeDp(String id) {
        String n = normalize(id);
        if (PUMPKIN.equals(n)) return new int[] {48, 44};
        if (SLEIGH.equals(n)) return new int[] {34, 56};
        return new int[] {20, 20};
    }

    /** Only the sleigh has a front; the dot and the pumpkin look the same from every direction. */
    static boolean rotates(String id) { return SLEIGH.equals(normalize(id)); }
}
