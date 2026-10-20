package de.ronny.pololauncher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;

import java.util.List;

/**
 * Signals that CarPlay (ZLink) is really connected to the phone: a Bluetooth audio/hands-free
 * profile is connected, or the CarPlay app has an active media session. Must not run on the UI
 * thread (media-session Binder call).
 */
final class CarPlayConnection {
    final boolean bluetooth;
    final boolean mediaSession;

    private CarPlayConnection(boolean bluetooth, boolean mediaSession) {
        this.bluetooth = bluetooth;
        this.mediaSession = mediaSession;
    }

    boolean connected() { return bluetooth || mediaSession; }

    String describe() {
        return "Bluetooth=" + (bluetooth ? "ja" : "nein") + ", CarPlay-Medien=" + (mediaSession ? "ja" : "nein");
    }

    static CarPlayConnection read(Context context, String carPlayPackage) {
        return new CarPlayConnection(bluetoothAudioConnected(), carPlayHasSession(context, carPlayPackage));
    }

    private static boolean bluetoothAudioConnected() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;
            return adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                    || adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
        } catch (RuntimeException noAccess) {
            return false;
        }
    }

    private static boolean carPlayHasSession(Context context, String carPlayPackage) {
        if (carPlayPackage == null) return false;
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return false;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, DabNotificationListener.class));
            if (sessions == null) return false;
            for (MediaController c : sessions) {
                if (c != null && carPlayPackage.equals(c.getPackageName())) return true;
            }
        } catch (RuntimeException noAccess) {
            return false;
        }
        return false;
    }
}
