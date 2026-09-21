package de.ronny.pololauncher;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

import java.util.List;

/** Sends commands only to DABdream+, never to CarPlay or another music player. */
public final class DabControls {
    public static final int PREVIOUS = 0;
    public static final int TOGGLE = 1;
    public static final int NEXT = 2;

    public static final class Status {
        public final boolean found;
        public final boolean playing;
        public final boolean previous;
        public final boolean toggle;
        public final boolean next;
        public final long actions;

        private Status(boolean found, boolean playing, boolean previous, boolean toggle, boolean next, long actions) {
            this.found = found;
            this.playing = playing;
            this.previous = previous;
            this.toggle = toggle;
            this.next = next;
            this.actions = actions;
        }
    }

    private static final Status NO_SESSION = new Status(false, false, false, false, false, 0L);

    private DabControls() {}

    private static MediaController controller(Context context) {
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) return null;
        List<MediaController> sessions = manager.getActiveSessions(new ComponentName(context, DabNotificationListener.class));
        if (sessions == null) return null;
        for (MediaController session : sessions) {
            if (DabNotificationListener.isDabPackage(context, session.getPackageName())) return session;
        }
        return null;
    }

    public static Status status(Context context) {
        try {
            return statusOf(controller(context));
        } catch (SecurityException ignored) {
            return NO_SESSION;
        } catch (Throwable t) {
            DiagLog.add("DAB-Steuerung Status: " + t.getClass().getSimpleName());
            return NO_SESSION;
        }
    }

    static Status statusOf(MediaController controller) {
        if (controller == null) return NO_SESSION;
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return new Status(true, false, false, false, false, 0L);
        long actions = state.getActions();
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING || state.getState() == PlaybackState.STATE_BUFFERING;
        long toggleAction = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        return new Status(true, playing,
                (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0,
                (actions & (toggleAction | PlaybackState.ACTION_PLAY_PAUSE)) != 0,
                (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0, actions);
    }

    /** Returns false if the session is absent or does not advertise this command. */
    public static boolean send(Context context, int command) {
        try {
            MediaController controller = controller(context);
            Status status = statusOf(controller);
            if (controller == null) return false;
            MediaController.TransportControls transport = controller.getTransportControls();
            if (transport == null) return false;
            if (command == PREVIOUS && status.previous) transport.skipToPrevious();
            else if (command == NEXT && status.next) transport.skipToNext();
            else if (command == TOGGLE && status.toggle) {
                long direct = status.playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                if ((status.actions & direct) != 0) {
                    if (status.playing) transport.pause();
                    else transport.play();
                } else {
                    controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                }
            } else return false;
            DiagLog.add("DAB-Befehl gesendet: " + (command == PREVIOUS ? "zurück" : command == NEXT ? "weiter" : status.playing ? "Pause" : "Play"));
            return true;
        } catch (Throwable t) {
            DiagLog.add("DAB-Befehl Fehler: " + t.getClass().getSimpleName());
            return false;
        }
    }
}
