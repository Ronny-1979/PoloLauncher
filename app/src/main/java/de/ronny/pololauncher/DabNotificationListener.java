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

    /** True when the user has granted this listener notification access in Android. */
    static boolean accessGranted(Context context) {
        String flat = android.provider.Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        ComponentName mine = new ComponentName(context, DabNotificationListener.class);
        for (String value : flat.split(":")) {
            if (mine.equals(ComponentName.unflattenFromString(value))) return true;
        }
        return false;
    }
    private static volatile boolean connected;
    private static final int MAX_TEXT_CHARS = 512;

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
        String subText = first(e, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT);
        String text = first(e, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT);
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
                    String album = firstMetadata(m, MediaMetadata.METADATA_KEY_ALBUM);
                    String description = firstMetadata(m, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION);
                    Bitmap image = firstBitmap(m, MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                            MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART);
                    PlaybackState playback = controller.getPlaybackState();
                    boolean playing = playback == null ? ongoing : playback.getState() == PlaybackState.STATE_PLAYING
                            || playback.getState() == PlaybackState.STATE_BUFFERING;
                    String track = song.isEmpty() ? title : song;
                    String station = chooseStation(track, artist, subText, album, description, title, MediaInfo.station());
                    MediaInfo.update(getApplicationContext(), station, track, artist, playing);
                    MediaInfo.updateArtwork(image);
                    DiagLog.add("DAB-Medienkarte: Titel=" + safe(song) + " | Interpret=" + safe(artist)
                            + " | Bild=" + (image != null));
                    return;
                }
            }
        } catch (Throwable t) {
            DiagLog.add("DAB Medienkarte nicht lesbar: " + t.getClass().getSimpleName());
        }
        // Without a MediaSession we cannot reliably separate DLS into song and artist.
        // Keep the known station and show the notification title/text as the best track metadata.
        MediaInfo.update(getApplicationContext(), MediaInfo.station(), title, text, ongoing);
        MediaInfo.updateArtwork(n.largeIcon);
        DiagLog.add("DAB Notification: title=" + safe(title) + " | text=" + safe(text) + " | ongoing=" + ongoing);
    }

    private static String first(Bundle b, String... keys) {
        if (b == null) return "";
        for (String key : keys) {
            try {
                Object value = b.get(key);
                if (value instanceof CharSequence) {
                    String text = bounded(value.toString());
                    if (!text.isEmpty()) return text;
                }
            } catch (Throwable ignored) {
                // A broken app can publish extras containing unparcelable values.
            }
        }
        return "";
    }

    private static String firstMetadata(MediaMetadata m, String... keys) {
        if (m == null) return "";
        for (String key : keys) {
            try {
                String value = bounded(m.getString(key));
                if (!value.isEmpty()) return value;
            } catch (RuntimeException ignored) {
            }
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


    private static String chooseStation(String track, String artist, String... candidates) {
        for (String candidate : candidates) {
            if (isStationCandidate(candidate, track, artist)) return candidate.trim();
        }
        return "DABdream+";
    }

    private static boolean isStationCandidate(String candidate, String track, String artist) {
        if (candidate == null || candidate.trim().isEmpty()) return false;
        String c = normalize(candidate);
        String t = normalize(track);
        String a = normalize(artist);
        if (c.equals("dls") || c.equals("dabdream+") || c.equals("dabdream")) return false;
        if (!t.isEmpty() && c.equals(t)) return false;
        if (!a.isEmpty() && c.equals(a)) return false;
        if (!t.isEmpty() && !a.isEmpty() && c.contains(t) && c.contains(a)) return false;
        return true;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').trim().toLowerCase(java.util.Locale.ROOT)
                .replace('–', '-').replace('—', '-').replaceAll("\\s+", " ");
    }

    private static String safe(String s) { return bounded(s); }

    private static String bounded(String value) {
        if (value == null) return "";
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }

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
