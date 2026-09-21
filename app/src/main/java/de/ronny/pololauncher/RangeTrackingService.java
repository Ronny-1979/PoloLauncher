package de.ronny.pololauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/** Foreground means a system notification, not bringing the launcher to the front. */
public final class RangeTrackingService extends Service {
    private static final String CHANNEL = "polo_range_tracking";
    private static final int NOTIFICATION_ID = 260;
    private static volatile RangeTrackingSession current;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RangeTrackingSession session;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) MainActivity.dabScreenChanged(true);
            else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) MainActivity.dabScreenChanged(false);
        }
    };

    /** Called only by the visible launcher, respecting Android's background-start rules. */
    static void ensureStarted(Context context) {
        if (isRunning()) return;
        try {
            context.startForegroundService(new Intent(context, RangeTrackingService.class));
        } catch (RuntimeException error) {
            DiagLog.add("Reichweiten-Hintergrunddienst konnte nicht starten: " + error);
        }
    }

    static boolean isRunning() {
        RangeTrackingSession value = current;
        return value != null && value.isActive();
    }

    static int remainingKm(Context context) {
        RangeTrackingSession value = current;
        int live = value == null ? -1 : value.remainingKm();
        return live >= 0 ? live : RangeDisplayStore.remainingKm(context);
    }

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, screenFilter);
            screenReceiverRegistered = true;
        } catch (RuntimeException error) { DiagLog.add("DAB-Standby-Dienst: " + error); }
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Tank und Reichweite", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        Intent home = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, home,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Polo Launcher – Tank und Reichweite")
                .setContentText("CAN-Erfassung und Verbrauchsberechnung laufen im Hintergrund")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).build();
        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException notAllowed) {
            // Android 12+ refuses startForeground() when the system restarts a sticky service while
            // no visible launcher exists (ForegroundServiceStartNotAllowedException). Give up quietly:
            // MainActivity.onStart() starts the service again as soon as the launcher is visible.
            DiagLog.add("Reichweiten-Dienst: startForeground abgelehnt (" + notAllowed.getClass().getSimpleName()
                    + "), Dienst beendet sich bis zum nächsten sichtbaren Launcher");
            stopSelf();
            return;
        }
        session = new RangeTrackingSession(this, SystemClock::elapsedRealtime,
                () -> VendorRuntime.acquire(getApplicationContext()),
                () -> VendorRuntime.release(getApplicationContext()));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (session == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        session.start();
        current = session;
        // Repeated starts or a sticky restart must not create multiple timer loops.
        handler.removeCallbacks(sample);
        handler.post(sample);
        return START_STICKY;
    }

    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (session == null || !session.isActive()) return;
            session.update(VehicleRepository.snapshot());
            handler.postDelayed(this, 500L);
        }
    };

    @Override public void onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        handler.removeCallbacks(sample);
        if (current == session) current = null;
        if (session != null) session.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
