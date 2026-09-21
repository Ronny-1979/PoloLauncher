package de.ronny.pololauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Reusable warm cockpit colors and drawable surfaces; no image assets or network needed. */
public final class CopperSkin {
    public static int BG = Color.rgb(19, 16, 16);
    public static int PANEL = Color.rgb(35, 29, 27);
    public static int PANEL_DARK = Color.rgb(25, 23, 23);
    public static int GOLD = Color.rgb(240, 190, 120);
    public static int MUTED = Color.rgb(199, 181, 163);
    public static int CREAM = Color.rgb(255, 246, 229);
    public static final int GREEN = Color.rgb(156, 215, 169);
    public static final String[] NAMES = {"Kupfer (Standard)", "Nachtblau", "Smaragd", "Bordeaux"};
    private static int[] backdrop = {Color.rgb(42, 32, 27), BG, Color.rgb(13, 13, 16)};
    private static int[] raised = {Color.rgb(73, 55, 42), Color.rgb(40, 33, 30), Color.rgb(27, 25, 26)};
    private static int[] inset = {Color.rgb(52, 43, 37), Color.rgb(29, 27, 27), Color.rgb(21, 22, 25)};
    private static int[] idle = {Color.rgb(75, 58, 45), Color.rgb(40, 34, 31), Color.rgb(27, 26, 27)};
    private static int[] active = {Color.rgb(163, 113, 66), Color.rgb(91, 61, 43), Color.rgb(44, 35, 31)};
    private static int brightBorder = Color.rgb(158, 112, 71);
    private static int darkBorder = Color.rgb(111, 83, 61);
    private static int idleBorder = Color.rgb(126, 95, 65);

    private CopperSkin() {}

    /** Saved selection is local to the launcher; an update never overwrites it. */
    public static int selection(Context c) {
        int n = c.getSharedPreferences("cockpit_appearance", Context.MODE_PRIVATE).getInt("skin", 0);
        return n >= 0 && n < NAMES.length ? n : 0;
    }

    public static void select(Context c, int n) {
        if (n < 0 || n >= NAMES.length) return;
        c.getSharedPreferences("cockpit_appearance", Context.MODE_PRIVATE).edit().putInt("skin", n).apply();
        apply(c);
    }

    public static void apply(Context c) {
        int n = selection(c);
        int[] palettes = {0xfff0be78, 0xff76bfff, 0xff84d9b6, 0xffefa1ba};
        GOLD = palettes[n];
        CREAM = n == 0 ? 0xfffff6e5 : 0xfff4f6fb;
        MUTED = n == 0 ? 0xffc7b5a3 : 0xffb9c3d0;
        int tint = n == 0 ? 0xff594031 : n == 1 ? 0xff233b59 : n == 2 ? 0xff245044 : 0xff573145;
        BG = n == 0 ? 0xff131010 : 0xff10151b;
        PANEL = n == 0 ? 0xff231d1b : 0xff1b222a;
        PANEL_DARK = n == 0 ? 0xff191717 : 0xff151b22;
        backdrop = n == 0 ? new int[]{0xff2a201b, BG, 0xff0d0d10}
                : new int[]{tint, BG, 0xff0d1016};
        raised = n == 0 ? new int[]{0xff49372a, 0xff28211e, 0xff1b191a}
                : new int[]{tint, PANEL, PANEL_DARK};
        inset = n == 0 ? new int[]{0xff342b25, 0xff1d1b1b, 0xff151619}
                : new int[]{tint, PANEL_DARK, 0xff11151b};
        idle = n == 0 ? new int[]{0xff4b3a2d, 0xff28221f, 0xff1b1a1b}
                : new int[]{tint, PANEL, PANEL_DARK};
        active = n == 0 ? new int[]{0xffa37142, 0xff5b3d2b, 0xff2c231f}
                : new int[]{GOLD, tint, PANEL_DARK};
        brightBorder = n == 0 ? 0xff9e7047 : GOLD;
        darkBorder = n == 0 ? 0xff6f533d : tint;
        idleBorder = n == 0 ? 0xff7e5f41 : GOLD;
    }

    /**
     * Dialogs in the dark cockpit look. The app theme is Material Light, whose default AlertDialog
     * is light: cream text and links on it would be unreadable.
     */
    public static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    public static GradientDrawable background() {
        return gradient(GradientDrawable.Orientation.TL_BR,
                backdrop, 0, 0, 0);
    }

    public static GradientDrawable panel(Context c, boolean bright) {
        return gradient(GradientDrawable.Orientation.TL_BR,
                bright ? raised : inset,
                dp(c, 17), bright ? brightBorder : darkBorder, dp(c, 1));
    }

    public static GradientDrawable button(Context c, boolean selected) {
        return gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                selected ? active : idle,
                dp(c, 14), selected ? GOLD : idleBorder, dp(c, 1));
    }

    public static RippleDrawable touch(Context c, boolean selected) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(100, 255, 217, 163)), button(c, selected), null);
    }

    private static GradientDrawable gradient(GradientDrawable.Orientation orientation, int[] colors,
                                             int radius, int stroke, int width) {
        GradientDrawable d = new GradientDrawable(orientation, colors);
        d.setCornerRadius(radius);
        if (width > 0) d.setStroke(width, stroke);
        return d;
    }

    private static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
