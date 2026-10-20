package de.ronny.pololauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * CarPlay-inspired glass surfaces.
 *
 * Frosted glass is now the permanent rendering mode. The selected wallpaper supplies nearly all
 * colour; each surface adds only a faint dark/clear glaze, edge highlight and cached blurred
 * wallpaper underneath. No legacy non-blur branch remains.
 */
final class GlassSkin {
    static final int TEXT = 0xfff7fbff;
    static final int MUTED = 0xffb8c7db;
    static final int ACCENT = 0xff7dccff;
    static final int GREEN = 0xff62e68d;
    static final int WARNING = 0xffff8b72;

    private GlassSkin() {}

    /** Dark dialog matching the Glass UI. */
    static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    static Drawable background(Context c) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff081735, 0xff12386d, 0xff0a2653, 0xff020816});
    }

    static Drawable panel(Context c, boolean bright) {
        return layered(c, 22,
                bright
                        ? new int[]{0x0cffffff, 0x08000000, 0x16000000}
                        : new int[]{0x08ffffff, 0x08000000, 0x18000000},
                bright ? 0x88ffffff : 0x72ffffff,
                bright ? 0x1cffffff : 0x14ffffff);
    }

    static Drawable sidebar(Context c) {
        return layered(c, 24,
                new int[]{0x0affffff, 0x08000000, 0x19000000},
                0x80ffffff, 0x16ffffff);
    }

    static Drawable chip(Context c) {
        return layered(c, 15,
                new int[]{0x0cffffff, 0x06000000, 0x16000000},
                0x76ffffff, 0x14ffffff);
    }

    static Drawable subtleChip(Context c) {
        return layered(c, 13,
                new int[]{0x08ffffff, 0x05000000, 0x12000000},
                0x60ffffff, 0x10ffffff);
    }

    static RippleDrawable touch(Context c, boolean selected) {
        Drawable base = layered(c, 17,
                selected
                        ? new int[]{0x18ffffff, 0x09000000, 0x18000000}
                        : new int[]{0x0cffffff, 0x06000000, 0x14000000},
                selected ? 0x92ffffff : 0x6cffffff,
                selected ? 0x20ffffff : 0x12ffffff);
        return new RippleDrawable(ColorStateList.valueOf(
                Color.argb(54, 255, 255, 255)), base, null);
    }

    static Drawable transparentBorder(Context c, int radiusDp) {
        GradientDrawable outer = new GradientDrawable();
        outer.setColor(Color.TRANSPARENT);
        outer.setCornerRadius(dp(c, radiusDp));
        outer.setStroke(dp(c, 1), 0xa8ffffff);
        GradientDrawable inner = new GradientDrawable();
        inner.setColor(Color.TRANSPARENT);
        inner.setCornerRadius(dp(c, Math.max(1, radiusDp - 1)));
        inner.setStroke(dp(c, 1), 0x26ffffff);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{outer, inner});
        layers.setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
        return layers;
    }

    private static Drawable layered(Context c, int radiusDp, int[] colors, int edge, int innerEdge) {
        GradientDrawable edgeLayer = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x12ffffff, 0x04ffffff, 0x08000000});
        edgeLayer.setCornerRadius(dp(c, radiusDp));
        edgeLayer.setStroke(dp(c, 1), edge);

        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        glass.setCornerRadius(dp(c, Math.max(1, radiusDp - 1)));
        glass.setStroke(dp(c, 1), innerEdge);

        GradientDrawable reflection = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x0cffffff, 0x03ffffff, 0x00000000});
        reflection.setCornerRadius(dp(c, Math.max(1, radiusDp - 2)));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{edgeLayer, glass, reflection});
        int one = dp(c, 1);
        layers.setLayerInset(1, one, one, one, one);
        layers.setLayerInset(2, dp(c, 2), dp(c, 2), dp(c, 2),
                dp(c, Math.max(10, radiusDp / 2f)));
        return new FrostedGlassDrawable(c, radiusDp, layers);
    }

    private static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
