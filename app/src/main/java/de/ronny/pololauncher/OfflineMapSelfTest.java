package de.ronny.pololauncher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.List;

/**
 * Selbsttest der Offline-Karte fuer das Diagnose-Log. Laeuft einmal pro Start im Hintergrund und
 * schreibt auf, was MapLibre spaeter lesen muss: Kartendatei, Kopfdaten, Kachel unter dem Auto,
 * Sprite und Schrift. So zeigt ein einziges Log, ob die Daten oder die Darstellung das Problem sind.
 */
final class OfflineMapSelfTest {
    private OfflineMapSelfTest() {}

    static void start(Context context, boolean hasPosition, double lat, double lon) {
        final Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try { run(app, hasPosition, lat, lon); }
            catch (Throwable e) { DiagLog.add("Offline-Selbsttest abgebrochen: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
        }, "offline-selftest");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void run(Context c, boolean hasPosition, double lat, double lon) {
        List<String> keys = hasPosition ? GermanyRegionIndex.select(lat, lon, GermanyOfflineStyle.LOAD_MARGIN_DEG, 3)
                : new java.util.ArrayList<>();
        if (keys.isEmpty()) keys.add(GermanyOfflineStore.REGION_KEYS[0]);
        for (String key : keys) inspectMap(c, key, hasPosition, lat, lon);
        File sprites = GermanyOfflineStyle.spriteDir(c);
        StringBuilder sp = new StringBuilder("Offline-Selbsttest Sprite: ");
        for (String n : new String[]{"sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png"}) {
            File f = sprites == null ? null : new File(sprites, n);
            sp.append(n).append('=').append(f != null && f.isFile() ? f.length() + " B" : "fehlt").append(' ');
        }
        DiagLog.add(sp.toString().trim() + " (wird beim ersten Stilaufbau kopiert)");
        StringBuilder gl = new StringBuilder("Offline-Selbsttest Schrift: ");
        for (int start : GermanyOfflineStore.glyphStarts()) {
            File f = GermanyOfflineStore.glyphFile(c, start);
            gl.append(start).append('=').append(f != null && f.isFile() ? f.length() + " B" : "fehlt").append(' ');
        }
        DiagLog.add(gl.toString().trim());
    }

    private static void inspectMap(Context c, String key, boolean hasPosition, double lat, double lon) {
        File file = GermanyOfflineStore.mapFile(c, key);
        String tag = "Offline-Selbsttest " + key + ": ";
        if (file == null || !file.isFile()) { DiagLog.add(tag + "Datei fehlt"); return; }
        DiagLog.add(tag + "Datei " + (file.length() / (1024 * 1024)) + " MB, URL mbtiles://" + file.getAbsolutePath());
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            StringBuilder meta = new StringBuilder();
            try (Cursor m = db.rawQuery("SELECT name, value FROM metadata", null)) {
                while (m.moveToNext()) {
                    String name = m.getString(0), value = m.getString(1);
                    if (value == null) value = "";
                    if (value.length() > 48) value = value.substring(0, 48) + "…(" + value.length() + " Zeichen)";
                    meta.append(name).append('=').append(value.replace('\n', ' ')).append("; ");
                }
            }
            DiagLog.add(tag + "Kopfdaten: " + (meta.length() == 0 ? "KEINE (Tabelle metadata leer)" : meta.toString().trim()));
            try (Cursor z = db.rawQuery("SELECT min(zoom_level), max(zoom_level) FROM tiles", null)) {
                if (z.moveToFirst()) DiagLog.add(tag + "Zoomstufen in tiles: " + z.getInt(0) + " bis " + z.getInt(1));
            }
            if (hasPosition) {
                int zoom = 14;
                int x = MvtRoadDecoder.tileX(lon, zoom), y = MvtRoadDecoder.tileY(lat, zoom);
                int row = (1 << zoom) - 1 - y;
                try (Cursor t = db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                        new String[]{Integer.toString(zoom), Integer.toString(x), Integer.toString(row)})) {
                    if (!t.moveToFirst()) DiagLog.add(tag + "Kachel 14/" + x + "/" + y + " unter der Position: NICHT vorhanden");
                    else {
                        byte[] blob = t.getBlob(0);
                        boolean gzip = blob != null && blob.length > 2 && (blob[0] & 0xff) == 0x1f && (blob[1] & 0xff) == 0x8b;
                        int roads = blob == null ? 0 : MvtRoadDecoder.decode(blob, zoom, x, y).length / 4;
                        DiagLog.add(tag + "Kachel 14/" + x + "/" + y + ": " + (blob == null ? 0 : blob.length) + " B"
                                + (gzip ? " gzip" : " ohne gzip") + ", " + roads + " Straßensegmente in Ebene streets");
                    }
                }
            }
        } catch (Throwable e) {
            DiagLog.add(tag + "Lesefehler: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            if (db != null) try { db.close(); } catch (RuntimeException ignored) {}
        }
    }
}
