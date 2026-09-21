package de.ronny.pololauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * Event-driven view of the DABdream+ MediaSession. Replaces the former 2-second polling on the UI
 * thread: metadata, artwork and the button state are read once per {@code MediaController.Callback}
 * event (so a new artwork Bitmap is only handled when the metadata really changed), plus a slow
 * safety resynchronisation. All methods must be called on the main thread except {@link #status()}.
 */
final class DabSessionWatcher {
    private static final long RETRY_MS = 5_000L;
    private static final long RESYNC_MS = 20_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ComponentName listenerComponent;
    private MediaSessionManager manager;
    private MediaController controller;
    private boolean started;
    private boolean registered;
    private long lastAttemptMs;
    private long lastResyncMs;
    private String lastTrace = "";
    private long lastActions = -1L;
    private volatile DabControls.Status status = DabControls.statusOf(null);

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::bind;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() {
            unbindController();
            status = DabControls.statusOf(null);
        }
    };

    DabSessionWatcher(Context context) {
        this.context = context.getApplicationContext();
        this.listenerComponent = new ComponentName(this.context, DabNotificationListener.class);
    }

    DabControls.Status status() { return status; }

    void start() {
        started = true;
        attempt();
    }

    /** Cheap; call from the dashboard tick. Retries while notification access is missing. */
    void poll() {
        if (!started) return;
        long now = SystemClock.elapsedRealtime();
        if (!registered) {
            if (now - lastAttemptMs >= RETRY_MS) attempt();
        } else if (now - lastResyncMs >= RESYNC_MS) {
            resync();
        }
    }

    void refreshNow() { if (registered) refresh(); }

    void stop() {
        started = false;
        if (registered && manager != null) {
            try { manager.removeOnActiveSessionsChangedListener(sessionsChanged); } catch (RuntimeException ignored) {}
        }
        registered = false;
        unbindController();
    }

    private void attempt() {
        lastAttemptMs = SystemClock.elapsedRealtime();
        if (registered) return;
        try {
            manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, handler);
            registered = true;
            resync();
        } catch (SecurityException noAccess) {
            // Expected until notification access is granted; poll() retries.
            registered = false;
            status = DabControls.statusOf(null);
        } catch (RuntimeException failure) {
            registered = false;
            DiagLog.add("DAB MediaSession Überwachung: " + failure.getClass().getSimpleName());
        }
    }

    private void resync() {
        lastResyncMs = SystemClock.elapsedRealtime();
        try {
            bind(manager == null ? null : manager.getActiveSessions(listenerComponent));
        } catch (SecurityException lostAccess) {
            // Access revoked: drop everything and let poll() re-register later.
            try { manager.removeOnActiveSessionsChangedListener(sessionsChanged); } catch (RuntimeException ignored) {}
            registered = false;
            unbindController();
            status = DabControls.statusOf(null);
        } catch (RuntimeException failure) {
            DiagLog.add("DAB MediaSession Fehler: " + failure.getClass().getSimpleName());
        }
    }

    private void bind(List<MediaController> sessions) {
        MediaController found = null;
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (DabNotificationListener.isDabPackage(context, candidate.getPackageName())) {
                    found = candidate;
                    break;
                }
            }
        }
        if (found == null) {
            unbindController();
            status = DabControls.statusOf(null);
            return;
        }
        if (controller == null || !controller.getSessionToken().equals(found.getSessionToken())) {
            unbindController();
            controller = found;
            controller.registerCallback(callback, handler);
        }
        refresh();
    }

    private void unbindController() {
        if (controller != null) {
            try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) {}
            controller = null;
        }
    }

    /** One MediaSession query supplies both metadata and button state. */
    private void refresh() {
        MediaController c = controller;
        if (c == null) {
            status = DabControls.statusOf(null);
            return;
        }
        try {
            MediaMetadata m = c.getMetadata();
            PlaybackState p = c.getPlaybackState();
            String title = metadata(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
            String sub = artist(m);
            boolean playing = p != null && (p.getState() == PlaybackState.STATE_PLAYING
                    || p.getState() == PlaybackState.STATE_BUFFERING);
            if (playing || !title.isEmpty() || !sub.isEmpty()) {
                String trace = c.getPackageName() + " | " + title + " | " + sub + " | state=" + (p == null ? "?" : p.getState());
                if (!trace.equals(lastTrace)) {
                    lastTrace = trace;
                    DiagLog.add("DAB MediaSession: " + trace);
                }
                MediaInfo.updateArtwork(artwork(m));
                if (MediaInfo.update(context, title, sub, playing))
                    DiagLog.add("DAB MediaSession erkannt: " + title + " | " + sub + " | pkg=" + c.getPackageName());
            }
            DabControls.Status next = DabControls.statusOf(c);
            status = next;
            if (next.found && next.actions != lastActions) {
                lastActions = next.actions;
                DiagLog.add("DAB-Tasten: vorheriger=" + next.previous + " Play/Pause=" + next.toggle
                        + " nächster=" + next.next + " actions=0x" + Long.toHexString(next.actions));
            }
        } catch (RuntimeException failure) {
            DiagLog.add("DAB MediaSession Fehler: " + failure.getClass().getSimpleName());
        }
    }

    private static String metadata(MediaMetadata m, String... keys) {
        if (m == null) return "";
        for (String key : keys) {
            String s = m.getString(key);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    private static String artist(MediaMetadata m) {
        String name = metadata(m, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return name.matches("(?i)DLS\\s*[-:]?\\s*") ? "" : name;
    }

    private static Bitmap artwork(MediaMetadata m) {
        if (m == null) return null;
        for (String key : new String[]{MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART}) {
            Bitmap bitmap = m.getBitmap(key);
            if (bitmap != null) return bitmap;
        }
        return null;
    }
}
