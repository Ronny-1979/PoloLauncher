package de.ronny.pololauncher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Road lines for the position matcher, read straight from the Shortbread map tiles that the
 * Germany package already holds on the device (MBTiles = SQLite, tiles gzip-compressed MVT).
 *
 * Unlike the compact traffic database this contains every road, so a car on a road without a
 * speed tag is still matched. It does not depend on what the screen shows: the tile under the
 * car is read directly. Decoded tiles are cached, so a normal drive decodes about one tile per
 * 1.5 km. All calls, including close(), run on the matcher's single worker thread; the UI thread never waits for tile I/O.
 */
final class GermanyRoadTiles implements AutoCloseable {
    private static final int MAX_ZOOM = 14;
    private static final int OPEN_DATABASES = 3;
    private static final int CACHED_TILES = 8;
    private static final double BOUNDS_MARGIN_DEGREES = 0.02d;

    private static final class Region {
        final String key;
        final File file;
        boolean metadataRead;
        double[] bounds;                 // minLon, minLat, maxLon, maxLat, or null if unknown
        int maxZoom = MAX_ZOOM;
        SQLiteDatabase db;
        boolean broken;
        Region(String key, File file) { this.key = key; this.file = file; }
    }

    private final ArrayList<Region> regions = new ArrayList<>();
    private final ArrayList<Region> open = new ArrayList<>();      // least recently used first
    private final LinkedHashMap<String, double[]> cache = new LinkedHashMap<String, double[]>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
            return size() > CACHED_TILES;
        }
    };
    private boolean closed;
    private volatile String lastRegion = "";

    private GermanyRoadTiles(Context context) {
        for (String key : GermanyOfflineStore.REGION_KEYS) {
            File file = GermanyOfflineStore.mapFile(context, key);
            if (file != null && file.isFile() && file.length() > 0L) regions.add(new Region(key, file));
        }
    }

    /** @return null when the Germany package has no map file at all. */
    static GermanyRoadTiles open(Context context) {
        GermanyRoadTiles tiles = new GermanyRoadTiles(context);
        return tiles.regions.isEmpty() ? null : tiles;
    }

    /** Not synchronized on purpose: the UI thread must never wait for a tile read. */
    String activeRegion() { return lastRegion.isEmpty() ? "keine" : lastRegion; }

    /** Reads every region's header once, so the first fix on the road does not pay for it. */
    synchronized void warmUp() {
        for (Region region : regions) {
            if (closed) return;
            readMetadata(region);
        }
    }

    /**
     * Road segments {lat1, lon1, lat2, lon2, ...} around a point, from all tiles that touch the
     * circle. Empty when the area is not covered (outside the package, unreadable file).
     */
    synchronized double[] segmentsNear(double lat, double lon, double radiusMetres) {
        if (closed) return new double[0];
        double dLat = radiusMetres / 111_320d;
        double dLon = radiusMetres / (111_320d * Math.max(0.2d, Math.cos(Math.toRadians(lat))));
        int zoom = MAX_ZOOM;
        int x0 = MvtRoadDecoder.tileX(lon - dLon, zoom), x1 = MvtRoadDecoder.tileX(lon + dLon, zoom);
        int y0 = MvtRoadDecoder.tileY(lat + dLat, zoom), y1 = MvtRoadDecoder.tileY(lat - dLat, zoom);
        double[][] parts = new double[(x1 - x0 + 1) * (y1 - y0 + 1)][];
        int total = 0, n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                double[] part = tile(zoom, x, y, lat, lon);
                parts[n++] = part;
                total += part.length;
            }
        }
        if (n == 1) return parts[0];
        double[] all = new double[total];
        int at = 0;
        for (double[] part : parts) {
            if (part == null) continue;
            System.arraycopy(part, 0, all, at, part.length);
            at += part.length;
        }
        return all;
    }

    private double[] tile(int zoom, int x, int y, double lat, double lon) {
        String key = zoom + "/" + x + "/" + y;
        double[] cached = cache.get(key);
        if (cached != null) return cached;
        double[] segments = new double[0];
        for (Region region : orderedRegions(lat, lon)) {
            byte[] blob = readBlob(region, zoom, x, y);
            if (blob == null) continue;
            segments = MvtRoadDecoder.decode(blob, zoom, x, y);
            lastRegion = GermanyOfflineStore.regionName(region.key);
            break;
        }
        cache.put(key, segments);      // also remembers "no tile here"
        return segments;
    }

    /** Regions whose bounds contain the point first (last used region first among those). */
    private ArrayList<Region> orderedRegions(double lat, double lon) {
        ArrayList<Region> list = new ArrayList<>();
        for (Region region : regions) {
            if (region.broken) continue;
            readMetadata(region);
            if (region.broken) continue;
            double[] b = region.bounds;
            boolean inside = b == null || (lon >= b[0] - BOUNDS_MARGIN_DEGREES && lon <= b[2] + BOUNDS_MARGIN_DEGREES
                    && lat >= b[1] - BOUNDS_MARGIN_DEGREES && lat <= b[3] + BOUNDS_MARGIN_DEGREES);
            if (!inside) continue;
            if (GermanyOfflineStore.regionName(region.key).equals(lastRegion)) list.add(0, region);
            else list.add(region);
        }
        return list;
    }

    private byte[] readBlob(Region region, int zoom, int x, int y) {
        int z = Math.min(zoom, region.maxZoom);
        if (z != zoom) return null;            // tiles of this region end below the zoom we need
        SQLiteDatabase db = database(region);
        if (db == null) return null;
        int row = (1 << zoom) - 1 - y;         // MBTiles rows are TMS (y grows northwards)
        try (Cursor c = db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                new String[] {Integer.toString(zoom), Integer.toString(x), Integer.toString(row)})) {
            return c.moveToFirst() ? c.getBlob(0) : null;
        } catch (RuntimeException e) {
            region.broken = true;
            closeDatabase(region);
            DiagLog.add("Straßenkacheln: " + region.key + " nicht lesbar: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private SQLiteDatabase database(Region region) {
        if (region.db != null) {
            open.remove(region);
            open.add(region);
            return region.db;
        }
        try {
            region.db = SQLiteDatabase.openDatabase(region.file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
        } catch (RuntimeException e) {
            region.broken = true;
            DiagLog.add("Straßenkacheln: " + region.key + " nicht zu öffnen: " + e.getClass().getSimpleName());
            return null;
        }
        open.add(region);
        while (open.size() > OPEN_DATABASES) closeDatabase(open.get(0));
        return region.db;
    }

    private void closeDatabase(Region region) {
        open.remove(region);
        if (region.db == null) return;
        try { region.db.close(); } catch (RuntimeException ignored) {}
        region.db = null;
    }

    private void readMetadata(Region region) {
        if (region.metadataRead || region.broken) return;
        region.metadataRead = true;
        SQLiteDatabase db = database(region);
        if (db == null) return;
        try (Cursor c = db.rawQuery("SELECT name, value FROM metadata WHERE name IN ('bounds','maxzoom')", null)) {
            while (c.moveToNext()) {
                String name = c.getString(0), value = c.getString(1);
                if ("bounds".equals(name)) region.bounds = parseBounds(value);
                else if ("maxzoom".equals(name)) region.maxZoom = parseInt(value, MAX_ZOOM);
            }
        } catch (RuntimeException ignored) {
            // No metadata table: keep unknown bounds, the tile lookup itself decides.
        }
    }

    static double[] parseBounds(String value) {
        if (value == null) return null;
        String[] p = value.split(",");
        if (p.length != 4) return null;
        try {
            double[] b = new double[4];
            for (int i = 0; i < 4; i++) b[i] = Double.parseDouble(p[i].trim());
            return b[0] < b[2] && b[1] < b[3] ? b : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (RuntimeException e) { return fallback; }
    }

    @Override public synchronized void close() {
        closed = true;
        for (Region region : new ArrayList<>(open)) closeDatabase(region);
        cache.clear();
    }
}
