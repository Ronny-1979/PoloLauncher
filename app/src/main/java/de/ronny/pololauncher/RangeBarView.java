package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Small glass range/fuel bar for the vehicle card. */
final class RangeBarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;

    RangeBarView(Context context) {
        super(context);
        track.setColor(0x4b0a2243);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        border.setColor(0x86a9dfff);
    }

    void setFraction(double value) {
        float next = Double.isFinite(value) ? (float) Math.max(0d, Math.min(1d, value)) : 0f;
        if (Math.abs(next - fraction) < 0.005f) return;
        fraction = next;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float inset = dp(1);
        RectF bar = new RectF(inset, inset, w - inset, h - inset);
        float r = h / 2f;
        canvas.drawRoundRect(bar, r, r, track);
        canvas.drawRoundRect(bar, r, r, border);
        if (fraction <= 0f) return;
        float right = bar.left + bar.width() * fraction;
        RectF active = new RectF(bar.left, bar.top, Math.max(bar.left + h * .55f, right), bar.bottom);
        fill.setShader(new LinearGradient(active.left, 0, active.right, 0,
                new int[]{0xff53e783, 0xff69e9a8, 0xff71d5ff}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(active, r, r, fill);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
