package de.ronny.pololauncher;

import android.content.Context;

/** Shared persistent initial zoom for online and downloaded maps. */
final class MapPreferences {
    static final int MIN_ZOOM = 5;
    static final int MAX_ZOOM = 19;
    /** Prevents the followed vehicle position from ever opening at country scale. */
    static final int FOLLOW_MIN_ZOOM = 12;
    static final int DEFAULT_SETTLEMENT_ZOOM = 15;
    static final int DEFAULT_OUTSIDE_ZOOM = 13;
    private static final String NAME = "cockpit_map";
    private static final String KEY = "zoom";
    private static final String AUTO = "auto_settlement_zoom";
    private static final String SETTLEMENT_ZOOM = "settlement_zoom";
    private static final String OUTSIDE_ZOOM = "outside_zoom";
    private static final String ROAD_SNAP = "road_snap_test";
    private static final String VECTOR_STYLE = "vector_style";
    private static final String HEADING_UP = "heading_up";

    private MapPreferences() {}

    static int zoom(Context context) {
        int value = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY, 15);
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

    static boolean roadSnap(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(ROAD_SNAP, false);
    }

    static void setRoadSnap(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(ROAD_SNAP, enabled).apply();
    }

    static boolean autoZoom(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(AUTO, false);
    }

    static void setAutoZoom(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(AUTO, enabled).apply();
    }

    static String vectorStyle(Context context) {
        String style = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(VECTOR_STYLE, "liberty");
        switch (style == null ? "" : style) {
            case "bright": case "3d": case "liberty": return style;
            default: return "liberty";
        }
    }

    static void setVectorStyle(Context context, String style) {
        if (!"bright".equals(style) && !"3d".equals(style)) style = "liberty";
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(VECTOR_STYLE, style).apply();
    }

    static boolean headingUp(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(HEADING_UP, true);
    }

    static void setHeadingUp(Context context, boolean enabled) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(HEADING_UP, enabled).apply();
    }
}
