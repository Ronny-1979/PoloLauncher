package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** German-style circular speed-limit sign rendered directly, without emoji/font symbols. */
final class SpeedLimitSignView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int limit = SpeedLimitValue.UNKNOWN;

    SpeedLimitSignView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setVisibility(GONE);

        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        fill.setShadowLayer(dp(3), 0f, dp(1), 0x66000000);

        ring.setColor(Color.rgb(210, 20, 28));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(5.5f));

        text.setColor(Color.BLACK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    void setLimit(int kmhOrState) {
        if (limit == kmhOrState) return;
        limit = kmhOrState;
        boolean show = kmhOrState > 0;
        setVisibility(show ? VISIBLE : GONE);
        if (show) {
            setContentDescription("Tempolimit " + kmhOrState + " Kilometer pro Stunde");
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (limit <= 0) return;

        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.5f - dp(4);

        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius - dp(2.75f), ring);

        float size;
        if (limit >= 100) size = dp(17f);
        else if (limit >= 10) size = dp(21f);
        else size = dp(23f);
        text.setTextSize(size);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(Integer.toString(limit), cx, baseline, text);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
