package de.ronny.pololauncher;

import android.content.Context;

import java.io.File;

/** The 16 Bundesländer used for the Germany offline package (Geofabrik names). */
final class GermanyRegions {
    static final String[] FILES = {"baden-wuerttemberg", "bayern", "berlin", "brandenburg", "bremen",
            "hamburg", "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen", "schleswig-holstein", "thueringen"};

    private GermanyRegions() {}

    static int indexOf(String key) {
        for (int i = 0; i < FILES.length; ++i) if (FILES[i].equals(key)) return i;
        return -1;
    }

    /** Folder of the pre-Germany regional .map files; only used to delete them. */
    static File legacyFolder(Context c) {
        File base = c.getExternalFilesDir(null);
        return base == null ? null : new File(base, "offline_maps");
    }

    static File legacyFile(Context c, String key) {
        File dir = legacyFolder(c);
        return indexOf(key) < 0 || dir == null ? null : new File(dir, key + ".map");
    }
}
