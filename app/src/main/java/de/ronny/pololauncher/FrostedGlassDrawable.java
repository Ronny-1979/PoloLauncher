package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * Adds an aligned blurred copy of the wallpaper below a normal GlassSkin drawable.
 * This makes the cards look like frosted windows without blurring text/icons.
 */
final class FrostedGlassDrawable extends Drawable {
    private final Context context;
    private final float radius;
    private final Drawable overlay;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final int[] location = new int[2];
    private Bitmap shaderBitmap;
    private BitmapShader bitmapShader;

    FrostedGlassDrawable(Context context, float radius, Drawable overlay) {
        this.context = context.getApplicationContext();
        this.radius = radius * context.getResources().getDisplayMetrics().density;
        this.overlay = overlay;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        Bitmap blurred = GlassBlurCache.get(context);
        View owner = ownerView();
        if (blurred != null && owner != null && b.width() > 0 && b.height() > 0) {
            owner.getLocationOnScreen(location);
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float scaleX = dm.widthPixels / (float) blurred.getWidth();
            float scaleY = dm.heightPixels / (float) blurred.getHeight();

            if (bitmapShader == null || shaderBitmap != blurred) {
                shaderBitmap = blurred;
                bitmapShader = new BitmapShader(
                        blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            shaderMatrix.reset();
            shaderMatrix.setScale(scaleX, scaleY);
            shaderMatrix.postTranslate(-location[0], -location[1]);
            bitmapShader.setLocalMatrix(shaderMatrix);
            blurPaint.setShader(bitmapShader);
            canvas.drawRoundRect(new RectF(b), radius, radius, blurPaint);
            blurPaint.setShader(null);
        }

        overlay.setBounds(b);
        overlay.draw(canvas);
    }

    private View ownerView() {
        Callback callback = getCallback();
        for (int i = 0; i < 4 && callback != null; i++) {
            if (callback instanceof View) return (View) callback;
            if (callback instanceof Drawable) callback = ((Drawable) callback).getCallback();
            else break;
        }
        return null;
    }

    @Override public void setAlpha(int alpha) {
        blurPaint.setAlpha(alpha);
        overlay.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        blurPaint.setColorFilter(colorFilter);
        overlay.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override public void getOutline(Outline outline) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        outline.setRoundRect(bounds, radius);
        outline.setAlpha(1f);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        overlay.setBounds(bounds);
    }
}
