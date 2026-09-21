package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Vector dock symbols: no icon-font dependency on the head unit. */
final class DockIconView extends View {
    static final int VEHICLE = 0;
    static final int DAB = 1;
    static final int MAPS = 2;
    static final int CARPLAY = 3;
    static final int YOUTUBE = 4;
    static final int SETTINGS = 5;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DockIconView(Context context, int kind) {
        super(context);
        this.kind = kind;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 48f, size / 48f);
        paint.setColor(CopperSkin.CREAM);
        paint.setStrokeWidth(3.1f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case VEHICLE: car(canvas); break;
            case DAB: dab(canvas); break;
            case MAPS: maps(canvas); break;
            case CARPLAY: phone(canvas); break;
            case YOUTUBE: youtube(canvas); break;
            default: settings(canvas);
        }
        canvas.restore();
    }

    private void car(Canvas c) {
        Path outline = new Path();
        outline.moveTo(7, 29);
        outline.lineTo(11, 20);
        outline.quadTo(12, 17, 16, 17);
        outline.lineTo(32, 17);
        outline.quadTo(36, 17, 37, 20);
        outline.lineTo(41, 29);
        outline.lineTo(41, 35);
        outline.lineTo(7, 35);
        outline.close();
        c.drawPath(outline, paint);
        c.drawLine(9, 29, 39, 29, paint);
        c.drawLine(11, 35, 11, 39, paint);
        c.drawLine(37, 35, 37, 39, paint);
        c.drawCircle(15, 32, 1.1f, paint);
        c.drawCircle(33, 32, 1.1f, paint);
    }

    private void dab(Canvas c) {
        c.drawRoundRect(7, 19, 41, 37, 4, 4, paint);
        c.drawLine(14, 18, 32, 10, paint);
        c.drawLine(13, 25, 30, 25, paint);
        c.drawLine(13, 30, 26, 30, paint);
        c.drawCircle(34, 29, 3, paint);
    }

    private void maps(Canvas c) {
        Path pin = new Path();
        pin.moveTo(24, 42);
        pin.cubicTo(20, 36, 12, 27, 12, 20);
        pin.cubicTo(12, 4, 36, 4, 36, 20);
        pin.cubicTo(36, 27, 28, 36, 24, 42);
        pin.close();
        c.drawPath(pin, paint);
        c.drawCircle(24, 20, 4.7f, paint);
    }

    private void phone(Canvas c) {
        c.drawRoundRect(13, 5, 35, 43, 4, 4, paint);
        c.drawLine(20, 10, 28, 10, paint);
        c.drawLine(22, 38, 26, 38, paint);
        Path play = new Path();
        play.moveTo(21, 18);
        play.lineTo(21, 31);
        play.lineTo(30, 24.5f);
        play.close();
        c.drawPath(play, paint);
    }

    private void youtube(Canvas c) {
        // Same cream outline as the other dock icons, instead of a red logo.
        c.drawRoundRect(5, 12, 43, 36, 8, 8, paint);
        Path play = new Path();
        play.moveTo(20, 17.5f);
        play.lineTo(20, 30.5f);
        play.lineTo(31, 24);
        play.close();
        c.drawPath(play, paint);
    }

    private void settings(Canvas c) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            float x = (float) Math.cos(angle);
            float y = (float) Math.sin(angle);
            c.drawLine(24 + x * 13, 24 + y * 13, 24 + x * 19, 24 + y * 19, paint);
        }
        c.drawCircle(24, 24, 13, paint);
        c.drawCircle(24, 24, 5, paint);
    }
}
