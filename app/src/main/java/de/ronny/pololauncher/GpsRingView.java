package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * "Auf GPS-Position zentrieren"-Taste: ein runder Ring wie beim Info-Knopf ⓘ, innen ein Fadenkreuz.
 * Ohne Hintergrundfläche; ein dunkler Saum hält die Linien auf heller Karte lesbar.
 */
final class GpsRingView extends View {
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    GpsRingView(Context context, float density) {
        super(context);
        this.density = density <= 0f ? 1f : density;
        halo.setStyle(Paint.Style.STROKE);
        halo.setColor(Color.argb(110, 0, 0, 0));
        line.setStyle(Paint.Style.STROKE);
        line.setColor(GlassSkin.TEXT);
        halo.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float line1 = 1.8f * density;
        float ring = Math.min(w, h) / 2f - 5f * density;  // Ring, wie das ⓘ
        float dot = ring * 0.30f;                          // kleiner Kreis in der Mitte
        float tickIn = ring * 0.55f;                       // Fadenkreuz-Striche
        float tickOut = ring * 0.95f;
        draw(c, cx, cy, ring, dot, tickIn, tickOut, halo, line1 + 2.6f * density);
        draw(c, cx, cy, ring, dot, tickIn, tickOut, line, line1);
    }

    private void draw(Canvas c, float cx, float cy, float ring, float dot, float tIn, float tOut,
                      Paint p, float width) {
        p.setStrokeWidth(width);
        c.drawCircle(cx, cy, ring, p);
        c.drawCircle(cx, cy, dot, p);
        c.drawLine(cx - tOut, cy, cx - tIn, cy, p);
        c.drawLine(cx + tIn, cy, cx + tOut, cy, p);
        c.drawLine(cx, cy - tOut, cx, cy - tIn, p);
        c.drawLine(cx, cy + tIn, cx, cy + tOut, p);
    }
}
