package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * CarPlay-like background. By default the built-in navy wallpaper is drawn.
 * Optionally a user-selected image from Android's document picker can be used.
 */
final class GlassBackgroundView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint imageTint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap customBitmap;
    private String loadedFile = "";

    GlassBackgroundView(Context context) {
        super(context);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        imageTint.setColor(0x22020c20);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshCustomBackground(true);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) refreshCustomBackground(false);
    }

    private void refreshCustomBackground(boolean force) {
        File file = BackgroundPreferences.customFile(getContext());
        String wanted = file == null ? "" : file.getAbsolutePath();
        if (!force && wanted.equals(loadedFile)) return;

        if (customBitmap != null && !customBitmap.isRecycled()) {
            customBitmap.recycle();
        }
        customBitmap = null;
        loadedFile = wanted;

        if (file == null || !file.isFile() || file.length() <= 0L
                || getWidth() <= 0 || getHeight() <= 0) {
            invalidate();
            return;
        }

        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = new FileInputStream(file)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IllegalArgumentException("Ungültiges Bild");
            }

            // Decode only as large as the display can actually use. This avoids 40–100 MiB
            // ARGB bitmaps when the user chooses a 4K/8K camera photo or a very wide panorama.
            int sample = ImageSampling.forCenterCrop(
                    bounds.outWidth, bounds.outHeight, getWidth(), getHeight());

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = new FileInputStream(file)) {
                customBitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (customBitmap == null) throw new IllegalArgumentException("Bild konnte nicht dekodiert werden");
        } catch (Throwable error) {
            customBitmap = null;
            loadedFile = "";
            DiagLog.add("Hintergrundbild konnte nicht geladen werden: "
                    + error.getClass().getSimpleName());
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (customBitmap != null && !customBitmap.isRecycled()) {
            drawCenterCrop(canvas, customBitmap, w, h);
            // A very light navy veil keeps white text/glass surfaces readable without hiding the photo.
            canvas.drawRect(0, 0, w, h, imageTint);
            return;
        }

        drawDefaultWallpaper(canvas, w, h);
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, float w, float h) {
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) return;

        float scale = Math.max(w / bw, h / bh);
        float drawW = bw * scale;
        float drawH = bh * scale;
        float left = (w - drawW) / 2f;
        float top = (h - drawH) / 2f;
        Rect src = new Rect(0, 0, bw, bh);
        RectF dst = new RectF(left, top, left + drawW, top + drawH);
        canvas.drawBitmap(bitmap, src, dst, bitmapPaint);
    }

    private void drawDefaultWallpaper(Canvas canvas, float w, float h) {
        fill.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xff081735, 0xff133b75, 0xff0a2858, 0xff06152f, 0xff020816},
                new float[]{0f, .24f, .48f, .76f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, fill);

        float[][] paths = {
                {-0.08f, .91f, .22f, .55f, .47f, .10f, .94f, -.08f},
                {.06f, 1.06f, .31f, .78f, .65f, .43f, 1.06f, .16f},
                {.40f, 1.08f, .58f, .79f, .80f, .62f, 1.06f, .49f}
        };
        for (float[] p : paths) {
            Path path = new Path();
            path.moveTo(p[0] * w, p[1] * h);
            path.cubicTo(p[2] * w, p[3] * h, p[4] * w, p[5] * h, p[6] * w, p[7] * h);
            glow.setColor(0x202f8fd9);
            glow.setStrokeWidth(Math.max(6f, h * .010f));
            canvas.drawPath(path, glow);
            line.setColor(0x8dc6e8ff);
            line.setStrokeWidth(Math.max(1.1f, h * .0017f));
            canvas.drawPath(path, line);
        }
    }
}
