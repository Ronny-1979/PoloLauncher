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
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Creates one small blurred wallpaper bitmap and reuses it for every glass tile.
 *
 * This deliberately avoids continuously blurring the whole UI. The wallpaper is static, so a
 * cached blurred backdrop gives the same frosted-glass appearance behind the tiles with a much
 * smaller load on the head unit.
 */
final class GlassBlurCache {
    private static final Paint BITMAP_PAINT =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private static Bitmap cached;
    private static int cachedScreenW;
    private static int cachedScreenH;

    private GlassBlurCache() {}

    static synchronized Bitmap get(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = Math.max(1, dm.widthPixels);
        int screenH = Math.max(1, dm.heightPixels);
        // Background changes explicitly call invalidate(). Therefore a valid cache at the same
        // display size can be returned immediately without SharedPreferences or filesystem stat
        // calls from every glass Drawable.draw() on the UI thread.
        if (cached != null && !cached.isRecycled()
                && cachedScreenW == screenW && cachedScreenH == screenH) return cached;

        recycleCached();
        File file = BackgroundPreferences.customFile(context);

        // Roughly quarter-resolution is enough for frosted glass and keeps the one-time blur cheap.
        int smallW = Math.max(180, screenW / 4);
        int smallH = Math.max(100, screenH / 4);
        Bitmap bitmap = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (file != null && file.isFile() && file.length() > 0L) {
            drawCustomWallpaper(file, canvas, smallW, smallH);
        } else {
            drawDefaultWallpaper(canvas, smallW, smallH, screenH);
        }

        // Stronger blur with low runtime cost: still calculated only once on a quarter-size bitmap.
        // Three passes approximate a soft Gaussian blur and are visibly blurrier without adding milkiness.
        boxBlur(bitmap, 9, 3);

        cached = bitmap;
        cachedScreenW = screenW;
        cachedScreenH = screenH;
        return cached;
    }

    static synchronized void invalidate() {
        recycleCached();
        cachedScreenW = 0;
        cachedScreenH = 0;
    }

    private static void recycleCached() {
        // FrostedGlassDrawable instances can still hold a shader reference while a wallpaper
        // import invalidates this cache from a worker. Explicit recycle could race a UI draw.
        // Drop only our reference; Android/GC releases the old bounded bitmap when drawables do.
        cached = null;
    }

    private static void drawCustomWallpaper(File file, Canvas canvas, int width, int height) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = new FileInputStream(file)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;

            // The blur bitmap is tiny. Keep roughly 2x its size as source detail, but never
            // decode an unnecessarily huge original image into RAM first.
            int sample = ImageSampling.forCenterCrop(
                    bounds.outWidth, bounds.outHeight,
                    Math.max(1, width * 2), Math.max(1, height * 2));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = new FileInputStream(file)) {
                decoded = BitmapFactory.decodeStream(in, null, options);
            }
            if (decoded == null) return;

            int bw = decoded.getWidth();
            int bh = decoded.getHeight();
            float scale = Math.max(width / (float) bw, height / (float) bh);
            float drawW = bw * scale;
            float drawH = bh * scale;
            float left = (width - drawW) / 2f;
            float top = (height - drawH) / 2f;
            canvas.drawBitmap(decoded, new Rect(0, 0, bw, bh),
                    new RectF(left, top, left + drawW, top + drawH), BITMAP_PAINT);

            Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
            veil.setColor(0x22020c20);
            canvas.drawRect(0, 0, width, height, veil);
        } catch (Throwable error) {
            DiagLog.add("Glass-Blur Hintergrund konnte nicht vorbereitet werden: "
                    + error.getClass().getSimpleName());
        } finally {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
        }
    }

    private static void drawDefaultWallpaper(Canvas canvas, float w, float h, int screenH) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xff081735, 0xff133b75, 0xff0a2858, 0xff06152f, 0xff020816},
                new float[]{0f, .24f, .48f, .76f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, fill);

        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);

        float[][] paths = {
                {-0.08f, .91f, .22f, .55f, .47f, .10f, .94f, -.08f},
                {.06f, 1.06f, .31f, .78f, .65f, .43f, 1.06f, .16f},
                {.40f, 1.08f, .58f, .79f, .80f, .62f, 1.06f, .49f}
        };
        for (float[] p : paths) {
            Path path = new Path();
            path.moveTo(p[0] * w, p[1] * h);
            path.cubicTo(p[2] * w, p[3] * h, p[4] * w, p[5] * h, p[6] * w, p[7] * h);
            float upscale = screenH / Math.max(1f, h);
            glow.setColor(0x202f8fd9);
            glow.setStrokeWidth(Math.max(6f / upscale, h * .010f));
            canvas.drawPath(path, glow);
            line.setColor(0x8dc6e8ff);
            line.setStrokeWidth(Math.max(1.1f / upscale, h * .0017f));
            canvas.drawPath(path, line);
        }
    }

    /**
     * Lightweight separable box blur. It runs only when the wallpaper/effect changes and operates
     * on a quarter-resolution bitmap.
     */
    private static void boxBlur(Bitmap bitmap, int radius, int passes) {
        if (bitmap == null || radius < 1) return;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        int[] temp = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int pass = 0; pass < passes; pass++) {
            horizontal(pixels, temp, w, h, radius);
            vertical(temp, pixels, w, h, radius);
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void horizontal(int[] src, int[] dst, int w, int h, int radius) {
        int window = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            long a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = src[row + clamp(i, 0, w - 1)];
                a += (c >>> 24) & 255;
                r += (c >>> 16) & 255;
                g += (c >>> 8) & 255;
                b += c & 255;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = ((int)(a / window) << 24)
                        | ((int)(r / window) << 16)
                        | ((int)(g / window) << 8)
                        | (int)(b / window);
                int remove = src[row + clamp(x - radius, 0, w - 1)];
                int add = src[row + clamp(x + radius + 1, 0, w - 1)];
                a += ((add >>> 24) & 255) - ((remove >>> 24) & 255);
                r += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                g += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }

    private static void vertical(int[] src, int[] dst, int w, int h, int radius) {
        int window = radius * 2 + 1;
        for (int x = 0; x < w; x++) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = src[clamp(i, 0, h - 1) * w + x];
                a += (c >>> 24) & 255;
                r += (c >>> 16) & 255;
                g += (c >>> 8) & 255;
                b += c & 255;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = ((int)(a / window) << 24)
                        | ((int)(r / window) << 16)
                        | ((int)(g / window) << 8)
                        | (int)(b / window);
                int remove = src[clamp(y - radius, 0, h - 1) * w + x];
                int add = src[clamp(y + radius + 1, 0, h - 1) * w + x];
                a += ((add >>> 24) & 255) - ((remove >>> 24) & 255);
                r += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                g += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
