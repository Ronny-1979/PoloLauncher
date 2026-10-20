package de.ronny.pololauncher;

import android.content.Context;
import android.net.Uri;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stores the optional user-selected dashboard background image.
 *
 * The chosen image is copied into the app's private files directory. This makes the background
 * independent of the original file manager/document-provider permission and keeps it available
 * after radio reboots. It only disappears when the user resets it, clears app data, or uninstalls
 * the launcher.
 */
final class BackgroundPreferences {
    private static final String PREFS = "cockpit_background";
    private static final String KEY_FILE = "custom_background_file";
    private static final String LEGACY_KEY_URI = "custom_background_uri";
    private static final String KEY_REVISION = "custom_background_revision";
    private static final String DIR_NAME = "background";
    private static final String FILE_NAME = "custom_background.img";
    private static final long MAX_BACKGROUND_BYTES = 64L * 1024L * 1024L;

    private BackgroundPreferences() {}

    static File customFile(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FILE, "");
        if (stored != null && !stored.trim().isEmpty()) {
            File file = new File(stored);
            if (file.isFile() && file.length() > 0L) return file;
        }

        // Stable default path used by v0.31.21+. Recover an interrupted transactional import
        // if the process died after moving the old wallpaper to .bak but before activating .tmp.
        File dir = new File(context.getFilesDir(), DIR_NAME);
        File fallback = new File(dir, FILE_NAME);
        File backup = new File(dir, FILE_NAME + ".bak");
        if ((!fallback.isFile() || fallback.length() <= 0L) && backup.isFile() && backup.length() > 0L) {
            if (!backup.renameTo(fallback)) return backup;
        }
        return fallback.isFile() && fallback.length() > 0L ? fallback : null;
    }

    static String signature(Context context) {
        long revision = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_REVISION, 0L);
        File file = customFile(context);
        if (file == null) return "default:" + revision;
        return file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified()
                + ":" + revision;
    }

    static boolean hasCustom(Context context) {
        return customFile(context) != null;
    }

    static void importFromUri(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("Keine Bilddatei ausgewählt");

        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Hintergrundordner konnte nicht angelegt werden");
        }

        File target = new File(dir, FILE_NAME);
        File temp = new File(dir, FILE_NAME + ".tmp");

        long bytes = 0L;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            if (in == null) throw new IOException("Bilddatei konnte nicht geöffnet werden");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes += read;
                if (bytes > MAX_BACKGROUND_BYTES)
                    throw new IOException("Hintergrundbild ist größer als 64 MB");
                out.write(buffer, 0, read);
            }
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException ignored) {
                // Not every Android filesystem/provider exposes fsync cleanly.
            }
        } catch (Throwable error) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Bild konnte nicht gespeichert werden", error);
        }

        if (bytes <= 0L) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Ausgewähltes Bild ist leer");
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream validation = new java.io.FileInputStream(temp)) {
            BitmapFactory.decodeStream(validation, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Ausgewählte Datei ist kein unterstütztes Bild");
        }

        // Keep the previous valid image until the new one has safely replaced it.
        File backup = new File(dir, FILE_NAME + ".bak");
        //noinspection ResultOfMethodCallIgnored
        backup.delete();
        boolean hadTarget = target.isFile();
        if (hadTarget && !target.renameTo(backup)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Altes Hintergrundbild konnte nicht gesichert werden");
        }
        if (!temp.renameTo(target)) {
            if (hadTarget) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(target);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Neues Hintergrundbild konnte nicht aktiviert werden");
        }
        //noinspection ResultOfMethodCallIgnored
        backup.delete();

        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long revision = prefs.getLong(KEY_REVISION, 0L) + 1L;
        prefs.edit()
                .putString(KEY_FILE, target.getAbsolutePath())
                .putLong(KEY_REVISION, revision)
                .remove(LEGACY_KEY_URI)
                .commit();
        GlassBlurCache.invalidate();
    }

    static void useDefault(Context context) {
        File file = customFile(context);
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        File dir = new File(context.getFilesDir(), DIR_NAME);
        File temp = new File(dir, FILE_NAME + ".tmp");
        File backup = new File(dir, FILE_NAME + ".bak");
        //noinspection ResultOfMethodCallIgnored
        temp.delete();
        //noinspection ResultOfMethodCallIgnored
        backup.delete();

        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long revision = prefs.getLong(KEY_REVISION, 0L) + 1L;
        prefs.edit()
                .remove(KEY_FILE)
                .remove(LEGACY_KEY_URI)
                .putLong(KEY_REVISION, revision)
                .commit();
        GlassBlurCache.invalidate();
    }
}
