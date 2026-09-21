package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

public final class MediaInfo {
    private static final String PREFS = "dab_media";
    private static volatile String title = "DABdream+";
    private static volatile String subtitle = "Noch keine Senderdaten";
    private static volatile boolean playing;
    private static volatile long updatedAt;
    private static volatile Bitmap artwork;
    // Remember the source identity without keeping a potentially multi-megabyte
    // MediaSession bitmap alive in addition to the bounded dashboard copy.
    private static WeakReference<Bitmap> artworkSource = new WeakReference<>(null);

    private MediaInfo() {}

    public static synchronized void restore(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        title = p.getString("title", "DABdream+");
        subtitle = p.getString("subtitle", "Interpret nicht verfügbar");
        if (!usefulSubtitle(subtitle)) subtitle = "Interpret nicht verfügbar";
        playing = p.getBoolean("playing", false);
        updatedAt = p.getLong("updated", 0L);
    }

    public static synchronized boolean update(Context c, String newTitle, String newSubtitle, boolean isPlaying) {
        String nextTitle = useful(newTitle) ? clean(newTitle) : title;
        String nextSubtitle = usefulSubtitle(newSubtitle) && !clean(newSubtitle).equalsIgnoreCase(nextTitle)
                ? clean(newSubtitle)
                : (nextTitle.equals(title) && usefulSubtitle(subtitle) ? subtitle : "Interpret nicht verfügbar");
        if (nextTitle.equals(title) && nextSubtitle.equals(subtitle) && isPlaying == playing) return false;
        title = nextTitle;
        subtitle = nextSubtitle;
        playing = isPlaying;
        updatedAt = System.currentTimeMillis();
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title)
                .putString("subtitle", subtitle)
                .putBoolean("playing", playing)
                .putLong("updated", updatedAt)
                .apply();
        return true;
    }

    public static String title() { return title; }
    public static String subtitle() { return subtitle; }
    public static boolean playing() { return playing; }
    public static long updatedAt() { return updatedAt; }
    public static Bitmap artwork() { return artwork; }

    public static synchronized void updateArtwork(Bitmap image) {
        if (image == null) { artwork = null; artworkSource.clear(); return; }
        if (image == artworkSource.get() || image == artwork) return;
        int w = image.getWidth(), h = image.getHeight();
        if (w <= 0 || h <= 0) return;
        float scale = Math.min(1f, 256f / Math.max(w, h));
        artwork = scale < 1f ? Bitmap.createScaledBitmap(image,
                Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true) : image;
        artworkSource = new WeakReference<>(image);
    }

    private static boolean useful(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim());
    }

    private static boolean usefulSubtitle(String s) {
        return useful(s) && !clean(s).matches("(?i)DLS\\s*[-:]?\\s*");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').trim();
    }
}
