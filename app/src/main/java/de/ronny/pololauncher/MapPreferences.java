package de.ronny.pololauncher;

import android.content.Context;

/** Persistent map settings (zoom, tilt, building height, colour mode). */
final class MapPreferences {
    static final String COLOR_AUTO = "auto";
    static final String COLOR_DAY = "day";
    static final String COLOR_NIGHT = "night";
    static final int MIN_ZOOM = 5;
    static final int MAX_ZOOM = 19;
    /** Prevents the followed vehicle position from ever opening at country scale. */
    static final int FOLLOW_MIN_ZOOM = 12;
    static final int DEFAULT_SETTLEMENT_ZOOM = 18;
    static final int DEFAULT_ZOOM = 16;
    static final int DEFAULT_OUTSIDE_ZOOM = 16;
    private static final String NAME = "cockpit_map";
    private static final String KEY = "zoom";
    private static final String DIRECT_BOUNDARY_ZOOM = "direct_boundary_zoom";
    private static final String SETTLEMENT_ZOOM = "settlement_zoom";
    private static final String OUTSIDE_ZOOM = "outside_zoom";
    private static final String SPEED_CAMERA_ALERTS = "speed_camera_alerts";
    private static final String TILT = "map_tilt_degrees";
    private static final String BUILDING_HEIGHT = "building_height_m";
    private static final String OFFLINE_TRANSPORT = "offline_transport";
    private static final String LAST_LAT = "last_known_lat";
    private static final String LAST_LON = "last_known_lon";
    private static final String LAST_BEARING = "last_known_bearing";
    private static final String LAST_ZOOM = "last_known_zoom";
    private static final String LAST_AREA = "last_known_area";
    private static final String HEADING_UP = "heading_up";
    private static final String COLOR_MODE = "color_mode";

    private MapPreferences() {}

    static int zoom(Context context) {
        int value = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_ZOOM);
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    static void setZoom(Context context, int value) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY, Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value))).apply();
    }

    static int clampFollowZoom(int value) {
        return Math.max(FOLLOW_MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    static int settlementZoom(Context context) {
        int value = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getInt(SETTLEMENT_ZOOM, DEFAULT_SETTLEMENT_ZOOM);
        return clampFollowZoom(value);
    }

    static void setSettlementZoom(Context context, int value) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putInt(SETTLEMENT_ZOOM, clampFollowZoom(value)).apply();
    }

    static int outsideZoom(Context context) {
        int value = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getInt(OUTSIDE_ZOOM, DEFAULT_OUTSIDE_ZOOM);
        return clampFollowZoom(value);
    }

    static void setOutsideZoom(Context context, int value) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putInt(OUTSIDE_ZOOM, clampFollowZoom(value)).apply();
    }

    /**
     * Road snapping is a permanent part of the position pipeline (no menu switch any more):
     * raw GPS drifts beside the road. The matchers still fall back to the raw fix whenever no
     * trustworthy road segment is found. Old "road_snap_test" preference values are ignored.
     */

    static boolean speedCameraAlerts(Context context) {
        // Intentionally off by default. The user has to enable the feature explicitly.
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getBoolean(SPEED_CAMERA_ALERTS, false);
    }

    static void setSpeedCameraAlerts(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(SPEED_CAMERA_ALERTS, enabled).apply();
    }

    static boolean directBoundaryZoom(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getBoolean(DIRECT_BOUNDARY_ZOOM, false);
    }

    static void setDirectBoundaryZoom(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(DIRECT_BOUNDARY_ZOOM, enabled).apply();
    }

    /** "direct" (mbtiles://) or "http" (local tile server): the way that last worked on this radio. */
    static String offlineTransport(Context context) {
        String t = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(OFFLINE_TRANSPORT, "direct");
        return "http".equals(t) ? "http" : "direct";
    }

    static void setOfflineTransport(Context context, String transport) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(OFFLINE_TRANSPORT, "http".equals(transport) ? "http" : "direct").apply();
    }

    /** Letzte bekannte Position (fuer die Auswahl der Offline-Pakete beim Start), oder null. */
    static double[] lastKnown(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        if (!p.contains(LAST_LAT) || !p.contains(LAST_LON)) return null;
        double lat = Double.longBitsToDouble(p.getLong(LAST_LAT, 0L));
        double lon = Double.longBitsToDouble(p.getLong(LAST_LON, 0L));
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90d || Math.abs(lon) > 180d) return null;
        return new double[]{lat, lon};
    }

    /** Letzter Kartenzustand (Position, Fahrtrichtung, Zoom, Ortslage), damit der Start dort weitermacht. */
    static void setLastState(Context context, double lat, double lon, float bearing, float zoom, MapArea area) {
        android.content.SharedPreferences.Editor e = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putLong(LAST_LAT, Double.doubleToRawLongBits(lat))
                .putLong(LAST_LON, Double.doubleToRawLongBits(lon))
                .putFloat(LAST_ZOOM, zoom);
        // An unknown area (no speed limit / settlement detected yet) must never overwrite the last known one.
        if (area == MapArea.SETTLEMENT || area == MapArea.OUTSIDE) e.putString(LAST_AREA, area.name());
        if (!Float.isNaN(bearing)) e.putFloat(LAST_BEARING, bearing);
        e.apply();
    }

    /** Gespeicherte Fahrtrichtung in Grad oder NaN. */
    static float lastBearing(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        float v = p.getFloat(LAST_BEARING, Float.NaN);
        return Float.isNaN(v) || v < 0f || v >= 360.5f ? Float.NaN : v;
    }

    /** Gespeicherter Zoom oder NaN. */
    static float lastZoom(Context context) {
        float v = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(LAST_ZOOM, Float.NaN);
        return Float.isNaN(v) ? Float.NaN : Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, v));
    }

    static MapArea lastArea(Context context) {
        String name = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString(LAST_AREA, "");
        if (MapArea.SETTLEMENT.name().equals(name)) return MapArea.SETTLEMENT;
        if (MapArea.OUTSIDE.name().equals(name)) return MapArea.OUTSIDE;
        return MapArea.UNKNOWN;
    }

    static final int DEFAULT_TILT = 45;
    static final int MAX_TILT = 60;
    static final int DEFAULT_BUILDING_HEIGHT = 9;
    static final int MAX_BUILDING_HEIGHT = 30;

    /** Camera tilt in degrees (0 = flat, from above). */
    static int tilt(Context context) {
        int v = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(TILT, DEFAULT_TILT);
        return Math.max(0, Math.min(MAX_TILT, v));
    }

    static void setTilt(Context context, int degrees) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putInt(TILT, Math.max(0, Math.min(MAX_TILT, degrees))).apply();
    }

    /** Standard height of buildings in metres (the offline data has no heights); 0 = flat buildings. */
    static int buildingHeight(Context context) {
        int v = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(BUILDING_HEIGHT, DEFAULT_BUILDING_HEIGHT);
        return Math.max(0, Math.min(MAX_BUILDING_HEIGHT, v));
    }

    static void setBuildingHeight(Context context, int metres) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putInt(BUILDING_HEIGHT, Math.max(0, Math.min(MAX_BUILDING_HEIGHT, metres))).apply();
    }

    static String colorMode(Context context) {
        String mode = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString(COLOR_MODE, COLOR_AUTO);
        if (COLOR_DAY.equals(mode) || COLOR_NIGHT.equals(mode)) return mode;
        return COLOR_AUTO;
    }

    static void setColorMode(Context context, String mode) {
        if (!COLOR_DAY.equals(mode) && !COLOR_NIGHT.equals(mode)) mode = COLOR_AUTO;
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
                .putString(COLOR_MODE, mode).apply();
    }

    static boolean headingUp(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(HEADING_UP, true);
    }

    static void setHeadingUp(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(HEADING_UP, enabled).apply();
    }
}
