package de.ronny.pololauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Grobe Umrisse (Bounding Boxes) der 14 Geofabrik-Bundeslandpakete.
 *
 * Der Offline-Stil bekommt pro Paket eine eigene MapLibre-Quelle samt rund 200 Ebenen. Alle 14
 * gleichzeitig zu laden wuerde die Karte unnoetig ausbremsen; es werden nur die Pakete eingebunden,
 * die an der aktuellen Position (plus Puffer) liegen. Reine Java-Klasse, ohne Android-Abhaengigkeit.
 */
final class GermanyRegionIndex {
    /** Reihenfolge und Schluessel identisch zu GermanyOfflineStore.REGION_KEYS (durch Test geprueft). */
    static final String[] KEYS = {
            "baden-wuerttemberg", "bayern", "brandenburg", "hamburg",
            "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen",
            "schleswig-holstein", "thueringen"
    };
    // minLon, minLat, maxLon, maxLat
    private static final double[][] BOX = {
            {7.51, 47.53, 10.50, 49.79}, {8.97, 47.27, 13.84, 50.57},
            {11.27, 51.36, 14.77, 53.56}, {8.42, 53.39, 10.33, 53.96},
            {7.77, 49.39, 10.24, 51.66}, {10.59, 53.11, 14.42, 54.70},
            {6.65, 51.30, 11.60, 53.90}, {5.86, 50.32, 9.46, 52.53},
            {6.11, 48.97, 8.51, 50.94}, {6.36, 49.11, 7.41, 49.64},
            {10.56, 50.94, 13.19, 53.04}, {11.87, 50.17, 15.04, 51.69},
            {7.87, 53.36, 11.31, 55.06}, {9.88, 50.20, 12.65, 51.65}
    };

    private GermanyRegionIndex() {}

    /** Abstand zur Box in Grad (Laengengrad mit cos(lat) gewichtet); 0 = innerhalb. */
    static double distanceDegrees(int index, double lat, double lon) {
        double[] b = BOX[index];
        double dLat = lat < b[1] ? b[1] - lat : lat > b[3] ? lat - b[3] : 0d;
        double dLon = lon < b[0] ? b[0] - lon : lon > b[2] ? lon - b[2] : 0d;
        dLon *= Math.cos(Math.toRadians(lat));
        return Math.hypot(dLat, dLon);
    }

    private static double area(int index) {
        double[] b = BOX[index];
        return (b[2] - b[0]) * (b[3] - b[1]);
    }

    /**
     * Bis zu {@code max} Pakete, deren Box naeher als {@code marginDegrees} liegt, nach Naehe
     * sortiert (bei Gleichstand kleinere Box zuerst, damit z. B. Hamburg vor Niedersachsen steht).
     * Die Sortierung ist unabhaengig vom Rand: ein kleinerer Rand liefert immer ein Praefix.
     */
    static List<String> select(double lat, double lon, double marginDegrees, int max) {
        Integer[] order = new Integer[KEYS.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final double[] dist = new double[KEYS.length];
        for (int i = 0; i < dist.length; i++) dist[i] = distanceDegrees(i, lat, lon);
        Arrays.sort(order, (a, b) -> {
            int c = Double.compare(dist[a], dist[b]);
            if (c != 0) return c;
            c = Double.compare(area(a), area(b));
            return c != 0 ? c : Integer.compare(a, b);
        });
        List<String> out = new ArrayList<>();
        for (int idx : order) {
            if (out.size() >= max) break;
            if (dist[idx] <= marginDegrees) out.add(KEYS[idx]);
        }
        return out;
    }

    /** True, wenn alle Pakete in Sichtnaehe (kleiner Rand) bereits im aktuellen Stil stecken. */
    static boolean covered(Collection<String> loaded, double lat, double lon, double marginDegrees, int max) {
        return loaded.containsAll(select(lat, lon, marginDegrees, max));
    }

    /** Position ausserhalb aller Pakete (Ausland): nichts auswaehlbar. */
    static boolean insideGermanyPackages(double lat, double lon) {
        return !select(lat, lon, 0.0d, 1).isEmpty();
    }
}
