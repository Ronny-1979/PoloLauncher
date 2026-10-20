package de.ronny.pololauncher;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class TrafficDataStore {
    private static final int SCHEMA = 1;

    private TrafficDataStore() {}

    static File folder(Context context) {
        File base = context.getExternalFilesDir(null);
        return base == null ? null : new File(base, "traffic_data");
    }

    static File dbFile(Context context, String region) {
        File dir = folder(context);
        return dir == null || GermanyRegions.indexOf(region) < 0
                ? null : new File(dir, region + ".traffic.sqlite");
    }

    static File tempDbFile(Context context, String region) {
        File dir = folder(context);
        return dir == null || GermanyRegions.indexOf(region) < 0
                ? null : new File(dir, region + ".traffic.sqlite.build");
    }

    static File pbfFile(Context context, String region) {
        File dir = folder(context);
        return dir == null || GermanyRegions.indexOf(region) < 0
                ? null : new File(dir, region + ".osm.pbf.download");
    }

    static Reader open(Context context, String region) {
        File file = dbFile(context, region);
        if (file == null || !file.isFile() || file.length() < 16_384L) return null;
        try {
            Reader reader = new Reader(SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY), region);
            if (!reader.valid()) {
                reader.close();
                return null;
            }
            return reader;
        } catch (Throwable error) {
            DiagLog.add("Verkehrsdaten nicht lesbar: " + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean atomicActivate(Context context, String region, File built) {
        File target = dbFile(context, region);
        if (target == null || built == null || !built.isFile()) return false;
        File dir = target.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) return false;
        File backup = new File(dir, region + ".traffic.sqlite.bak");
        if (backup.exists()) backup.delete();
        if (target.exists() && !target.renameTo(backup)) return false;
        boolean ok = built.renameTo(target);
        if (!ok && backup.exists()) backup.renameTo(target);
        if (ok && backup.exists()) backup.delete();
        return ok;
    }

    static final class Builder implements AutoCloseable, TrafficPbfScanner.Writer {
        private final SQLiteDatabase db;
        private final SQLiteStatement road;
        private final SQLiteStatement roadIndex;
        private final SQLiteStatement camera;
        private long roadCount;
        private long cameraCount;
        private double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        private double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        private boolean transactionOpen;

        Builder(File file) {
            if (file == null) throw new IllegalArgumentException("Kein Verkehrsdaten-Ziel");
            File parent = file.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs()))
                throw new IllegalStateException("Verkehrsdaten-Ordner nicht verfügbar");
            if (file.exists() && !file.delete())
                throw new IllegalStateException("Alte temporäre Verkehrsdaten nicht löschbar");

            db = SQLiteDatabase.openOrCreateDatabase(file.getAbsolutePath(), null);
            // PRAGMA statements are queries on some Android/SQLite builds (journal_mode
            // always returns the selected mode). SQLiteDatabase.execSQL() rejects such
            // statements with "Queries can be performed using query/rawQuery only".
            // Execute all build PRAGMAs via rawQuery and force the cursor once so the
            // setting is actually applied on vendor Android builds as well.
            applyPragma("PRAGMA journal_mode=OFF");
            applyPragma("PRAGMA synchronous=OFF");
            applyPragma("PRAGMA temp_store=MEMORY");
            db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
            db.execSQL("CREATE TABLE roads(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "way_id INTEGER NOT NULL,lat1 REAL NOT NULL,lon1 REAL NOT NULL,"
                    + "lat2 REAL NOT NULL,lon2 REAL NOT NULL,highway TEXT,"
                    + "maxspeed TEXT,forward TEXT,backward TEXT,zone TEXT,source TEXT,speed_type TEXT)");
            try {
                db.execSQL("CREATE VIRTUAL TABLE road_index USING rtree("
                        + "id,min_lat,max_lat,min_lon,max_lon)");
            } catch (SQLException noRtree) {
                db.execSQL("CREATE TABLE road_index(id INTEGER PRIMARY KEY,"
                        + "min_lat REAL,max_lat REAL,min_lon REAL,max_lon REAL)");
                db.execSQL("CREATE INDEX road_idx_lat ON road_index(min_lat,max_lat)");
                db.execSQL("CREATE INDEX road_idx_lon ON road_index(min_lon,max_lon)");
            }
            db.execSQL("CREATE TABLE cameras(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "osm_id INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL)");
            db.execSQL("CREATE INDEX camera_lat ON cameras(lat)");
            db.execSQL("CREATE INDEX camera_lon ON cameras(lon)");

            road = db.compileStatement("INSERT INTO roads(way_id,lat1,lon1,lat2,lon2,"
                    + "highway,maxspeed,forward,backward,zone,source,speed_type)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)");
            roadIndex = db.compileStatement("INSERT INTO road_index(id,min_lat,max_lat,min_lon,max_lon)"
                    + " VALUES(?,?,?,?,?)");
            camera = db.compileStatement("INSERT INTO cameras(osm_id,lat,lon) VALUES(?,?,?)");
            db.beginTransaction();
            transactionOpen = true;
        }

        @Override public void addSegment(long wayId, double lat1, double lon1,
                                         double lat2, double lon2, String highway,
                                         String maxspeed, String forward, String backward,
                                         String zone, String source, String type) {
            road.clearBindings();
            road.bindLong(1, wayId);
            road.bindDouble(2, lat1); road.bindDouble(3, lon1);
            road.bindDouble(4, lat2); road.bindDouble(5, lon2);
            bind(road, 6, highway); bind(road, 7, maxspeed);
            bind(road, 8, forward); bind(road, 9, backward);
            bind(road, 10, zone); bind(road, 11, source); bind(road, 12, type);
            long id = road.executeInsert();

            roadIndex.clearBindings();
            roadIndex.bindLong(1, id);
            roadIndex.bindDouble(2, Math.min(lat1, lat2));
            roadIndex.bindDouble(3, Math.max(lat1, lat2));
            roadIndex.bindDouble(4, Math.min(lon1, lon2));
            roadIndex.bindDouble(5, Math.max(lon1, lon2));
            roadIndex.executeInsert();
            include(lat1, lon1);
            include(lat2, lon2);
            roadCount++;
        }

        @Override public void addCamera(long nodeId, double lat, double lon) {
            camera.clearBindings();
            camera.bindLong(1, nodeId);
            camera.bindDouble(2, lat);
            camera.bindDouble(3, lon);
            camera.executeInsert();
            include(lat, lon);
            cameraCount++;
        }

        void finish(String region, long sourceModified, long sourceSize, String roadProfile) {
            putMeta("schema", Integer.toString(SCHEMA));
            putMeta("region", region);
            putMeta("source_modified", Long.toString(sourceModified));
            putMeta("source_size", Long.toString(sourceSize));
            putMeta("road_profile", roadProfile == null ? "speed" : roadProfile);
            putMeta("built_at", Long.toString(System.currentTimeMillis()));
            putMeta("road_segments", Long.toString(roadCount));
            putMeta("cameras", Long.toString(cameraCount));
            if (Double.isFinite(minLat) && Double.isFinite(maxLat)
                    && Double.isFinite(minLon) && Double.isFinite(maxLon)) {
                putMeta("min_lat", Double.toString(minLat));
                putMeta("max_lat", Double.toString(maxLat));
                putMeta("min_lon", Double.toString(minLon));
                putMeta("max_lon", Double.toString(maxLon));
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            transactionOpen = false;
            db.execSQL("ANALYZE");
        }


        private void applyPragma(String sql) {
            try (Cursor cursor = db.rawQuery(sql, null)) {
                // SQLiteCursor is lazy; touching the first row forces execution.
                cursor.moveToFirst();
            }
        }

        private void include(double lat, double lon) {
            minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon); maxLon = Math.max(maxLon, lon);
        }

        private void putMeta(String key, String value) {
            db.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)",
                    new Object[]{key, value == null ? "" : value});
        }

        private static void bind(SQLiteStatement statement, int index, String value) {
            if (value == null) statement.bindNull(index);
            else statement.bindString(index, value);
        }

        @Override public void close() {
            try {
                if (transactionOpen) {
                    db.endTransaction();
                    transactionOpen = false;
                }
            } catch (Throwable ignored) {}
            try { road.close(); } catch (Throwable ignored) {}
            try { roadIndex.close(); } catch (Throwable ignored) {}
            try { camera.close(); } catch (Throwable ignored) {}
            try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /** Lat/lon box of a given radius (metres) around a point, as SQL bind arguments. */
    private static final class Window {
        final double minLat, maxLat, minLon, maxLon;
        Window(double lat, double lon, double metres) {
            double latDelta = metres / 110_540d;
            double cos = Math.max(0.2d, Math.cos(Math.toRadians(lat)));
            double lonDelta = metres / (111_320d * cos);
            minLat = lat - latDelta; maxLat = lat + latDelta;
            minLon = lon - lonDelta; maxLon = lon + lonDelta;
        }
        String[] args() {
            return new String[]{Double.toString(minLat), Double.toString(maxLat),
                    Double.toString(minLon), Double.toString(maxLon)};
        }
    }

    static final class Bounds {
        final double minLat, maxLat, minLon, maxLon;
        Bounds(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat; this.maxLat = maxLat;
            this.minLon = minLon; this.maxLon = maxLon;
        }

        boolean mightContain(double lat, double lon, double metres) {
            if (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
                    || !Double.isFinite(minLon) || !Double.isFinite(maxLon)) return true;
            Window w = new Window(lat, lon, metres);
            return w.maxLat >= minLat && w.minLat <= maxLat
                    && w.maxLon >= minLon && w.minLon <= maxLon;
        }
    }

    static final class Reader implements AutoCloseable {
        private final SQLiteDatabase db;
        private final String expectedRegion;
        private final int cameraCount;
        private final double minLat, maxLat, minLon, maxLon;

        Reader(SQLiteDatabase db, String expectedRegion) {
            this.db = db;
            this.expectedRegion = expectedRegion;
            this.cameraCount = parseInt(meta("cameras"), 0);
            this.minLat = parseDouble(meta("min_lat"), Double.NaN);
            this.maxLat = parseDouble(meta("max_lat"), Double.NaN);
            this.minLon = parseDouble(meta("min_lon"), Double.NaN);
            this.maxLon = parseDouble(meta("max_lon"), Double.NaN);
        }

        boolean valid() {
            return Integer.toString(SCHEMA).equals(meta("schema"))
                    && expectedRegion.equals(meta("region"));
        }

        Bounds bounds() { return new Bounds(minLat, maxLat, minLon, maxLon); }

        boolean mightContain(double lat, double lon, double metres) {
            if (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
                    || !Double.isFinite(minLon) || !Double.isFinite(maxLon)) return true;
            Window w = new Window(lat, lon, metres);
            return w.maxLat >= minLat && w.minLat <= maxLat
                    && w.maxLon >= minLon && w.minLon <= maxLon;
        }

        /**
         * Cheap coverage probe used by the Germany regional reader. This deliberately asks
         * whether this extract contains ANY traffic data near the current position, rather
         * than treating the rectangular extract bounds as the political state boundary.
         */
        boolean hasTrafficNear(double lat, double lon, double metres) {
            if (!mightContain(lat, lon, metres)) return false;
            Window window = new Window(lat, lon, metres);
            String[] args = window.args();
            try (Cursor road = db.rawQuery(
                    "SELECT 1 FROM road_index WHERE max_lat>=? AND min_lat<=? "
                            + "AND max_lon>=? AND min_lon<=? LIMIT 1", args)) {
                if (road.moveToFirst()) return true;
            }
            if (cameraCount <= 0) return false;
            try (Cursor camera = db.rawQuery(
                    "SELECT 1 FROM cameras WHERE lat BETWEEN ? AND ? "
                            + "AND lon BETWEEN ? AND ? LIMIT 1", args)) {
                return camera.moveToFirst();
            }
        }

        /** All road segments near the fix that pass the distance and heading filter, best first. */
        List<RoadSnapSelector.Candidate> findRoadCandidates(Location source, double maxDistance,
                                                            double maxDirectionError, int limit) {
            double lat = source.getLatitude();
            double lon = source.getLongitude();
            Window window = new Window(lat, lon, Math.max(220d, maxDistance));
            boolean directionKnown = source.hasBearing() && source.hasSpeed()
                    && source.getSpeed() >= 2.5f
                    && (!source.hasBearingAccuracy() || source.getBearingAccuracyDegrees() <= 40f);
            ArrayList<RoadSnapSelector.Candidate> found = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT r.lat1,r.lon1,r.lat2,r.lon2 "
                            + "FROM road_index i JOIN roads r ON r.id=i.id "
                            + "WHERE i.max_lat>=? AND i.min_lat<=? "
                            + "AND i.max_lon>=? AND i.min_lon<=? LIMIT 2500",
                    window.args())) {
                while (cursor.moveToNext()) {
                    double lat1 = cursor.getDouble(0), lon1 = cursor.getDouble(1);
                    double lat2 = cursor.getDouble(2), lon2 = cursor.getDouble(3);
                    RoadGeometry.Projection p = RoadGeometry.project(lat1, lon1, lat2, lon2, lat, lon);
                    RoadSnapSelector.Candidate c = RoadSnapCandidates.build(
                            lat1, lon1, lat2, lon2, p, source, directionKnown, maxDistance,
                            maxDirectionError, false);
                    if (c != null) found.add(c);
                }
            }
            return RoadSnapCandidates.best(found, limit);
        }

        SpeedMatch findSpeed(Location source, double maxDistance, double maxDirectionError) {
            double lat = source.getLatitude();
            double lon = source.getLongitude();
            Window window = new Window(lat, lon, Math.max(220d, maxDistance));

            boolean directionKnown = source.hasBearing() && source.hasSpeed()
                    && source.getSpeed() >= 2.5f
                    && (!source.hasBearingAccuracy()
                        || source.getBearingAccuracyDegrees() <= 40f);

            Candidate best = null;
            double nearest = Double.POSITIVE_INFINITY;
            try (Cursor cursor = db.rawQuery(
                    "SELECT r.lat1,r.lon1,r.lat2,r.lon2,r.highway,r.maxspeed,"
                            + "r.forward,r.backward,r.zone,r.source,r.speed_type "
                            + "FROM road_index i JOIN roads r ON r.id=i.id "
                            + "WHERE i.max_lat>=? AND i.min_lat<=? "
                            + "AND i.max_lon>=? AND i.min_lon<=? LIMIT 2500",
                    window.args())) {
                while (cursor.moveToNext()) {
                    double lat1 = cursor.getDouble(0), lon1 = cursor.getDouble(1);
                    double lat2 = cursor.getDouble(2), lon2 = cursor.getDouble(3);
                    RoadGeometry.Projection projection = RoadGeometry.project(
                            lat1, lon1, lat2, lon2, lat, lon);
                    nearest = Math.min(nearest, projection.distanceMetres);
                    if (projection.distanceMetres > maxDistance) continue;

                    double axisError = directionKnown
                            ? RoadGeometry.axisDifference(source.getBearing(), projection.roadBearing)
                            : 0d;
                    if (directionKnown && axisError > maxDirectionError) continue;

                    double score = projection.distanceMetres + axisError * 0.35d;
                    if (best != null && score >= best.score) continue;

                    String highway = text(cursor, 4);
                    String max = text(cursor, 5);
                    String forward = text(cursor, 6);
                    String backward = text(cursor, 7);
                    String zone = text(cursor, 8);
                    String speedSource = text(cursor, 9);
                    String type = text(cursor, 10);
                    boolean forwardTravel = !directionKnown
                            || directionDifference(source.getBearing(), projection.roadBearing) <= 90d;
                    int resolved = SpeedLimitValue.resolve(max, forward, backward, zone,
                            speedSource, type, directionKnown, forwardTravel);
                    if (resolved == SpeedLimitValue.UNKNOWN) continue;
                    best = new Candidate(score, highway,
                            rawTag(directionKnown, forwardTravel, max, forward, backward,
                                    zone, speedSource, type), resolved);
                }
            }

            double nearestValue = Double.isFinite(nearest) ? nearest : Double.NaN;
            return best == null
                    ? new SpeedMatch(SpeedLimitValue.UNKNOWN, nearestValue,
                        "", "", false)
                    : new SpeedMatch(best.speed, nearestValue,
                        best.highway, best.rawTag, true);
        }

        List<CameraPoint> camerasNear(double lat, double lon, double metres) {
            Window window = new Window(lat, lon, metres);
            ArrayList<CameraPoint> result = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT osm_id,lat,lon FROM cameras "
                            + "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 512",
                    window.args())) {
                while (cursor.moveToNext())
                    result.add(new CameraPoint(cursor.getLong(0),
                            cursor.getDouble(1), cursor.getDouble(2)));
            }
            return result;
        }

        double[] roadAxesNear(double lat, double lon, double metres) {
            Window window = new Window(lat, lon, metres);
            ArrayList<Axis> candidates = new ArrayList<>();
            double nearest = Double.POSITIVE_INFINITY;
            try (Cursor cursor = db.rawQuery(
                    "SELECT r.lat1,r.lon1,r.lat2,r.lon2 FROM road_index i "
                            + "JOIN roads r ON r.id=i.id "
                            + "WHERE i.max_lat>=? AND i.min_lat<=? AND i.max_lon>=? AND i.min_lon<=? "
                            + "LIMIT 1000",
                    window.args())) {
                while (cursor.moveToNext()) {
                    RoadGeometry.Projection p = RoadGeometry.project(
                            cursor.getDouble(0), cursor.getDouble(1),
                            cursor.getDouble(2), cursor.getDouble(3), lat, lon);
                    if (p.distanceMetres <= metres) {
                        nearest = Math.min(nearest, p.distanceMetres);
                        candidates.add(new Axis(p.distanceMetres, p.roadBearing));
                    }
                }
            }
            if (!Double.isFinite(nearest)) return new double[0];
            ArrayList<Double> axes = new ArrayList<>();
            for (Axis candidate : candidates) {
                if (candidate.distance > nearest + 8d) continue;
                boolean duplicate = false;
                for (double existing : axes)
                    if (SpeedCameraDecision.roadAxisDifference(existing, candidate.bearing) < 8d) {
                        duplicate = true; break;
                    }
                if (!duplicate) axes.add(candidate.bearing);
                if (axes.size() >= 8) break;
            }
            double[] result = new double[axes.size()];
            for (int i = 0; i < axes.size(); i++) result[i] = axes.get(i);
            return result;
        }

        String meta(String key) {
            try (Cursor cursor = db.rawQuery("SELECT v FROM meta WHERE k=?",
                    new String[]{key})) {
                return cursor.moveToFirst() ? cursor.getString(0) : "";
            }
        }

        @Override public void close() {
            try { db.close(); } catch (Throwable ignored) {}
        }

        private static String text(Cursor cursor, int index) {
            return cursor.isNull(index) ? "" : cursor.getString(index);
        }

        private static int parseInt(String value, int fallback) {
            try { return Integer.parseInt(value); }
            catch (Exception ignored) { return fallback; }
        }

        private static double parseDouble(String value, double fallback) {
            try { return Double.parseDouble(value); }
            catch (Exception ignored) { return fallback; }
        }

        private static String rawTag(boolean directionKnown, boolean forwardTravel,
                                     String max, String forward, String backward,
                                     String zone, String source, String type) {
            if (directionKnown) {
                String d = forwardTravel ? forward : backward;
                if (!d.isEmpty()) return (forwardTravel ? "maxspeed:forward=" : "maxspeed:backward=") + d;
            }
            if (!max.isEmpty()) return "maxspeed=" + max;
            if (!zone.isEmpty()) return "zone:maxspeed=" + zone;
            if (!source.isEmpty()) return "source:maxspeed=" + source;
            if (!type.isEmpty()) return "maxspeed:type=" + type;
            return "";
        }

        private static double directionDifference(double a, double b) {
            double na = (a % 360d + 360d) % 360d;
            double nb = (b % 360d + 360d) % 360d;
            return Math.abs((na - nb + 540d) % 360d - 180d);
        }

        private static final class Candidate {
            final double score;
            final String highway, rawTag;
            final int speed;
            Candidate(double score, String highway, String rawTag, int speed) {
                this.score = score; this.highway = highway; this.rawTag = rawTag; this.speed = speed;
            }
        }

        private static final class Axis {
            final double distance, bearing;
            Axis(double distance, double bearing) { this.distance = distance; this.bearing = bearing; }
        }
    }

    static final class SpeedMatch {
        final int speed;
        final double nearestRoadMetres;
        final String roadType;
        final String rawSpeedTag;
        final boolean matchedRoad;
        SpeedMatch(int speed, double nearestRoadMetres, String roadType,
                   String rawSpeedTag, boolean matchedRoad) {
            this.speed = speed;
            this.nearestRoadMetres = nearestRoadMetres;
            this.roadType = roadType;
            this.rawSpeedTag = rawSpeedTag;
            this.matchedRoad = matchedRoad;
        }
    }

    static final class CameraPoint {
        final long id;
        final double lat, lon;
        CameraPoint(long id, double lat, double lon) {
            this.id = id; this.lat = lat; this.lon = lon;
        }
    }
}
