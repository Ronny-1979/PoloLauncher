package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * Zeigt das Senderlogo mit abgerundeten Ecken. Beschnitten wird genau das tatsächlich gezeichnete
 * Bild (nicht die ganze View), damit auch nicht quadratische Logos sauber in die runde Kachel passen.
 */
final class RoundedArtworkView extends ImageView {
    private final Path clip = new Path();
    private final RectF shown = new RectF();
    private final float radiusPx;

    RoundedArtworkView(Context context, float radiusPx) {
        super(context);
        this.radiusPx = radiusPx;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable d = getDrawable();
        if (d == null) {
            super.onDraw(canvas);
            return;
        }
        shown.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        getImageMatrix().mapRect(shown);
        shown.offset(getPaddingLeft(), getPaddingTop());
        shown.intersect(0, 0, getWidth(), getHeight());
        float r = Math.min(radiusPx, Math.min(shown.width(), shown.height()) / 2f);
        clip.rewind();
        clip.addRoundRect(shown, r, r, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }
}
