package de.ronny.pololauncher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Liest Kacheln, Schrift und Sprite fuer den {@link LocalTileServer} aus dem Deutschland-Paket. */
final class MbtilesTileLookup implements LocalTileServer.Tiles, LocalTileServer.Files {
    private final Context context;
    private final Map<String, SQLiteDatabase> databases = new HashMap<>();
    private final Set<String> broken = new HashSet<>();

    MbtilesTileLookup(Context context) { this.context = context.getApplicationContext(); }

    @Override public byte[] tile(String region, int z, int x, int y) {
        if (z < 0 || z > 14 || !Arrays.asList(GermanyRegionIndex.KEYS).contains(region)) return null;
        SQLiteDatabase db = open(region);
        if (db == null) return null;
        int row = (1 << z) - 1 - y;                       // MBTiles stores TMS rows
        try (Cursor c = db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                new String[]{Integer.toString(z), Integer.toString(x), Integer.toString(row)})) {
            return c.moveToFirst() ? c.getBlob(0) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private synchronized SQLiteDatabase open(String region) {
        SQLiteDatabase db = databases.get(region);
        if (db != null) return db;
        if (broken.contains(region)) return null;
        File file = GermanyOfflineStore.mapFile(context, region);
        try {
            if (file == null || !file.isFile()) throw new IOException("fehlt");
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            databases.put(region, db);
            return db;
        } catch (IOException | RuntimeException e) {
            broken.add(region);
            DiagLog.add("Kachelserver: " + region + " nicht zu öffnen: " + e.getClass().getSimpleName());
            return null;
        }
    }

    @Override public byte[] read(String relativePath) {
        File glyphs = GermanyOfflineStore.glyphRoot(context);
        if (glyphs == null || glyphs.getParentFile() == null) return null;
        if (!relativePath.startsWith("glyphs/") && !relativePath.startsWith("sprites/")) return null;
        File root = glyphs.getParentFile();
        File file = new File(root, relativePath);
        try {
            if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) || !file.isFile()) return null;
            try (InputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length() + 16, 1 << 20));
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    synchronized void close() {
        for (SQLiteDatabase db : databases.values()) try { db.close(); } catch (RuntimeException ignored) {}
        databases.clear();
    }
}
