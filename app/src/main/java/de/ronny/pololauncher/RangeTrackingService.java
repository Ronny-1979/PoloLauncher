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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Foreground means a system notification, not bringing the launcher to the front. */
public final class RangeTrackingService extends Service {
    private static final String CHANNEL = "polo_range_tracking";
    private static final int NOTIFICATION_ID = 260;
    private static volatile RangeTrackingSession current;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RangeTrackingSession session;
    private boolean screenReceiverRegistered;

    private final CarPlayReturnGate carPlayReturnGate = new CarPlayReturnGate();
    private final StandbyWakeDetector standbyWakeDetector = new StandbyWakeDetector();
    private String cachedCarPlayPackage;
    private long lastCarPlayPackageLookupElapsed;
    private long lastCarPlayForegroundCheckElapsed;
    private boolean carPlayQueryInFlight;
    private boolean serviceDestroyed;
    private long carPlayQueryGeneration;
    private long carPlayReturnGeneration;
    private final ThreadPoolExecutor carPlayInspector = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), r -> {
        Thread thread = new Thread(r, "carplay-foreground-check");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                MainActivity.dabScreenChanged(true);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                standbyWakeDetector.screenOn(SystemClock.elapsedRealtime());
                Obd2Runtime.requestReconnect();
                MainActivity.dabScreenChanged(false);
            }
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
        standbyWakeDetector.reset(SystemClock.elapsedRealtime());
        // Repeated starts or a sticky restart must not create multiple timer loops.
        handler.removeCallbacks(sample);
        handler.post(sample);
        return START_STICKY;
    }

    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (session == null || !session.isActive()) return;
            long nowElapsed = SystemClock.elapsedRealtime();
            if (standbyWakeDetector.heartbeat(nowElapsed)) {
                handleQuickStartWake();
            }
            session.update(VehicleRepository.snapshot());
            checkCarPlayAutoReturn();
            handler.postDelayed(this, 500L);
        }
    };

    private void handleQuickStartWake() {
        // Some Android head units do not dispatch SCREEN_OFF/SCREEN_ON while entering their
        // one-hour quick-start standby. A long pause in this normally 500 ms heartbeat is then
        // the reliable signal that Android was frozen and has just resumed.
        MainActivity.dabQuickStartWakeDetected();
        Obd2Runtime.requestReconnect();
        DiagLog.add("DAB-Schnellstart: Standby-Aufwachen ohne SCREEN_ON erkannt; Autostart neu scharf");
        try {
            sendBroadcast(new Intent(MainActivity.ACTION_DAB_QUICKSTART_WAKE)
                    .setPackage(getPackageName()));
        } catch (RuntimeException error) {
            DiagLog.add("DAB-Schnellstart: internes Wake-Signal fehlgeschlagen: " + error);
        }
    }

    private void checkCarPlayAutoReturn() {
        if (!CarPlayAutoReturn.enabled(this) || !CarPlayAutoReturn.hasUsageAccess(this)) {
            cancelCarPlayChecks();
            return;
        }

        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastCarPlayForegroundCheckElapsed < 1_000L || carPlayQueryInFlight)
            return;
        lastCarPlayForegroundCheckElapsed = nowElapsed;

        boolean discoverPackage = cachedCarPlayPackage == null
                && nowElapsed - lastCarPlayPackageLookupElapsed >= 10_000L;
        if (cachedCarPlayPackage == null && !discoverPackage) return;
        if (discoverPackage) lastCarPlayPackageLookupElapsed = nowElapsed;

        final long generation = ++carPlayQueryGeneration;
        final String knownPackage = cachedCarPlayPackage;
        final Context app = getApplicationContext();
        carPlayQueryInFlight = true;
        try {
            carPlayInspector.execute(() -> {
                String detectedPackage = knownPackage;
                if (detectedPackage == null && discoverPackage)
                    detectedPackage = AppLauncher.findCarPlayPackage(app);
                String foreground = detectedPackage == null ? null
                        : ForegroundAppDetector.currentPackage(app);
                boolean manual = CarPlayAutoReturn.manualLaunchSuppressed(app);
                final String packageResult = detectedPackage;
                final String foregroundResult = foreground;
                handler.post(() -> {
                    if (serviceDestroyed || generation != carPlayQueryGeneration) return;
                    carPlayQueryInFlight = false;
                    if (!CarPlayAutoReturn.enabled(RangeTrackingService.this)
                            || !CarPlayAutoReturn.hasUsageAccess(RangeTrackingService.this)) {
                        cancelCarPlayChecks();
                        return;
                    }
                    if (packageResult != null) cachedCarPlayPackage = packageResult;
                    if (packageResult == null) return;
                    applyCarPlayForegroundResult(SystemClock.elapsedRealtime(),
                            packageResult.equals(foregroundResult), manual);
                });
            });
        } catch (RejectedExecutionException busy) {
            carPlayQueryInFlight = false;
        }
    }

    private void applyCarPlayForegroundResult(long nowElapsed,
                                               boolean carPlayForeground,
                                               boolean manual) {
        CarPlayReturnGate.Action action =
                carPlayReturnGate.update(nowElapsed, carPlayForeground, manual);
        if (action == CarPlayReturnGate.Action.CANCEL) {
            handler.removeCallbacks(returnFromCarPlay);
            carPlayReturnGeneration++;
        } else if (action == CarPlayReturnGate.Action.SCHEDULE) {
            handler.removeCallbacks(returnFromCarPlay);
            handler.postDelayed(returnFromCarPlay, CarPlayAutoReturn.RETURN_DELAY_MS);
            lastCarPlayConnectionLog = null;
            DiagLog.add("CarPlay-Automatik: CarPlay im Vordergrund, warte auf bestätigte Verbindung");
        }
    }

    private void cancelCarPlayChecks() {
        handler.removeCallbacks(returnFromCarPlay);
        carPlayReturnGate.reset();
        carPlayQueryGeneration++;
        carPlayReturnGeneration++;
        carPlayQueryInFlight = false;
    }

    private String lastCarPlayConnectionLog;
    private final Runnable returnFromCarPlay = this::confirmCarPlayReturnAsync;

    private void confirmCarPlayReturnAsync() {
        if (!CarPlayAutoReturn.enabled(this)
                || !CarPlayAutoReturn.hasUsageAccess(this)
                || cachedCarPlayPackage == null) {
            carPlayReturnGate.reset();
            return;
        }

        final long generation = ++carPlayReturnGeneration;
        final String packageName = cachedCarPlayPackage;
        final Context app = getApplicationContext();
        try {
            carPlayInspector.execute(() -> {
                String foreground = ForegroundAppDetector.currentPackage(app);
                boolean manual = CarPlayAutoReturn.manualLaunchSuppressed(app);
                final CarPlayConnection connection = CarPlayConnection.read(app, packageName);
                handler.post(() -> {
                    if (serviceDestroyed || generation != carPlayReturnGeneration) return;
                    if (!CarPlayAutoReturn.enabled(RangeTrackingService.this)
                            || !CarPlayAutoReturn.hasUsageAccess(RangeTrackingService.this)
                            || !packageName.equals(cachedCarPlayPackage)) {
                        carPlayReturnGate.reset();
                        return;
                    }
                    boolean carPlayForeground = packageName.equals(foreground);
                    CarPlayReturnGate.Result result = carPlayReturnGate.confirmReturn(
                            SystemClock.elapsedRealtime(), carPlayForeground, manual, connection.connected());
                    String state = connection.describe();
                    if (!state.equals(lastCarPlayConnectionLog)) {
                        lastCarPlayConnectionLog = state;
                        DiagLog.add("CarPlay-Automatik: Verbindungssignale " + state);
                    }
                    if (result == CarPlayReturnGate.Result.WAIT) {
                        handler.postDelayed(returnFromCarPlay, CarPlayAutoReturn.POLL_MS);
                        return;
                    }
                    if (result != CarPlayReturnGate.Result.RETURN) return;
                    if (carPlayReturnGate.lastReturnTimedOut())
                        DiagLog.add("CarPlay-Automatik: keine Verbindung erkannt, Rückkehr nach Zeitlimit");

                    try {
                        Intent home = new Intent(RangeTrackingService.this, MainActivity.class)
                                .setAction(Intent.ACTION_MAIN)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(home);
                        DiagLog.add("CarPlay-Automatik: Polo Launcher nach Verbindung zurückgeholt");
                    } catch (Throwable error) {
                        DiagLog.add("CarPlay-Automatik: Rückkehr fehlgeschlagen: "
                                + error.getClass().getSimpleName());
                    }
                });
            });
        } catch (RejectedExecutionException busy) {
            carPlayReturnGate.reset();
        }
    }

    @Override public void onDestroy() {
        serviceDestroyed = true;
        carPlayQueryGeneration++;
        carPlayReturnGeneration++;
        carPlayInspector.shutdownNow();
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        handler.removeCallbacks(sample);
        handler.removeCallbacks(returnFromCarPlay);
        if (current == session) current = null;
        if (session != null) session.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
