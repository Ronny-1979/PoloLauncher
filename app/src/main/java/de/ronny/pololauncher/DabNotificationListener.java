package de.ronny.pololauncher;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class DabNotificationListener extends NotificationListenerService {
    public static final String DAB_PACKAGE = "com.thf.dabplayer";
    private static volatile boolean connected;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        try {
            for (StatusBarNotification n : getActiveNotifications()) inspect(n);
        } catch (Throwable ignored) {}
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        inspect(sbn);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && isDabPackage(this, sbn.getPackageName())) {
            MediaInfo.update(getApplicationContext(), "", "", false);
            DiagLog.add("DAB Notification beendet");
        }
    }

    private void inspect(StatusBarNotification sbn) {
        if (sbn == null || !isDabPackage(this, sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle e = n.extras;
        String title = first(e, Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG);
        String text = first(e, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT);
        boolean ongoing = sbn.isOngoing() || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        try {
            Object tokenValue = e == null ? null : e.get(Notification.EXTRA_MEDIA_SESSION);
            if (tokenValue instanceof MediaSession.Token) {
                MediaController controller = new MediaController(this, (MediaSession.Token) tokenValue);
                MediaMetadata m = controller.getMetadata();
                if (m != null) {
                    String song = firstMetadata(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
                    String artist = firstMetadata(m, MediaMetadata.METADATA_KEY_ARTIST,
                            MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                    Bitmap image = firstBitmap(m, MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                            MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART);
                    PlaybackState playback = controller.getPlaybackState();
                    boolean playing = playback == null ? ongoing : playback.getState() == PlaybackState.STATE_PLAYING
                            || playback.getState() == PlaybackState.STATE_BUFFERING;
                    MediaInfo.update(getApplicationContext(), song.isEmpty() ? title : song, artist, playing);
                    MediaInfo.updateArtwork(image);
                    DiagLog.add("DAB-Medienkarte: Titel=" + safe(song) + " | Interpret=" + safe(artist)
                            + " | Bild=" + (image != null));
                    return;
                }
            }
        } catch (Throwable t) {
            DiagLog.add("DAB Medienkarte nicht lesbar: " + t.getClass().getSimpleName());
        }
        MediaInfo.update(getApplicationContext(), title, text, ongoing);
        MediaInfo.updateArtwork(n.largeIcon);
        DiagLog.add("DAB Notification: title=" + safe(title) + " | text=" + safe(text) + " | ongoing=" + ongoing);
    }

    private static String first(Bundle b, String... keys) {
        if (b == null) return "";
        for (String key : keys) {
            Object v = b.get(key);
            if (v != null && !String.valueOf(v).trim().isEmpty()) return String.valueOf(v).trim();
        }
        return "";
    }

    private static String firstMetadata(MediaMetadata m, String... keys) {
        for (String key : keys) {
            String value = m.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static Bitmap firstBitmap(MediaMetadata m, String... keys) {
        for (String key : keys) {
            Bitmap image = m.getBitmap(key);
            if (image != null) return image;
        }
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s.replace('\n', ' '); }

    /** Label lookups are Binder/resource work and run for every notification of every app: cache them. */
    private static final java.util.Map<String, Boolean> LABEL_CHECKS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isDabPackage(Context context, String pkg) {
        if (pkg == null) return false;
        if (DAB_PACKAGE.equals(pkg)) return true;
        Boolean known = LABEL_CHECKS.get(pkg);
        if (known != null) return known;
        try {
            CharSequence label = context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(pkg, 0));
            String value = (String.valueOf(label) + " " + pkg).toLowerCase(java.util.Locale.ROOT);
            boolean dab = value.contains("dabdream") || value.contains("dab dream");
            LABEL_CHECKS.put(pkg, dab);
            return dab;
        } catch (Throwable ignored) {
            // Not cached: the package may be installed later.
            return false;
        }
    }

    public static void requestReconnect(Context context) {
        try {
            ComponentName component = new ComponentName(context, DabNotificationListener.class);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (!connected && manager != null && manager.isNotificationListenerAccessGranted(component)) {
                NotificationListenerService.requestRebind(component);
            }
        } catch (Throwable ignored) {}
    }
}
