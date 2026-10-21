package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Draws the position marker in code (dot, pumpkin or sleigh), scaled to the view size. */
final class MarkerArtView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private String style = MarkerStyle.STANDARD;

    MarkerArtView(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void setStyle(String id) {
        String n = MarkerStyle.normalize(id);
        if (n.equals(style)) return;
        style = n;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (MarkerStyle.PUMPKIN.equals(style)) drawPumpkin(c, w, h);
        else if (MarkerStyle.SLEIGH.equals(style)) drawSleigh(c, w, h);
        else drawDot(c, w, h);
    }

    private void drawDot(Canvas c, float w, float h) {
        float stroke = Math.max(2f, w * 0.15f);
        float r = Math.min(w, h) / 2f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(31, 139, 247));
        c.drawCircle(w / 2f, h / 2f, r, fill);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(stroke);
        line.setColor(Color.WHITE);
        c.drawCircle(w / 2f, h / 2f, r - stroke / 2f, line);
    }

    private void drawPumpkin(Canvas c, float w, float h) {
        float u = w / 48f;
        fill.setStyle(Paint.Style.FILL);
        // stem
        fill.setColor(Color.rgb(76, 140, 43));
        path.reset();
        path.moveTo(21 * u, 11 * u);
        path.lineTo(23 * u, 2 * u);
        path.lineTo(29 * u, 3 * u);
        path.lineTo(27.5f * u, 12 * u);
        path.close();
        c.drawPath(path, fill);
        // outer lobes (dark orange) then the big body on top
        fill.setColor(Color.rgb(214, 96, 14));
        rect.set(2 * u, 11 * u, 24 * u, 43 * u);
        c.drawOval(rect, fill);
        rect.set(24 * u, 11 * u, 46 * u, 43 * u);
        c.drawOval(rect, fill);
        fill.setColor(Color.rgb(247, 127, 24));
        rect.set(10 * u, 10 * u, 38 * u, 43.5f * u);
        c.drawOval(rect, fill);
        // ribs
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * u);
        line.setColor(Color.rgb(190, 80, 8));
        rect.set(15 * u, 11 * u, 33 * u, 43 * u);
        c.drawOval(rect, line);
        // outline
        line.setStrokeWidth(1.8f * u);
        line.setColor(Color.rgb(120, 50, 5));
        rect.set(2 * u, 11 * u, 46 * u, 43 * u);
        c.drawRoundRect(rect, 22 * u, 16 * u, line);
        // face (glowing)
        fill.setColor(Color.rgb(255, 224, 80));
        path.reset();
        path.moveTo(13 * u, 22 * u); path.lineTo(21 * u, 22 * u); path.lineTo(17 * u, 15.5f * u); path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(27 * u, 22 * u); path.lineTo(35 * u, 22 * u); path.lineTo(31 * u, 15.5f * u); path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(12 * u, 29 * u);
        path.lineTo(17 * u, 33 * u); path.lineTo(20.5f * u, 29.5f * u); path.lineTo(24 * u, 34 * u);
        path.lineTo(27.5f * u, 29.5f * u); path.lineTo(31 * u, 33 * u); path.lineTo(36 * u, 29 * u);
        path.lineTo(33 * u, 38 * u); path.lineTo(15 * u, 38 * u);
        path.close();
        c.drawPath(path, fill);
        line.setStrokeWidth(1.2f * u);
        line.setColor(Color.rgb(90, 35, 0));
        c.drawPath(path, line);
    }

    /** Santa's sleigh seen from above, front (curled runners) pointing up. */
    private void drawSleigh(Canvas c, float w, float h) {
        float u = w / 34f; // design grid 34 x 56
        // runners
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.6f * u);
        line.setColor(Color.rgb(214, 170, 60));
        path.reset();
        path.moveTo(5 * u, 14 * u); path.cubicTo(1 * u, 9 * u, 4 * u, 3 * u, 8 * u, 3.5f * u);
        path.moveTo(5 * u, 14 * u); path.lineTo(5 * u, 52 * u);
        c.drawPath(path, line);
        path.reset();
        path.moveTo(29 * u, 14 * u); path.cubicTo(33 * u, 9 * u, 30 * u, 3 * u, 26 * u, 3.5f * u);
        path.moveTo(29 * u, 14 * u); path.lineTo(29 * u, 52 * u);
        c.drawPath(path, line);
        // body (red) with a swept front
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(200, 30, 36));
        path.reset();
        path.moveTo(17 * u, 5 * u);
        path.cubicTo(25 * u, 10 * u, 28 * u, 18 * u, 27 * u, 26 * u);
        path.lineTo(27 * u, 48 * u);
        path.quadTo(27 * u, 52 * u, 23 * u, 52 * u);
        path.lineTo(11 * u, 52 * u);
        path.quadTo(7 * u, 52 * u, 7 * u, 48 * u);
        path.lineTo(7 * u, 26 * u);
        path.cubicTo(6 * u, 18 * u, 9 * u, 10 * u, 17 * u, 5 * u);
        path.close();
        c.drawPath(path, fill);
        // gold trim
        line.setStrokeWidth(1.6f * u);
        line.setColor(Color.rgb(240, 200, 80));
        c.drawPath(path, line);
        // bench divider
        line.setColor(Color.rgb(140, 18, 24));
        line.setStrokeWidth(1.4f * u);
        c.drawLine(9 * u, 30 * u, 25 * u, 30 * u, line);
        // gift sack at the back
        fill.setColor(Color.rgb(120, 78, 40));
        rect.set(10 * u, 33 * u, 24 * u, 49 * u);
        c.drawRoundRect(rect, 5 * u, 5 * u, fill);
        fill.setColor(Color.rgb(46, 160, 67));
        rect.set(15 * u, 38 * u, 19 * u, 42 * u);
        c.drawRect(rect, fill);
        // Santa seen from above: shoulders, red hat bent backwards with a white pompom
        fill.setColor(Color.rgb(150, 20, 28));
        rect.set(11 * u, 17 * u, 23 * u, 27.5f * u);
        c.drawRoundRect(rect, 4 * u, 4 * u, fill);
        fill.setColor(Color.rgb(250, 250, 250));
        c.drawCircle(17 * u, 20.5f * u, 4.6f * u, fill);
        fill.setColor(Color.rgb(235, 40, 45));
        path.reset();
        path.moveTo(12.6f * u, 19.5f * u);
        path.quadTo(17 * u, 12.5f * u, 21.4f * u, 19.5f * u);
        path.quadTo(21 * u, 24.5f * u, 17 * u, 27 * u);
        path.quadTo(13 * u, 24.5f * u, 12.6f * u, 19.5f * u);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(Color.rgb(250, 250, 250));
        c.drawCircle(17 * u, 27.5f * u, 2f * u, fill);
    }
}
