package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.widget.TextView;

/**
 * DAB previous/next icon drawn as geometry.
 *
 * It intentionally keeps the same visual character as the former ◀◀ / ▶▶ buttons:
 * two broad filled white triangles. The only addition is the narrow outer skip bar.
 * No emoji or font-dependent media symbol is used.
 */
final class DabSkipButtonView extends TextView {
    static final int PREVIOUS = -1;
    static final int NEXT = 1;

    private final int direction;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DabSkipButtonView(Context context, int direction) {
        super(context);
        this.direction = direction < 0 ? PREVIOUS : NEXT;
        setGravity(Gravity.CENTER);
        setText("");
        setIncludeFontPadding(false);
        setPadding(0, 0, 0, 0);
        iconPaint.setColor(GlassSkin.TEXT);
        iconPaint.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;

        // Same chunky proportions as the previous double arrows.
        float h = 15.5f * d;
        float triW = 9.0f * d;
        float triGap = 1.3f * d;
        float barW = 2.0f * d;
        float barGap = 2.0f * d;

        float groupW = triW * 2f + triGap + barGap + barW;
        float left = cx - groupW * 0.5f;
        float top = cy - h * 0.5f;
        float bottom = cy + h * 0.5f;

        if (direction == PREVIOUS) {
            drawBar(canvas, left, top, bottom, barW);
            float first = left + barW + barGap;
            drawLeftTriangle(canvas, first, top, bottom, triW);
            drawLeftTriangle(canvas, first + triW + triGap, top, bottom, triW);
        } else {
            drawRightTriangle(canvas, left, top, bottom, triW);
            drawRightTriangle(canvas, left + triW + triGap, top, bottom, triW);
            drawBar(canvas, left + groupW - barW, top, bottom, barW);
        }
    }

    private void drawBar(Canvas canvas, float left, float top, float bottom, float width) {
        canvas.drawRect(left, top, left + width, bottom, iconPaint);
    }

    private void drawLeftTriangle(Canvas canvas, float left, float top, float bottom, float width) {
        Path p = new Path();
        p.moveTo(left, (top + bottom) * 0.5f);
        p.lineTo(left + width, top);
        p.lineTo(left + width, bottom);
        p.close();
        canvas.drawPath(p, iconPaint);
    }

    private void drawRightTriangle(Canvas canvas, float left, float top, float bottom, float width) {
        Path p = new Path();
        p.moveTo(left + width, (top + bottom) * 0.5f);
        p.lineTo(left, top);
        p.lineTo(left, bottom);
        p.close();
        canvas.drawPath(p, iconPaint);
    }
}
