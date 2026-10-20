package de.ronny.pololauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

/** Cached DAB metadata shared between notification/session listeners and the dashboard. */
public final class MediaInfo {
    private static final String PREFS = "dab_media";
    private static final int MAX_TEXT_CHARS = 512;
    private static volatile String station = "DABdream+";
    private static volatile String title = "Noch keine Titeldaten";
    private static volatile String artist = "Interpret nicht verfügbar";
    private static volatile boolean playing;
    private static volatile long updatedAt;
    private static volatile Bitmap artwork;
    // Remember the source identity without keeping a potentially multi-megabyte
    // MediaSession bitmap alive in addition to the bounded dashboard copy.
    private static WeakReference<Bitmap> artworkSource = new WeakReference<>(null);

    private MediaInfo() {}

    public static synchronized void restore(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        station = clean(p.getString("station", "DABdream+"));
        // Old v0.31.1 cache used title/subtitle only; keep that data after an update.
        title = clean(p.getString("track", p.getString("title", "Noch keine Titeldaten")));
        artist = clean(p.getString("artist", p.getString("subtitle", "Interpret nicht verfügbar")));
        if (!usefulStation(station, title, artist)) station = "DABdream+";
        if (!useful(title)) title = "Noch keine Titeldaten";
        if (!usefulSubtitle(artist)) artist = "Interpret nicht verfügbar";
        playing = p.getBoolean("playing", false);
        updatedAt = p.getLong("updated", 0L);
    }

    /** Backwards-compatible track/artist update; the known station name is preserved. */
    public static synchronized boolean update(Context c, String newTitle, String newSubtitle, boolean isPlaying) {
        return update(c, station, newTitle, newSubtitle, isPlaying);
    }

    /** Full station + track + artist update used when DABdream's notification exposes all three. */
    public static synchronized boolean update(Context c, String newStation, String newTitle,
                                              String newArtist, boolean isPlaying) {
        String nextStation = usefulStation(newStation, newTitle, newArtist) ? clean(newStation) : station;
        String nextTitle = useful(newTitle) ? clean(newTitle) : title;
        String nextArtist = usefulSubtitle(newArtist) && !clean(newArtist).equalsIgnoreCase(nextTitle)
                ? clean(newArtist)
                : (nextTitle.equals(title) && usefulSubtitle(artist) ? artist : "Interpret nicht verfügbar");
        if (!usefulStation(nextStation, nextTitle, nextArtist)) nextStation = "DABdream+";
        if (!useful(nextTitle)) nextTitle = "Noch keine Titeldaten";
        if (nextStation.equals(station) && nextTitle.equals(title)
                && nextArtist.equals(artist) && isPlaying == playing) return false;
        station = nextStation;
        title = nextTitle;
        artist = nextArtist;
        playing = isPlaying;
        updatedAt = System.currentTimeMillis();
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("station", station)
                .putString("track", title)
                .putString("artist", artist)
                // Keep the old keys for diagnostics/backwards compatibility.
                .putString("title", title)
                .putString("subtitle", artist)
                .putBoolean("playing", playing)
                .putLong("updated", updatedAt)
                .apply();
        return true;
    }

    public static String station() { return station; }
    public static String title() { return title; }
    public static String artist() { return artist; }
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

    private static boolean usefulStation(String candidate, String track, String artist) {
        if (!useful(candidate)) return false;
        String c = normalized(candidate);
        String t = useful(track) ? normalized(track) : "";
        String a = usefulSubtitle(artist) ? normalized(artist) : "";
        if (!t.isEmpty() && c.equals(t)) return false;
        if (!a.isEmpty() && c.equals(a)) return false;
        // DABdream exposes the DLS line on some radios as notification title. That line
        // often contains "Interpret - Titel" and must never be cached as the station name.
        if (!t.isEmpty() && !a.isEmpty() && c.contains(t) && c.contains(a)) return false;
        return !c.matches("(?i)dls\\s*[-:]?\\s*");
    }

    private static String normalized(String s) {
        return clean(s).toLowerCase(java.util.Locale.ROOT)
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", " ").trim();
    }

    private static boolean useful(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim());
    }

    private static boolean usefulSubtitle(String s) {
        return useful(s) && !clean(s).matches("(?i)DLS\\s*[-:]?\\s*");
    }

    private static String clean(String s) {
        if (s == null) return "";
        String value = s.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= MAX_TEXT_CHARS ? value : value.substring(0, MAX_TEXT_CHARS);
    }
}
