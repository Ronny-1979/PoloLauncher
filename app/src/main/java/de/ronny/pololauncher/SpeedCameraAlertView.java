package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Compact map overlay for an approaching fixed speed camera. No emoji/font icon is used. */
final class SpeedCameraAlertView extends View {
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int distanceMetres = -1;

    SpeedCameraAlertView(Context context) {
        super(context);
        setVisibility(GONE);

        card.setColor(0xE8FFFFFF);
        card.setStyle(Paint.Style.FILL);
        card.setShadowLayer(dp(3), 0f, dp(1), 0x66000000);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        accent.setColor(Color.rgb(210, 20, 28));
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeWidth(dp(3));

        icon.setColor(Color.BLACK);
        icon.setStyle(Paint.Style.FILL);
        lens.setColor(Color.WHITE);
        lens.setStyle(Paint.Style.FILL);

        text.setColor(Color.BLACK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(dp(13));
    }

    void setDistance(int metres) {
        if (distanceMetres == metres) return;
        distanceMetres = metres;
        boolean show = metres >= 0;
        setVisibility(show ? VISIBLE : GONE);
        if (show) {
            setContentDescription("Fester Blitzer in " + formatDistance(metres));
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (distanceMetres < 0) return;

        float w = getWidth();
        float h = getHeight();
        RectF outer = new RectF(dp(2), dp(2), w - dp(2), h - dp(2));
        canvas.drawRoundRect(outer, dp(13), dp(13), card);
        canvas.drawRoundRect(outer, dp(13), dp(13), accent);

        // Camera body + lens, drawn geometrically so Android cannot substitute an emoji.
        float cx = w * 0.5f;
        RectF body = new RectF(cx - dp(17), dp(13), cx + dp(17), dp(35));
        canvas.drawRoundRect(body, dp(4), dp(4), icon);
        canvas.drawRect(cx - dp(9), dp(9), cx + dp(5), dp(15), icon);

        canvas.drawCircle(cx + dp(6), dp(24), dp(6), lens);
        canvas.drawCircle(cx + dp(6), dp(24), dp(3.5f), icon);

        String label = formatDistance(distanceMetres);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = h - dp(9) - fm.descent;
        canvas.drawText(label, cx, baseline, text);
    }

    private static String formatDistance(int metres) {
        if (metres >= 1000) return String.format(java.util.Locale.GERMANY, "%.1f km", metres / 1000d);
        if (metres >= 100) return (Math.round(metres / 10f) * 10) + " m";
        return Math.max(0, metres) + " m";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
