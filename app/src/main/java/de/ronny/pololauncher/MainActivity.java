package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainActivity extends Activity {
    private static final double POLO_TANK_CAPACITY_L = 45d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    static final String ACTION_DAB_QUICKSTART_WAKE =
            "de.ronny.pololauncher.action.DAB_QUICKSTART_WAKE";
    private static final DabStartupGate dabStartupGate = new DabStartupGate();
    private boolean dabWaitingLogged;
    private boolean dabScreenReceiverRegistered;
    private static volatile boolean dabScreenOff;
    static void dabScreenChanged(boolean off) {
        dabScreenOff = off;
        if (off) dabStartupGate.screenOff();
    }

    /** Fallback for head units that freeze Android for quick-start without SCREEN_OFF. */
    static void dabQuickStartWakeDetected() {
        dabScreenOff = false;
        dabStartupGate.screenOff();
    }
    private final BroadcastReceiver dabScreenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                dabScreenChanged(true);
                if (germanyMap != null) germanyMap.saveState();
                dabWaitingLogged = false;
                handler.removeCallbacks(startDabAutomatically);
                dabReturnGeneration++;
                handler.removeCallbacks(dabReturnCheck);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                dabScreenChanged(false);
                onStandbyWake("Bildschirm an");
                if (activityVisible) {
                    handler.removeCallbacks(startDabAutomatically);
                    handler.postDelayed(startDabAutomatically, 800L);
                }
            } else if (ACTION_DAB_QUICKSTART_WAKE.equals(intent.getAction())) {
                dabWaitingLogged = false;
                onStandbyWake("Quick-Start");
                if (activityVisible) {
                    handler.removeCallbacks(startDabAutomatically);
                    handler.postDelayed(startDabAutomatically, 800L);
                }
            }
        }
    };
    // After head-unit standby the MapLibre GL surfaces can stay blank (blue) until the process is
    // restarted. A wake therefore rebuilds the Activity (and with it all map renderers) once.
    private static long lastWakeMapRebuildMs;
    private boolean wakeMapRebuildPending;
    private final Runnable rebuildMapAfterWake = () -> {
        if (!this.wakeMapRebuildPending || isFinishing() || isDestroyed() || !this.activityVisible) return;
        long now = android.os.SystemClock.elapsedRealtime();
        // Static cooldown: SCREEN_ON and the Quick-Start broadcast belong to the same wake.
        if (lastWakeMapRebuildMs != 0L && now - lastWakeMapRebuildMs < 15_000L) {
            this.wakeMapRebuildPending = false;
            return;
        }
        this.wakeMapRebuildPending = false;
        lastWakeMapRebuildMs = now;
        DiagLog.add("Hauptseite neu: Karte nach Standby neu aufgebaut");
        recreate();
    };

    private void onStandbyWake(String reason) {
        wakeMapRebuildPending = true;
        DiagLog.add("Standby-Aufwachen erkannt (" + reason + ")");
        handler.removeCallbacks(rebuildMapAfterWake);
        if (activityVisible) handler.postDelayed(rebuildMapAfterWake, 2_000L);
    }

    private final Runnable startDabAutomatically = () -> {
        boolean enabled = getSharedPreferences("cockpit_media", MODE_PRIVATE)
                .getBoolean("dab_start_and_return", false);
        if (!this.activityVisible || dabScreenOff || isFinishing() || !dabStartupGate.pending(enabled)) return;
        boolean internetReady = dabInternetAvailable();
        if (!internetReady) {
            if (!this.dabWaitingLogged) {
                DiagLog.add("DAB-Autostart: wartet auf bestätigten Internetzugang");
                this.dabWaitingLogged = true;
            }
            handler.postDelayed(this.startDabAutomatically, 2000L);
            return;
        }
        if (!dabStartupGate.claim(enabled, internetReady)) return;
        DiagLog.add("DAB-Autostart: Internet bestätigt");
        if (AppLauncher.launchFirst(this, "DABdream+", DabNotificationListener.DAB_PACKAGE)) {
            DiagLog.add("DAB-Autostart: geöffnet, warte auf Vordergrund und laufenden Sender");
            this.dabReturnGate.start(android.os.SystemClock.elapsedRealtime());
            this.dabReturnGeneration++;
            handler.removeCallbacks(this.dabReturnCheck);
            handler.postDelayed(this.dabReturnCheck, 1000L);
        } else DiagLog.add("DAB-Autostart: App nicht startbar");
    };

    private boolean dabInternetAvailable() {
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = manager == null ? null
                    : manager.getNetworkCapabilities(manager.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException error) { return false; }
    }

    // ---- Automatic weekly map-update check with a popup (downloads only after confirmation) ----
    private static long lastMapUpdateAttemptElapsed;
    private long mapUpdateResumedAt;
    private android.app.AlertDialog mapUpdateDialog;

    private final Runnable mapUpdateWatch = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed() || !activityVisible) return;
            try { checkMapUpdateStep(); }
            catch (RuntimeException error) { DiagLog.add("Karten-Update-Automatik: " + error.getClass().getSimpleName()); }
            handler.postDelayed(this, 15_000L);
        }
    };

    /** The launcher counts as fully started when it has been visible for a while and nothing else is pending. */
    private boolean launcherSettled() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mapUpdateResumedAt < MapUpdateSchedule.SETTLE_MS) return false;
        if (lastWakeMapRebuildMs != 0L && now - lastWakeMapRebuildMs < MapUpdateSchedule.SETTLE_MS) return false;
        if (wakeMapRebuildPending || dabScreenOff) return false;
        boolean dabEnabled = getSharedPreferences("cockpit_media", MODE_PRIVATE)
                .getBoolean("dab_start_and_return", false);
        if (dabStartupGate.pending(dabEnabled)) return false;
        return dabInternetAvailable();
    }

    private void checkMapUpdateStep() {
        if (mapUpdateDialog != null && mapUpdateDialog.isShowing()) return;
        if (!GermanyOfflineStore.ready(this) || GermanyOfflineStore.active(this)
                || GermanyOfflineStore.runPending(this)) return;
        if (!launcherSettled()) return;
        // Updates already known from an earlier check: ask now unless "Später" is still in force.
        if (GermanyOfflineStore.updatePromptDue(this)) {
            showMapUpdateDialog(GermanyOfflineStore.updateStatus(this).detail);
            return;
        }
        if (!GermanyOfflineStore.updateCheckDue(this)) return;
        if (GermanyOfflineUpdateChecker.running()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastMapUpdateAttemptElapsed != 0L && now - lastMapUpdateAttemptElapsed < MapUpdateSchedule.RETRY_MS) return;
        lastMapUpdateAttemptElapsed = now;
        DiagLog.add("Karten-Update-Automatik: wöchentliche Prüfung gestartet");
        GermanyOfflineUpdateChecker.checkAsync(this, (owner, result) -> {
            if (result.busy) return;
            if (!result.complete()) {
                DiagLog.add("Karten-Update-Automatik: Prüfung unvollständig, später erneut");
                return;
            }
            DiagLog.add("Karten-Update-Automatik: " + result.summary());
            if (result.hasUpdates() && owner instanceof MainActivity)
                ((MainActivity) owner).showMapUpdateDialog(result.detail());
        });
    }

    private void showMapUpdateDialog(String detail) {
        if (isFinishing() || isDestroyed() || !activityVisible) return;
        if (mapUpdateDialog != null && mapUpdateDialog.isShowing()) return;
        String regions = detail == null ? "" : detail;
        mapUpdateDialog = GlassSkin.dialog(this)
                .setTitle("Neue Karten verfügbar")
                .setMessage(regions + "\n\nNur geänderte Regionen werden geladen (auch über das Handynetz). "
                        + "Bis alles fertig ist, bleibt die alte Karte aktiv. Jetzt aktualisieren?")
                .setNegativeButton("Später", (dialog, which) -> {
                    GermanyOfflineStore.postponeUpdatePrompt(this);
                    DiagLog.add("Karten-Update-Automatik: Später gewählt, Erinnerung in 24 h");
                })
                .setPositiveButton("Aktualisieren", (dialog, which) -> {
                    GermanyOfflineStore.startUpdate(this);
                    DiagLog.add("Karten-Update-Automatik: Aktualisierung bestätigt");
                    android.widget.Toast.makeText(this, "Karten-Aktualisierung gestartet",
                            android.widget.Toast.LENGTH_LONG).show();
                })
                .create();
        mapUpdateDialog.show();
        DiagLog.add("Karten-Update-Automatik: Hinweis angezeigt");
    }

    private void stopMapUpdateWatch() {
        handler.removeCallbacks(mapUpdateWatch);
        if (mapUpdateDialog != null) {
            try { mapUpdateDialog.dismiss(); } catch (RuntimeException ignored) {}
            mapUpdateDialog = null;
        }
    }

    private final DabReturnGate dabReturnGate = new DabReturnGate();
    private int dabReturnGeneration;
    private final java.util.concurrent.ExecutorService dabReturnExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "dab-return-check");
                t.setDaemon(true);
                return t;
            });
    private String lastDabReturnLog = "";

    /** Prüft jede Sekunde, ob DABdream+ vorn ist und der Sender läuft; erst dann geht es zurück. */
    private final Runnable dabReturnCheck = () -> {
        if (dabScreenOff || isFinishing() || isDestroyed()
                || !getSharedPreferences("cockpit_media", MODE_PRIVATE)
                .getBoolean("dab_start_and_return", false)) return;
        final int generation = dabReturnGeneration;
        final Context app = getApplicationContext();
        try {
            dabReturnExecutor.execute(() -> {
                boolean foregroundKnown = CarPlayAutoReturn.hasUsageAccess(app);
                String foreground = foregroundKnown ? ForegroundAppDetector.currentPackage(app) : null;
                boolean dabForeground = foreground != null
                        && DabNotificationListener.isDabPackage(app, foreground);
                if (foregroundKnown && foreground == null) foregroundKnown = false;
                boolean playing = DabControls.status(app).playing;
                final boolean known = foregroundKnown;
                handler.post(() -> {
                    if (generation != dabReturnGeneration || isDestroyed()) return;
                    DabReturnGate.Result result = dabReturnGate.update(
                            android.os.SystemClock.elapsedRealtime(), known, dabForeground, playing);
                    String state = "DABdream+ vorn=" + (known ? (dabForeground ? "ja" : "nein") : "?")
                            + ", Sender läuft=" + (playing ? "ja" : "nein");
                    if (!state.equals(lastDabReturnLog)) {
                        lastDabReturnLog = state;
                        DiagLog.add("DAB-Autostart: " + state);
                    }
                    if (result == DabReturnGate.Result.WAIT) {
                        handler.postDelayed(this.dabReturnCheck, 1000L);
                    } else if (result == DabReturnGate.Result.RETURN) {
                        if (dabReturnGate.timedOut())
                            DiagLog.add("DAB-Autostart: Sender nicht erkannt, Rückkehr nach Zeitlimit");
                        returnAfterDabStartup();
                    } else {
                        DiagLog.add("DAB-Autostart: DABdream+ wurde verlassen, keine automatische Rückkehr");
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            // activity is going away
        }
    };

    private void returnAfterDabStartup() {
        if (dabScreenOff || isFinishing() || isDestroyed()
                || !getSharedPreferences("cockpit_media", MODE_PRIVATE)
                .getBoolean("dab_start_and_return", false)) return;
        try {
            startActivity(new Intent(this, MainActivity.class).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            DiagLog.add("DAB-Autostart: Launcher-Rückkehr angefordert");
        } catch (RuntimeException error) {
            DiagLog.add("DAB-Autostart: Launcher-Rückkehr fehlgeschlagen: " + error);
        }
    }
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    private final SimpleDateFormat sidebarDateFormat = new SimpleDateFormat("d.M.yy", Locale.GERMANY);
    private final SimpleDateFormat sidebarWeekdayFormat = new SimpleDateFormat("EEEE", Locale.GERMANY);

    private TextView sidebarClock;
    private TextView sidebarDate;
    private TextView sidebarWeekday;
    private TextView sidebarNetwork;
    private TextView sidebarSignal;
    private TextView sidebarGps;
    private TextView fuel;
    private TextView estimatedRange;
    private TextView speed;
    private TextView rpm;
    private TextView voltage;
    private TextView outside;
    private TextView weatherLocationName;
    private ImageView weatherCurrentIcon;
    private TextView weatherCurrentTemperature;
    private TextView weatherCurrentDescription;
    private TextView weatherHighLow;
    private TextView weatherSunrise;
    private TextView weatherSunset;
    private final TextView[] weatherDayName = new TextView[WeatherForecast.FORECAST_DAYS];
    private final ImageView[] weatherDayIcon = new ImageView[WeatherForecast.FORECAST_DAYS];
    private final TextView[] weatherDayDescription = new TextView[WeatherForecast.FORECAST_DAYS];
    private final TextView[] weatherDayTemperature = new TextView[WeatherForecast.FORECAST_DAYS];
    private TextView washer;
    private TextView consumption;
    private TextView avgConsumptionView;
    private RangeBarView rangeBar;
    private TextView dabStation;
    private TextView dabTitle;
    private TextView dabArtist;
    private ImageView dabArtwork;
    private Bitmap lastDabArtwork;
    private TextView dabPrevious;
    private TextView dabPlayPause;
    private TextView dabNext;
    private TextView seatbeltStatus;
    private TextView warning;
    private TextView seatbeltWarning;
    private android.animation.ObjectAnimator seatbeltBlink;
    private final SeatbeltAlert seatbeltAlert = new SeatbeltAlert();
    private PagedLauncherLayout pager;
    private LauncherApps launcherApps;
    private boolean appCallbackRegistered;
    private boolean activityVisible;
    private boolean appCatalogDirty;
    private String appCatalogSignature = "";
    private boolean runtimeActive;
    private GermanyOfflinePanel germanyMap;
    private GermanyRoadPositionMatcher germanyRoadMatcher;
    private FallbackLocationFeed fallbackGps;
    private TextView mapMode;
    private long mapCreditShownAt;
    private String mapCreditText = "";
    private String loadedGermanySignature = "";
    private String loadedBackgroundSignature = "";
    private FrameLayout mapSurface;
    private SpeedLimitSignView speedLimitSign;
    private SpeedCameraAlertView speedCameraAlert;
    private boolean mapActivityStarted;
    private boolean mapActivityResumed;
    private boolean mapConnectionOnline;
    private long lastMapNetworkMs;
    private Boolean appliedMapNightMode;
    private static final long MAP_THEME_REFRESH_MS = 60_000L;
    private static final long NETWORK_REFRESH_MS = 3_000L;
    private static final long WEATHER_REFRESH_MS = 3L * 60L * 1_000L;
    private static final long WEATHER_RETRY_MS = 2L * 60L * 1_000L;
    private static final long LOCALITY_LOOKUP_MIN_INTERVAL_MS = 30_000L;
    // Was 500 m. Kept smaller so that near einer Ortsgrenze schneller erneut geprüft wird
    // (siehe pendingLocality-Bestätigung unten, die dadurch nicht ewig auf Bewegung wartet).
    private static final float LOCALITY_LOOKUP_DISTANCE_METERS = 250f;
    private WeatherForecast displayedWeather;
    private String weatherStatusNote = "";
    private boolean weatherRequestInFlight;
    private long lastSidebarNetworkUpdateElapsed;
    private NetworkStatus cachedSidebarNetwork = new NetworkStatus("OFFLINE", "");
    private long lastWeatherAttemptElapsed;
    private Locality displayedLocality;
    // Ein vom aktuell angezeigten Ort abweichendes Ergebnis wird nicht sofort übernommen,
    // sondern erst, wenn eine zweite Abfrage denselben abweichenden Namen bestätigt. Das
    // filtert einzelne Ausreißer nahe einer Ortsgrenze (z.B. wenn Nominatim dort je nach
    // exakter Position mal den einen, mal den anderen Ortsnamen liefert) heraus, ohne einen
    // echten Ortswechsel merklich zu verzögern.
    private Locality pendingLocality;
    private boolean localityRequestInFlight;
    private long lastLocalityAttemptElapsed;
    private boolean weatherRefreshForNewLocality;
    private DabSessionWatcher dabWatcher;
    private DabControls.Status dabControlsStatus;
    /** User setting: the front-left door contact is defective, so its bit is ignored. */
    private boolean doorFlFault = true;
    /** App icons load off the UI thread so the dashboard is usable before the app pages are filled. */
    private final ExecutorService iconLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "launcher-icons");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService weatherLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "launcher-weather");
        thread.setDaemon(true);
        return thread;
    });

    private final Runnable refreshMapTheme = new Runnable() {
        @Override public void run() {
            if (!mapActivityStarted || isFinishing() || isDestroyed()) return;
            applyMapNightMode();
            handler.postDelayed(this, MAP_THEME_REFRESH_MS);
        }
    };

    /** Network, map fallback and weather checks do not belong in the 500 ms vehicle UI loop. */
    private final Runnable refreshNetworkState = new Runnable() {
        @Override public void run() {
            if (!mapActivityStarted || isFinishing() || isDestroyed()) return;
            updateMapConnection();
            maybeRefreshLocality();
            maybeRefreshWeather();
            String germanySignature = GermanyOfflineStore.signature(MainActivity.this);
            if (!loadedGermanySignature.equals(germanySignature) && !isFinishing()) {
                DiagLog.add("Hauptseite neu: Deutschland-Offlinepaket geändert");
                recreate();
                return;
            }
            // Never run GermanyOfflineStore.ready() here: it validates all 14 maps/glyph sets
            // and opens every traffic SQLite DB. This loop runs every 3 seconds on the UI thread.
            handler.postDelayed(this, NETWORK_REFRESH_MS);
        }
    };

    private final Runnable refreshAppPages = new Runnable() {
        @Override public void run() {
            if (!activityVisible || isFinishing() || isDestroyed()) return;
            appCatalogDirty = false;
            if (pager == null) return;
            List<AppEntry> apps = loadLaunchableApps();
            String signature = appSignature(apps);
            // Repeated OEM package-change events must not rebuild the live dashboard.
            if (appCatalogSignature.equals(signature)) return;
            int previousPage = pager.getCurrentPage();
            for (int index = pager.getChildCount() - 1; index > 0; index--)
                pager.removeViewAt(index);
            final int appsPerPage = 18;
            int pageCount = Math.max(1, (apps.size() + appsPerPage - 1) / appsPerPage);
            for (int page = 0; page < pageCount; page++) {
                int from = page * appsPerPage;
                int to = Math.min(apps.size(), from + appsPerPage);
                pager.addView(buildAppsPage(new ArrayList<>(apps.subList(from, to)), page));
            }
            appCatalogSignature = signature;
            pager.setCurrentPage(Math.min(previousPage, pageCount), false);
            DiagLog.add("App-Seiten ohne Neustart aktualisiert: " + apps.size() + " Apps");
        }
    };

    private final LauncherApps.Callback appChanges = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String packageName, UserHandle user) { scheduleAppRefresh(); }
        @Override public void onPackageAdded(String packageName, UserHandle user) { scheduleAppRefresh(); }
        @Override public void onPackageChanged(String packageName, UserHandle user) { scheduleAppRefresh(); }
        @Override public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) { scheduleAppRefresh(); }
        @Override public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) { scheduleAppRefresh(); }
    };

    @Override protected void onCreate(Bundle state) {
        MapFollowDiagnostics.event("Hauptseite onCreate");
        super.onCreate(state);
        IntentFilter dabScreenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        dabScreenFilter.addAction(Intent.ACTION_SCREEN_ON);
        dabScreenFilter.addAction(ACTION_DAB_QUICKSTART_WAKE);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                registerReceiver(dabScreenReceiver, dabScreenFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(dabScreenReceiver, dabScreenFilter);
            dabScreenReceiverRegistered = true;
        } catch (RuntimeException error) { DiagLog.add("DAB-Standby-Empfänger: " + error); }
        DiagLog.add("Hauptseite Activity neu erstellt");
        loadedBackgroundSignature = BackgroundPreferences.signature(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        MediaInfo.restore(this);
        dabWatcher = new DabSessionWatcher(this);
        doorFlFault = VehiclePreferences.doorFlFault(this);
        buildUi();
        registerAppChangeListener();
    }

    private void buildUi() {
        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(0, 0, 0, 0);
        dashboard.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout content = row();
        content.setGravity(Gravity.CENTER_VERTICAL);
        dashboard.addView(content, lp(-1, -1, 0));

        LinearLayout centerStack = new LinearLayout(this);
        centerStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams centerLp = lp(0, -1, 0.67f);
        centerLp.rightMargin = dp(9);
        content.addView(centerStack, centerLp);

        FrameLayout mapFrame = new FrameLayout(this);
        mapFrame.setBackground(GlassSkin.panel(this, true));
        mapFrame.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        mapFrame.setClipToOutline(true);
        mapFrame.setElevation(dp(7));
        centerStack.addView(mapFrame, lp(-1, 0, 1f));

        mapSurface = new FrameLayout(this);
        mapSurface.setClipToOutline(true);
        mapSurface.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });
        mapFrame.addView(mapSurface, new FrameLayout.LayoutParams(-1, -1));
        loadedGermanySignature = GermanyOfflineStore.signature(this);
        boolean initialNightMode = resolveMapNightMode(null);
        appliedMapNightMode = initialNightMode;
        // Startup must stay fluid: only the cheap READY flag is read here. The full validation of
        // maps, glyphs and 14 traffic DBs runs on a background thread (see warmUpAsync).
        GermanyOfflineStore.warmUpAsync(this, () -> {
            if (!isFinishing() && !isDestroyed()) {
                DiagLog.add("Hauptseite neu: Deutschland-Paket war unvollständig");
                recreate();
            }
        });
        if (GermanyOfflineStore.markedReady(this)) {
            try {
                germanyMap = new GermanyOfflinePanel(this, mapSurface, this::updateMapConnection, initialNightMode);
                germanyMap.setVisible(true);
                DiagLog.add("Offline-Karte: Deutschland MapLibre wird geprüft (Startansicht offline)");
            } catch (Throwable t) {
                DiagLog.add("Deutschland-Offlinekarte fehlerhaft: " + t.getClass().getSimpleName() + " " + t.getMessage());
                germanyMap = null;
            }
        } else {
            DiagLog.add("Offline-Karte Deutschland noch nicht bereit – keine Karte, bis sie installiert ist");
        }
        try { fallbackGps = new FallbackLocationFeed(this); }
        catch (Throwable t) {
            DiagLog.add("GPS-Fallback nicht verfügbar: " + t.getClass().getSimpleName());
            fallbackGps = null;
        }
        if (germanyMap != null) {
            try {
                germanyRoadMatcher = new GermanyRoadPositionMatcher(this, mapSurface,
                        (source, display, snapped) -> {
                            MapFollowDiagnostics.matcherResult(snapped);
                            deliverGermanyCameraPosition(display);
                        });
                germanyRoadMatcher.warmUp();
                DiagLog.add("Deutschland Straßenkorrektur: "
                        + (germanyRoadMatcher.available() ? "bereit (Kartenkacheln " + germanyRoadMatcher.tileRegion() + ")" : "keine Straßendaten"));
            } catch (Throwable t) {
                germanyRoadMatcher = null;
                DiagLog.add("Deutschland Straßenkorrektur nicht verfügbar: "
                        + t.getClass().getSimpleName());
            }
        }
        if (germanyMap == null) {
            LinearLayout noMap = new LinearLayout(this);
            noMap.setOrientation(LinearLayout.VERTICAL);
            noMap.setGravity(Gravity.CENTER);
            noMap.setPadding(dp(20), dp(20), dp(20), dp(20));
            noMap.setBackground(GlassSkin.panel(this, true));
            TextView pin = label("⌖", 56, GlassSkin.ACCENT, Typeface.NORMAL);
            pin.setGravity(Gravity.CENTER);
            TextView title = label("KARTE", 22, GlassSkin.TEXT, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            TextView hint = label("Karte konnte nicht geöffnet werden\nBitte Diagnose prüfen", 14, GlassSkin.MUTED, Typeface.NORMAL);
            hint.setGravity(Gravity.CENTER);
            noMap.addView(pin);
            noMap.addView(title);
            noMap.addView(hint);
            noMap.setOnClickListener(v -> startActivity(new Intent(this, OfflineMapsActivity.class)));
            mapSurface.addView(noMap, new FrameLayout.LayoutParams(-1, -1));
        }

        View mapBorder = new View(this);
        mapBorder.setBackground(GlassSkin.transparentBorder(this, 22));
        mapFrame.addView(mapBorder, new FrameLayout.LayoutParams(-1, -1));

        speedLimitSign = new SpeedLimitSignView(this);
        FrameLayout.LayoutParams speedLp =
                new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.TOP | Gravity.RIGHT);
        speedLp.setMargins(0, dp(12), dp(12), 0);
        mapFrame.addView(speedLimitSign, speedLp);

        speedCameraAlert = new SpeedCameraAlertView(this);
        FrameLayout.LayoutParams cameraLp =
                new FrameLayout.LayoutParams(dp(86), dp(70), Gravity.TOP | Gravity.LEFT);
        cameraLp.setMargins(dp(12), dp(12), 0, 0);
        mapFrame.addView(speedCameraAlert, cameraLp);

        if (germanyMap != null) {
            germanyMap.setSpeedLimitConsumer(this::updateSpeedLimitSign);
            germanyMap.setSpeedCameraConsumer(this::updateSpeedCameraAlert);
        }

        warning = label("", 14, Color.WHITE, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setMaxLines(1);
        warning.setEllipsize(android.text.TextUtils.TruncateAt.END);
        warning.setAutoSizeTextTypeUniformWithConfiguration(
                10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        warning.setVisibility(View.GONE);
        warning.setBackground(rounded(Color.argb(235, 190, 42, 42), dp(12), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams warnLp = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        warnLp.setMargins(dp(10), dp(10), dp(10), 0);
        mapFrame.addView(warning, warnLp);

        // Gurt-Warnung: orange, blinkend, nur bei Fahrt mit offenem Gurt; steht unter der Türwarnung.
        seatbeltWarning = label("⚠  Gurt nicht angelegt!", 14, Color.WHITE, Typeface.BOLD);
        seatbeltWarning.setGravity(Gravity.CENTER);
        seatbeltWarning.setMaxLines(1);
        seatbeltWarning.setEllipsize(android.text.TextUtils.TruncateAt.END);
        seatbeltWarning.setAutoSizeTextTypeUniformWithConfiguration(
                10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        seatbeltWarning.setVisibility(View.GONE);
        seatbeltWarning.setBackground(rounded(Color.argb(240, 245, 124, 0), dp(12), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams beltLp = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        beltLp.setMargins(dp(10), dp(10), dp(10), 0);
        mapFrame.addView(seatbeltWarning, beltLp);

        mapMode = label("", 10, GlassSkin.TEXT, Typeface.BOLD);
        mapMode.setMaxLines(1);
        mapMode.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mapMode.setAutoSizeTextTypeUniformWithConfiguration(
                7, 10, 1, TypedValue.COMPLEX_UNIT_SP);
        mapMode.setBackground(GlassSkin.chip(this));
        mapMode.setPadding(dp(8), 0, dp(8), 0);
        mapMode.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))); }
            catch (Exception e) { DiagLog.add("OSM-Lizenzseite: " + e.getClass().getSimpleName()); }
        });
        FrameLayout.LayoutParams modeLp = new FrameLayout.LayoutParams(-2, dp(25), Gravity.BOTTOM | Gravity.LEFT);
        modeLp.setMargins(dp(12), 0, 0, dp(43));
        mapFrame.addView(mapMode, modeLp);

        TextView mapInfo = label("ⓘ", 20, GlassSkin.TEXT, Typeface.BOLD);
        mapInfo.setGravity(Gravity.CENTER);
        mapInfo.setContentDescription("Kartenquellen und Lizenzen");
        // No filled chip: transparent, with a soft shadow so the glyph stays readable on the map.
        mapInfo.setBackground(null);
        mapInfo.setShadowLayer(dp(3), 0f, 0f, 0xCC000000);
        mapInfo.setOnClickListener(v -> showMapCredits());
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.LEFT);
        infoLp.setMargins(dp(6), 0, 0, dp(6));
        mapFrame.addView(mapInfo, infoLp);
        updateMapConnection();
        addWeatherPanel(centerStack);

        LinearLayout rightStack = new LinearLayout(this);
        rightStack.setOrientation(LinearLayout.VERTICAL);
        content.addView(rightStack, lp(0, -1, 0.33f));

        // v0.31.3: Die separate Shortcut-Kachel rechts entfällt. Die echten App-Icons
        // sitzen jetzt komplett in der schmaleren linken Leiste; dadurch haben Fahrzeug
        // und DAB deutlich mehr Höhe und wirken näher am CarPlay-Dashboard.
        LinearLayout vehicleCard = buildVehicleGlassCard();
        LinearLayout.LayoutParams vehicleLp = lp(-1, 0, 0.40f);
        rightStack.addView(vehicleCard, vehicleLp);

        LinearLayout dabCard = buildDabGlassCard();
        LinearLayout.LayoutParams dabLp = lp(-1, 0, 0.60f);
        dabLp.topMargin = dp(8);
        rightStack.addView(dabCard, dabLp);

        List<AppEntry> launchableApps = loadLaunchableApps();
        appCatalogSignature = appSignature(launchableApps);
        final int appsPerPage = 18;
        int appPageCount = Math.max(1, (launchableApps.size() + appsPerPage - 1) / appsPerPage);

        pager = new PagedLauncherLayout(this);
        pager.setPageGestureListener(new PagedLauncherLayout.PageGestureListener() {
            @Override public void onGestureStart() {
            }
            @Override public void onPageSwipe() {
                if (germanyMap != null) germanyMap.restorePageGesture();
            }
        });
        pager.addView(dashboard);
        for (int page = 0; page < appPageCount; page++) {
            int from = page * appsPerPage;
            int to = Math.min(launchableApps.size(), from + appsPerPage);
            pager.addView(buildAppsPage(new ArrayList<>(launchableApps.subList(from, to)), page));
        }

        FrameLayout host = new FrameLayout(this);
        host.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = row();
        shell.setPadding(dp(8), dp(8), dp(10), dp(8));
        LinearLayout sidebar = buildGlassSidebar();
        LinearLayout.LayoutParams sidebarLp = lp(dp(64), -1, 0);
        sidebarLp.rightMargin = dp(8);
        shell.addView(sidebar, sidebarLp);
        shell.addView(pager, lp(0, -1, 1f));
        host.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
    }

    private LinearLayout buildGlassSidebar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(dp(4), dp(6), dp(4), dp(6));
        bar.setBackground(GlassSkin.sidebar(this));
        bar.setElevation(dp(8));

        // v0.31.3: Uhr wie bei CarPlay ohne orange Hinterlegung. Der Mobilfunkbereich
        // bekommt zwei Zeilen, damit Anbieter deutlich größer als Netztyp/Empfang ist.
        sidebarClock = label("--:--", 16, GlassSkin.TEXT, Typeface.BOLD);
        sidebarClock.setGravity(Gravity.CENTER);
        sidebarClock.setBackgroundColor(Color.TRANSPARENT);
        bar.addView(sidebarClock, lp(-1, dp(25), 0));

        sidebarDate = label("--.--.--", 9, GlassSkin.MUTED, Typeface.BOLD);
        sidebarDate.setGravity(Gravity.CENTER);
        sidebarDate.setMaxLines(1);
        sidebarDate.setAutoSizeTextTypeUniformWithConfiguration(6, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams dateLp = lp(-1, dp(13), 0);
        dateLp.topMargin = 0;
        bar.addView(sidebarDate, dateLp);

        sidebarWeekday = label("Wochentag", 9, GlassSkin.MUTED, Typeface.BOLD);
        sidebarWeekday.setGravity(Gravity.CENTER);
        sidebarWeekday.setMaxLines(1);
        sidebarWeekday.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sidebarWeekday.setAutoSizeTextTypeUniformWithConfiguration(
                6, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        bar.addView(sidebarWeekday, lp(-1, dp(13), 0));

        sidebarNetwork = label("Netz", 10, GlassSkin.TEXT, Typeface.BOLD);
        sidebarNetwork.setGravity(Gravity.CENTER);
        sidebarNetwork.setMaxLines(1);
        sidebarNetwork.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sidebarNetwork.setAutoSizeTextTypeUniformWithConfiguration(7, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams carrierLp = lp(-1, dp(18), 0);
        carrierLp.topMargin = dp(4);
        bar.addView(sidebarNetwork, carrierLp);

        sidebarSignal = label("▂▄▆█ 5G", 8, GlassSkin.MUTED, Typeface.BOLD);
        sidebarSignal.setGravity(Gravity.CENTER);
        sidebarSignal.setMaxLines(1);
        sidebarSignal.setAutoSizeTextTypeUniformWithConfiguration(6, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        bar.addView(sidebarSignal, lp(-1, dp(15), 0));

        sidebarGps = label("GPS --", 8, GlassSkin.MUTED, Typeface.BOLD);
        sidebarGps.setGravity(Gravity.CENTER);
        sidebarGps.setMaxLines(1);
        sidebarGps.setAutoSizeTextTypeUniformWithConfiguration(
                6, 8, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams gpsLp = lp(-1, dp(17), 0);
        gpsLp.topMargin = dp(2);
        bar.addView(sidebarGps, gpsLp);

        View spacer = new View(this);
        bar.addView(spacer, lp(-1, 0, 1f));
        addSidebarButton(bar, DockIconView.MAPS, "Google Maps", "com.google.android.apps.maps",
                () -> AppLauncher.launchMaps(this));
        addSidebarButton(bar, DockIconView.DAB, "DABdream+", AppLauncher.findDabPackage(this),
                () -> AppLauncher.launchDab(this));
        addSidebarButton(bar, DockIconView.CARPLAY, "CarPlay", AppLauncher.findCarPlayPackage(this),
                () -> AppLauncher.launchCarPlay(this));
        addSidebarButton(bar, DockIconView.YOUTUBE, "YouTube", "com.google.android.youtube",
                () -> AppLauncher.launchFirst(this, "YouTube", "com.google.android.youtube"));

        // Launcher-Einstellungen bleiben dauerhaft unten in der linken Leiste sichtbar.
        // Absichtlich ohne Paket-Icon: So wird immer unser eigenes Zahnrad angezeigt
        // und nicht das Android-Systemeinstellungen-Icon geladen oder ausgeblendet.
        View lower = new View(this);
        bar.addView(lower, lp(-1, 0, 1f));
        addSidebarButton(bar, DockIconView.SETTINGS, "Launcher-Einstellungen", null,
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        addSidebarButton(bar, DockIconView.APPS, "Alle Apps", null, () -> {
            if (pager != null) pager.setCurrentPage(1, true);
        });
        return bar;
    }

    private void addSidebarButton(LinearLayout bar, int fallbackIcon, String description,
                                  String packageName, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setElevation(0f);
        addOriginalAppIcon(button, fallbackIcon, packageName, dp(31));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = lp(dp(45), dp(45), 0);
        p.topMargin = dp(5);
        bar.addView(button, p);
    }

    private void addOriginalAppIcon(FrameLayout holder, int fallbackIcon, String packageName, int size) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setPadding(dp(1), dp(1), dp(1), dp(1));
        holder.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        if (packageName == null || packageName.trim().isEmpty()) {
            holder.removeView(image);
            DockIconView graphic = new DockIconView(this, fallbackIcon);
            holder.addView(graphic, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            return;
        }
        try {
            iconLoader.execute(() -> {
                Drawable drawable;
                try { drawable = AppLauncher.loadLauncherIcon(MainActivity.this, packageName); }
                catch (Throwable missing) { drawable = null; }
                final Drawable result = drawable;
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (result != null) image.setImageDrawable(result);
                    else {
                        holder.removeView(image);
                        DockIconView graphic = new DockIconView(this, fallbackIcon);
                        holder.addView(graphic, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
                    }
                });
            });
        } catch (RejectedExecutionException stopped) {
            holder.removeView(image);
            DockIconView graphic = new DockIconView(this, fallbackIcon);
            holder.addView(graphic, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }
    }

    private LinearLayout buildVehicleGlassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(8), dp(9), dp(7));
        card.setBackground(GlassSkin.panel(this, false));
        card.setElevation(dp(7));
        card.setOnClickListener(v -> AppLauncher.launchFirst(this, "Polo CAN", "de.ronny.polocan", "de.ronny.polocan.debug"));

        // Kopfzeile: Tank, Balken, Reichweite - rechts klein Bordnetz und Außentemperatur.
        fuel = metricValue("-- L", GlassSkin.TEXT, 1);
        estimatedRange = metricValue("ca. -- km", GlassSkin.TEXT, 1);
        voltage = metricValue("-- V", GlassSkin.TEXT, 1);
        outside = metricValue("-- °C", GlassSkin.TEXT, 1);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), 0, dp(2), dp(3));
        addVehicleRangeRow(header);
        addVehicleSmallReading(header, "⚡", "Bordnetz", voltage, 0.20f);
        addVehicleSmallReading(header, "🌡", "Außen", outside, 0.20f);
        card.addView(header, lp(-1, 0, 1f));

        // Vier große Werte: Tempo und Drehzahl am größten, darunter Aktuell und Ø 100 KM.
        speed = bigValue("-- km/h", 10, 30, 0.4f);
        rpm = bigValue("-- U/min", 10, 30, 0.4f);
        consumption = bigValue("-- l", 9, 24, 0.75f);
        avgConsumptionView = bigValue("-- l", 9, 24, 0.75f);
        LinearLayout upper = row();
        addVehicleTile(upper, "TEMPO", speed, true);
        addVehicleTile(upper, "DREHZAHL", rpm, true);
        card.addView(upper, lp(-1, 0, 2.1f));
        LinearLayout lower = row();
        addVehicleTile(lower, "AKTUELL", consumption, false);
        addVehicleTile(lower, "Ø 100 KM", avgConsumptionView, false);
        card.addView(lower, lp(-1, 0, 1.5f));

        // Gurt und Wasser bleiben immer sichtbar.
        LinearLayout status = row();
        status.setPadding(0, dp(3), 0, 0);
        seatbeltStatus = metricValue("--", GlassSkin.MUTED, 1);
        washer = metricValue("--", GlassSkin.MUTED, 1);
        addStatusChip(status, "Gurt", seatbeltStatus);
        addStatusChip(status, "Wasser", washer);
        card.addView(status, lp(-1, 0, 0.95f));
        return card;
    }

    private TextView bigValue(String text, int minSp, int maxSp, float unitScale) {
        UnitValueView v = new UnitValueView(this, unitScale);
        v.setText(text);
        v.setTextColor(GlassSkin.TEXT);
        v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMaxLines(1);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setTextSize(maxSp);
        v.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        return v;
    }

    private void addVehicleTile(LinearLayout row, String caption, TextView value, boolean primary) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(7), dp(2), dp(6), dp(2));
        tile.setBackground(primary ? GlassSkin.chip(this) : GlassSkin.subtleChip(this));
        TextView heading = label(caption, 7, GlassSkin.ACCENT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setMaxLines(1);
        value.setGravity(Gravity.CENTER);
        tile.addView(heading, lp(-1, dp(11), 0));
        tile.addView(value, lp(-1, 0, 1f));
        LinearLayout.LayoutParams p = lp(0, -1, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(tile, p);
    }

    private void addVehicleSmallReading(LinearLayout parent, String symbol, String caption,
                                        TextView value, float weight) {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setContentDescription(caption);
        TextView icon = label(symbol, 10, GlassSkin.TEXT, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, lp(dp(14), -1, 0));
        value.setTextSize(11);
        value.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        value.setMaxLines(1);
        value.setAutoSizeTextTypeUniformWithConfiguration(6, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        box.addView(value, lp(0, -1, 1f));
        LinearLayout.LayoutParams p = lp(0, -1, weight);
        p.leftMargin = dp(3);
        parent.addView(box, p);
    }

    private void addVehicleRangeRow(LinearLayout line) {
        TextView icon = label("⛽", 12, GlassSkin.TEXT, Typeface.NORMAL);
        icon.setGravity(Gravity.CENTER);
        line.addView(icon, lp(dp(15), -1, 0));

        fuel.setTextSize(12);
        fuel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        fuel.setMaxLines(1);
        fuel.setAutoSizeTextTypeUniformWithConfiguration(7, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams fuelLp = lp(0, -1, .13f);
        fuelLp.rightMargin = dp(3);
        line.addView(fuel, fuelLp);

        rangeBar = new RangeBarView(this);
        LinearLayout.LayoutParams barLp = lp(0, dp(10), .20f);
        barLp.leftMargin = dp(1);
        barLp.rightMargin = dp(4);
        line.addView(rangeBar, barLp);

        estimatedRange.setTextSize(11);
        estimatedRange.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        estimatedRange.setMaxLines(1);
        estimatedRange.setAutoSizeTextTypeUniformWithConfiguration(7, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        line.addView(estimatedRange, lp(0, -1, .27f));
    }

    private void addStatusChip(LinearLayout row, String caption, TextView value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), 0, dp(8), 0);
        box.setBackground(GlassSkin.subtleChip(this));
        TextView heading = label(caption + "  ·  ", 9, GlassSkin.MUTED, Typeface.BOLD);
        value.setTextSize(10);
        value.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        value.setAutoSizeTextTypeUniformWithConfiguration(6, 10, 1, TypedValue.COMPLEX_UNIT_SP);
        box.addView(heading);
        box.addView(value, lp(0, -1, 1f));
        LinearLayout.LayoutParams p = lp(0, -1, 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(box, p);
    }

    private LinearLayout buildDabGlassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(GlassSkin.panel(this, true));
        card.setElevation(dp(7));
        card.setOnClickListener(v -> AppLauncher.launchDab(this));

        dabStation = label("DABdream+", 17, GlassSkin.TEXT, Typeface.BOLD);
        dabStation.setGravity(Gravity.CENTER);
        dabStation.setMaxLines(1);
        dabStation.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dabStation.setAutoSizeTextTypeUniformWithConfiguration(
                10, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        card.addView(dabStation, lp(-1, dp(28), 0));

        FrameLayout artworkFrame = new FrameLayout(this);
        artworkFrame.setPadding(dp(4), dp(4), dp(4), dp(4));
        artworkFrame.setBackground(GlassSkin.chip(this));

        dabArtwork = new RoundedArtworkView(this, dp(11));
        dabArtwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dabArtwork.setAdjustViewBounds(true);
        dabArtwork.setVisibility(View.INVISIBLE);
        artworkFrame.addView(dabArtwork,
                new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        // Etwas kleiner als zuvor, damit die Bedientasten garantiert innerhalb der Kachel bleiben.
        LinearLayout.LayoutParams artLp = lp(dp(98), dp(98), 0);
        artLp.gravity = Gravity.CENTER_HORIZONTAL;
        artLp.topMargin = dp(3);
        artLp.bottomMargin = dp(5);
        card.addView(artworkFrame, artLp);

        dabTitle = label("Noch keine Titeldaten", 16, GlassSkin.TEXT, Typeface.BOLD);
        dabTitle.setGravity(Gravity.CENTER);
        dabTitle.setMaxLines(2);
        dabTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dabTitle.setAutoSizeTextTypeUniformWithConfiguration(
                10, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams titleLp = lp(-1, dp(42), 0);
        titleLp.leftMargin = dp(5);
        titleLp.rightMargin = dp(5);
        card.addView(dabTitle, titleLp);

        dabArtist = label("Interpret nicht verfügbar", 13, GlassSkin.MUTED, Typeface.NORMAL);
        dabArtist.setGravity(Gravity.CENTER);
        dabArtist.setMaxLines(2);
        dabArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dabArtist.setAutoSizeTextTypeUniformWithConfiguration(
                8, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams artistLp = lp(-1, dp(32), 0);
        artistLp.leftMargin = dp(8);
        artistLp.rightMargin = dp(8);
        card.addView(dabArtist, artistLp);

        View mediaSpacer = new View(this);
        card.addView(mediaSpacer, lp(-1, 0, 1f));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp = lp(-1, dp(44), 0);
        controlsLp.topMargin = dp(2);

        dabPrevious = dabButton("", "Vorheriger DAB-Sender", DabControls.PREVIOUS);
        dabPlayPause = dabButton("▶", "DAB abspielen oder pausieren", DabControls.TOGGLE);
        dabNext = dabButton("", "Nächster DAB-Sender", DabControls.NEXT);

        LinearLayout.LayoutParams p1 = lp(dp(42), dp(42), 0);
        p1.rightMargin = dp(8);
        LinearLayout.LayoutParams p2 = lp(dp(42), dp(42), 0);
        p2.leftMargin = dp(8);
        p2.rightMargin = dp(8);
        LinearLayout.LayoutParams p3 = lp(dp(42), dp(42), 0);
        p3.leftMargin = dp(8);

        controls.addView(dabPrevious, p1);
        controls.addView(dabPlayPause, p2);
        controls.addView(dabNext, p3);
        card.addView(controls, controlsLp);
        return card;
    }

    private void registerAppChangeListener() {
        try {
            launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
            if (launcherApps != null) {
                launcherApps.registerCallback(appChanges, handler);
                appCallbackRegistered = true;
            }
        } catch (Throwable t) {
            DiagLog.add("LauncherApps Callback nicht verfügbar: " + t.getClass().getSimpleName());
        }
    }

    private void scheduleAppRefresh() {
        appCatalogDirty = true;
        if (!activityVisible) return;
        handler.removeCallbacks(refreshAppPages);
        handler.postDelayed(refreshAppPages, 500L);
    }

    private void updateMapConnection() {
        if (mapMode == null) return;
        boolean connected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            // Some Android head units report cellular Internet without VALIDATED despite working SIM data.
            connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } catch (RuntimeException e) { DiagLog.add("Karten-Netzstatus: " + e.getClass().getSimpleName()); }
        long now = android.os.SystemClock.elapsedRealtime();
        if (connected) lastMapNetworkMs = now;
        else if (mapConnectionOnline && now - lastMapNetworkMs < 4000L) connected = true;
        if (mapConnectionOnline != connected) {
            mapConnectionOnline = connected;
            DiagLog.add("Kartenverbindung: " + (connected ? "online" : "offline"));
        }
        if (germanyMap != null) {
            updateGermanyMapConnection();
            return;
        }
        // Without the installed Germany package there is no map (the online map has been removed).
        if (fallbackGps != null) {
            fallbackGps.setRawConsumer(null);
            fallbackGps.setConsumer(null);
            fallbackGps.stop();
        }
        showMapCreditBriefly("  KEINE OFFLINE-KARTE · bitte im Menü unter Karte laden  ");
    }

    private void updateGermanyMapConnection() {
        if (germanyMap == null) return;
        germanyMap.setVisible(true);
        if (germanyMap.failed()) {
            // Show the failure in the map area instead of a black rectangle; details are in the diagnosis log.
            showMapCreditBriefly("  OFFLINE-KARTE FEHLERHAFT – siehe Diagnose  ");
            return;
        }
        if (fallbackGps != null) {
            // Raw GPS remains authoritative for speed limits, cameras and settlement zoom.
            // Only the already camera-filtered fix may optionally pass through the conservative
            // road matcher. Its 350 ms fallback guarantees that map follow can never be starved.
            fallbackGps.setRawConsumer(germanyMap::acceptAnalysisLocation);
            fallbackGps.setConsumer(this::acceptGermanyCameraPosition);
            if (mapActivityStarted) fallbackGps.start();
        }
        if (mapActivityStarted) {
            germanyMap.start();
            if (mapActivityResumed) germanyMap.resume();
        }
        showMapCreditBriefly("  OFFLINE DEUTSCHLAND · © OpenStreetMap-Mitwirkende  ");
    }

    private void acceptGermanyCameraPosition(Location location) {
        if (location == null) return;
        GermanyRoadPositionMatcher matcher = germanyRoadMatcher;
        if (matcher != null && matcher.available()) {
            MapFollowDiagnostics.matcherQueued();
            matcher.accept(location);
            return;
        }
        deliverGermanyCameraPosition(location);
    }

    private void deliverGermanyCameraPosition(Location location) {
        if (location == null) return;
        if (germanyMap != null && !germanyMap.failed()) {
            MapFollowDiagnostics.routedGermany();
            germanyMap.acceptLocation(location);
        } else MapFollowDiagnostics.routedNobody();
    }

    private void updateSpeedLimitSign(Integer value) {
        if (speedLimitSign == null) return;
        speedLimitSign.setLimit(value == null ? SpeedLimitValue.UNKNOWN : value);
    }

    private void updateSpeedCameraAlert(Integer distanceMetres) {
        if (speedCameraAlert == null) return;
        speedCameraAlert.setDistance(distanceMetres == null ? -1 : distanceMetres);
    }

    private void showMapCreditBriefly(String text) {
        long now = android.os.SystemClock.uptimeMillis();
        if (!text.equals(mapCreditText)) {
            mapCreditText = text;
            mapCreditShownAt = now;
            setTextIfChanged(mapMode, text);
        }
        mapMode.setVisibility(now - mapCreditShownAt < 5000L ? View.VISIBLE : View.GONE);
    }

    private void showMapCredits() {
        android.app.AlertDialog dialog = GlassSkin.dialog(this).create();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(16), dp(18), dp(16));
        shell.setBackground(GlassSkin.panel(this, false));

        TextView title = label("Kartenquellen und Lizenzen", 19, GlassSkin.TEXT, Typeface.BOLD);
        title.setPadding(dp(4), dp(2), dp(4), dp(10));
        shell.addView(title, lp(-1, -2, 0));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        TextView credits = label("", 15, GlassSkin.TEXT, Typeface.NORMAL);
        credits.setLineSpacing(0f, 1.05f);
        credits.setPadding(dp(4), dp(2), dp(4), dp(6));
        credits.setText(android.text.Html.fromHtml(
                "Kartendaten: © <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap-Mitwirkende</a> (ODbL)<br><br>"
                + "Karte: Geofabrik Shortbread-Kacheln, Stil <a href=\"https://github.com/versatiles-org/versatiles-style\">VersaTiles</a> (MIT), Symbole Maki/Temaki (CC0), Darstellung MapLibre<br><br>"
                + "Wetter: <a href=\"https://open-meteo.com/\">Open-Meteo</a><br>"
                + "Ortsnamen: <a href=\"https://nominatim.openstreetmap.org/\">Nominatim</a> / OpenStreetMap",
                android.text.Html.FROM_HTML_MODE_LEGACY));
        credits.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        credits.setLinkTextColor(GlassSkin.ACCENT);
        scroll.addView(credits, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = lp(-1, 0, 1f);
        scrollLp.bottomMargin = dp(12);
        shell.addView(scroll, scrollLp);

        LinearLayout actions = row();
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView close = label("SCHLIESSEN", 13, GlassSkin.TEXT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(18), dp(9), dp(18), dp(9));
        close.setBackground(GlassSkin.chip(this));
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(close, lp(-2, dp(40), 0));
        shell.addView(actions, lp(-1, -2, 0));

        dialog.setView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dp(640), Math.min(dp(500), getResources().getDisplayMetrics().heightPixels - dp(40)));
        }
    }


    /** A launchable app with its label, resolved exactly once (label lookups are costly Binder/resource work). */
    private static final class AppEntry {
        final ResolveInfo info;
        final String label;
        AppEntry(ResolveInfo info, String label) {
            this.info = info;
            this.label = label;
        }
    }

    private List<AppEntry> loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> raw;
        try { raw = pm.queryIntentActivities(query, PackageManager.MATCH_ALL); }
        catch (Throwable t) { raw = new ArrayList<>(); }

        List<AppEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : raw) {
            if (info == null || info.activityInfo == null || !info.activityInfo.enabled
                    || !info.activityInfo.exported || info.activityInfo.applicationInfo == null
                    || !info.activityInfo.applicationInfo.enabled) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName())) continue;
            String component = pkg + "/" + info.activityInfo.name;
            if (seen.add(component)) result.add(new AppEntry(info, safeAppLabel(info, pm)));
        }
        Collator collator = Collator.getInstance(Locale.GERMANY);
        result.sort((a, b) -> collator.compare(a.label, b.label));
        return result;
    }

    private String appSignature(List<AppEntry> apps) {
        StringBuilder signature = new StringBuilder(apps.size() * 64);
        for (AppEntry app : apps) {
            signature.append(app.info.activityInfo.packageName).append('/')
                    .append(app.info.activityInfo.name).append(':')
                    .append(app.label).append('\n');
        }
        return signature.toString();
    }

    private View buildAppsPage(List<AppEntry> apps, int page) {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18), dp(8), dp(18), dp(12));
        screen.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = row();
        TextView title = label("ALLE APPS", 22, GlassSkin.TEXT, Typeface.BOLD);
        header.addView(title, lp(0, dp(48), 1f));
        screen.addView(header, lp(-1, dp(50), 0));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(false);
        for (int index = 0; index < 18; index++) {
            View tile = index < apps.size() ? buildAppTile(apps.get(index)) : new View(this);
            int row = index / 6;
            int column = index % 6;
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                    GridLayout.spec(row, 1, 1f), GridLayout.spec(column, 1, 1f));
            p.width = 0;
            p.height = 0;
            p.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(tile, p);
        }
        screen.addView(grid, lp(-1, 0, 1f));

        TextView hint = label(page == 0 ? "Nach rechts wischen: zurück zum Polo-Dashboard" : "Horizontal wischen: weitere Seiten", 12, GlassSkin.MUTED, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        screen.addView(hint, lp(-1, dp(28), 0));
        return screen;
    }

    private View buildAppTile(AppEntry app) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(5), dp(4), dp(4));
        tile.setBackgroundColor(Color.TRANSPARENT);
        tile.setElevation(0f);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        loadIconAsync(app.info, icon);
        tile.addView(icon, lp(dp(60), 0, 1f));

        TextView name = label(app.label, 12, GlassSkin.TEXT, Typeface.BOLD);
        name.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tile.addView(name, lp(-1, dp(38), 0));
        tile.setOnClickListener(v -> launchResolvedApp(app.info));
        return tile;
    }

    private void loadIconAsync(ResolveInfo info, ImageView target) {
        PackageManager pm = getPackageManager();
        try {
            iconLoader.execute(() -> {
                Drawable drawable;
                try { drawable = info.loadIcon(pm); } catch (Throwable ignored) { return; }
                target.post(() -> target.setImageDrawable(drawable));
            });
        } catch (RejectedExecutionException ignored) { /* activity is being destroyed */ }
    }

    private static String safeAppLabel(ResolveInfo info, PackageManager pm) {
        try {
            CharSequence loaded = info.loadLabel(pm);
            if (loaded != null && !loaded.toString().trim().isEmpty()) return loaded.toString().trim();
        } catch (Throwable ignored) {}
        if (info != null && info.activityInfo != null && info.activityInfo.packageName != null)
            return info.activityInfo.packageName;
        return "Unbekannte App";
    }

    private void launchResolvedApp(ResolveInfo info) {
        try {
            if (info != null && info.activityInfo != null
                    && AppLauncher.isCarPlayPackage(this, info.activityInfo.packageName)) {
                CarPlayAutoReturn.markManualLaunch(this);
            }
            // Like Launcher3, close the all-apps pages before handing off to an app.
            // Pressing Back or Home afterwards must reveal the dashboard, not the drawer.
            if (pager != null) pager.setCurrentPage(0, false);
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "App konnte nicht gestartet werden", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) && pager != null) {
            pager.setCurrentPage(0, true);
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (pager != null && pager.getCurrentPage() != 0) {
            pager.setCurrentPage(0, true);
        }
        // On the dashboard a launcher stays open; it must not reveal the previous app.
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            if (sidebarClock != null) setTextIfChanged(sidebarClock, timeFormat.format(now));
            if (sidebarDate != null) setTextIfChanged(sidebarDate, sidebarDateFormat.format(now));
            if (sidebarWeekday != null) {
                setTextIfChanged(sidebarWeekday, sidebarWeekdayFormat.format(now));
            }

            VehicleState s = VehicleRepository.snapshot();
            double shownTank = valid(s.fuelLiters) ? s.fuelLiters : RangeDisplayStore.tankLitres(MainActivity.this);
            boolean liveTank = valid(s.fuelLiters);
            setMetricState(fuel, valid(shownTank)
                    ? String.format(Locale.GERMANY, "%.0f L", shownTank)
                    : "-- L", liveTank, s.fuelUpdatedAtMs > 0L || valid(shownTank));
            int remaining = RangeTrackingService.remainingKm(MainActivity.this);
            setTextIfChanged(estimatedRange, remaining >= 0 ? "ca. " + remaining + " km" : "ca. -- km");
            if (rangeBar != null) rangeBar.setFraction(valid(shownTank) ? shownTank / POLO_TANK_CAPACITY_L : 0d);
            updateConsumption(s);
            setMetricState(speed, valid(s.speedKmh)
                    ? String.format(Locale.GERMANY, "%.0f km/h", s.speedKmh) : "-- km/h ALT",
                    valid(s.speedKmh), s.speedUpdatedAtMs > 0L);
            setMetricState(rpm, s.rpm >= 0 ? s.rpm + " U/min" : "-- U/min ALT",
                    s.rpm >= 0, s.rpmUpdatedAtMs > 0L);
            setMetricState(voltage, valid(s.voltage)
                    ? String.format(Locale.GERMANY, "%.1f V", s.voltage) : "-- V ALT",
                    valid(s.voltage), s.voltageUpdatedAtMs > 0L);
            setMetricState(outside, valid(s.outsideC)
                    ? String.format(Locale.GERMANY, "%.1f °C", s.outsideC) : "-- °C ALT",
                    valid(s.outsideC), s.outsideUpdatedAtMs > 0L);
            updateWasherStatus(s);
            updateSeatbeltStatus(s);
            updateSidebarStatus();

            showDoorWarning(s);
            showSeatbeltWarning(s);
            // Event-driven: the watcher only retries registration here (cheap), no Binder call per tick.
            dabWatcher.poll();
            dabControlsStatus = dabWatcher.status();
            updateDabButtons();
            setTextIfChanged(dabStation, MediaInfo.station());
            setTextIfChanged(dabTitle, MediaInfo.title());
            setTextIfChanged(dabArtist, MediaInfo.artist());
            Bitmap artwork = MediaInfo.artwork();
            if (lastDabArtwork != artwork) {
                lastDabArtwork = artwork;
                dabArtwork.setImageBitmap(artwork);
                dabArtwork.setVisibility(artwork == null ? View.INVISIBLE : View.VISIBLE);
            }
            if (activityVisible) handler.postDelayed(this, 500L);
        }
    };

    private static final class NetworkStatus {
        final String carrier;
        final String signal;
        NetworkStatus(String carrier, String signal) {
            this.carrier = carrier == null ? "" : carrier;
            this.signal = signal == null ? "" : signal;
        }
        boolean offline() { return "OFFLINE".equals(carrier); }
    }

    private void updateSidebarStatus() {
        if (sidebarNetwork != null) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (lastSidebarNetworkUpdateElapsed == 0L || now - lastSidebarNetworkUpdateElapsed >= 2_000L) {
                lastSidebarNetworkUpdateElapsed = now;
                cachedSidebarNetwork = sidebarNetworkStatus();
            }
            setTextIfChanged(sidebarNetwork, cachedSidebarNetwork.carrier);
            if (sidebarSignal != null) setTextIfChanged(sidebarSignal, cachedSidebarNetwork.signal);
            setColorIfChanged(sidebarNetwork, cachedSidebarNetwork.offline()
                    ? GlassSkin.WARNING : GlassSkin.TEXT);
            if (sidebarSignal != null) setColorIfChanged(sidebarSignal, cachedSidebarNetwork.offline()
                    ? GlassSkin.WARNING : GlassSkin.MUTED);
        }
        if (sidebarGps != null) {
            Location location = latestMapLocation();
            boolean fresh = false;
            if (location != null) {
                long age = location.getTime() <= 0L ? 0L : WeatherStore.wallTimeMs() - location.getTime();
                fresh = age >= -1000L && age <= 15000L;
            }
            setTextIfChanged(sidebarGps, fresh ? "GPS ●" : "GPS ○");
            setColorIfChanged(sidebarGps, fresh ? GlassSkin.GREEN : GlassSkin.MUTED);
        }
    }

    private NetworkStatus sidebarNetworkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                return new NetworkStatus("OFFLINE", "");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return new NetworkStatus("WLAN", "● ONLINE");
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                return new NetworkStatus("NETZ", "● ONLINE");

            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String carrier = tm == null ? "" : tm.getNetworkOperatorName();
            if (carrier == null || carrier.trim().isEmpty()) carrier = "SIM";
            String type = "";
            int level = -1;
            if (tm != null) {
                try { type = mobileNetworkType(tm.getDataNetworkType()); }
                catch (SecurityException ignored) {}
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    try {
                        SignalStrength strength = tm.getSignalStrength();
                        if (strength != null) level = strength.getLevel();
                    } catch (SecurityException ignored) {}
                }
            }
            String lower = signalBars(level);
            if (!type.isEmpty()) lower += "  " + type;
            return new NetworkStatus(carrier.trim(), lower.trim());
        } catch (RuntimeException ignored) { return new NetworkStatus("OFFLINE", ""); }
    }

    private static String signalBars(int level) {
        if (level < 0) return "▁ ▁ ▁ ▁";
        int count = Math.max(0, Math.min(4, level));
        switch (count) {
            case 0: return "▁ ▁ ▁ ▁";
            case 1: return "▂ ▁ ▁ ▁";
            case 2: return "▂ ▄ ▁ ▁";
            case 3: return "▂ ▄ ▆ ▁";
            default: return "▂ ▄ ▆ █";
        }
    }

    private static String mobileNetworkType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "H+";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS: return "2G";
            default: return "";
        }
    }

    private TextView dabButton(String symbol, String description, int command) {
        TextView button;
        if (command == DabControls.PREVIOUS) {
            button = new DabSkipButtonView(this, DabSkipButtonView.PREVIOUS);
        } else if (command == DabControls.NEXT) {
            button = new DabSkipButtonView(this, DabSkipButtonView.NEXT);
        } else {
            button = label(symbol, 18, GlassSkin.TEXT, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setIncludeFontPadding(false);
        }
        button.setContentDescription(description);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(GlassSkin.touch(this, command == DabControls.TOGGLE));
        button.setElevation(dp(4));
        button.setOnClickListener(v -> {
            if (!DabControls.send(this, command)) {
                android.widget.Toast.makeText(this, "DABdream+ bietet diesen Befehl gerade nicht an – Diagnose prüfen", android.widget.Toast.LENGTH_SHORT).show();
            }
            dabWatcher.refreshNow();
            dabControlsStatus = dabWatcher.status();
            updateDabButtons();
        });
        return button;
    }

    private void updateDabButtons() {
        if (dabControlsStatus == null || dabPrevious == null) return;
        dabPrevious.setAlpha(dabControlsStatus.previous ? 1f : 0.45f);
        dabPlayPause.setAlpha(dabControlsStatus.toggle ? 1f : 0.45f);
        dabNext.setAlpha(dabControlsStatus.next ? 1f : 0.45f);
        setTextIfChanged(dabPlayPause, dabControlsStatus.playing ? "Ⅱ" : "▶");
        dabPlayPause.setContentDescription(dabControlsStatus.playing ? "DAB pausieren" : "DAB abspielen");
    }

    private void showDoorWarning(VehicleState s) {
        String text = "";
        if (s.doorFL && !doorFlFault) text += "Tür vorn links  ";
        if (s.doorFR) text += "Tür vorn rechts  ";
        if (s.doorRL) text += "Tür hinten links  ";
        if (s.doorRR) text += "Tür hinten rechts  ";
        if (s.trunk) text += "Kofferraum  ";
        if (text.isEmpty()) warning.setVisibility(View.GONE);
        else {
            setTextIfChanged(warning, "⚠  " + text.trim() + " offen");
            warning.setVisibility(View.VISIBLE);
        }
    }

    private void showSeatbeltWarning(VehicleState s) {
        if (seatbeltWarning == null) return;
        boolean show = seatbeltAlert.update(s, VehicleRepository.now());
        if (show) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) seatbeltWarning.getLayoutParams();
            int top = warning.getVisibility() == View.VISIBLE ? dp(58) : dp(10);
            if (lp.topMargin != top) {
                lp.topMargin = top;
                seatbeltWarning.setLayoutParams(lp);
            }
            if (seatbeltWarning.getVisibility() != View.VISIBLE) {
                seatbeltWarning.setVisibility(View.VISIBLE);
                seatbeltBlink = android.animation.ObjectAnimator.ofFloat(seatbeltWarning, View.ALPHA, 1f, 0.3f);
                seatbeltBlink.setDuration(550);
                seatbeltBlink.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                seatbeltBlink.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                seatbeltBlink.start();
            }
        } else if (seatbeltWarning.getVisibility() != View.GONE) {
            if (seatbeltBlink != null) seatbeltBlink.cancel();
            seatbeltBlink = null;
            seatbeltWarning.setAlpha(1f);
            seatbeltWarning.setVisibility(View.GONE);
        }
    }

    private void updateSeatbeltStatus(VehicleState s) {
        if (seatbeltStatus == null) return;
        long age = VehicleRepository.now() - s.seatbeltUpdatedAtMs;
        if (!s.seatbeltKnown || age < 0 || age > 15000L) {
            setTextIfChanged(seatbeltStatus, "--");
            setColorIfChanged(seatbeltStatus, GlassSkin.MUTED);
        } else if (s.seatbeltOpen) {
            setTextIfChanged(seatbeltStatus, "WARNUNG");
            setColorIfChanged(seatbeltStatus, GlassSkin.WARNING);
        } else {
            // CAN has only a shared warning bit; no individual seat states.
            setTextIfChanged(seatbeltStatus, "OK");
            setColorIfChanged(seatbeltStatus, GlassSkin.GREEN);
        }
        seatbeltStatus.setContentDescription("Gurtstatus: " + seatbeltStatus.getText());
    }

    private final AverageDisplayFilter averageDisplayFilter = new AverageDisplayFilter();

    private void updateConsumption(VehicleState s) {
        if (consumption == null) return;
        // The shown average is the gliding mean of the last 100 km (distance-weighted rolling window).
        // The 15-second filter smooths only the visible figure, never the accumulated fuel or distance.
        double average = averageDisplayFilter.update(Obd2Runtime.rangeAverage(this),
                android.os.SystemClock.elapsedRealtime());
        setTextIfChanged(consumption, ConsumptionDisplay.instantaneous(s));
        setTextIfChanged(avgConsumptionView, valid(average)
                ? String.format(Locale.GERMANY, "%.1f l", average) : "-- l");
    }

    private void updateWasherStatus(VehicleState s) {
        if (washer == null) return;
        long age = VehicleRepository.now() - s.washerUpdatedAtMs;
        if (!s.washerKnown || age < 0 || age > 15000L) {
            setTextIfChanged(washer, "--");
            setColorIfChanged(washer, GlassSkin.MUTED);
        } else if (s.washerLow) {
            setTextIfChanged(washer, "NIEDRIG");
            setColorIfChanged(washer, GlassSkin.WARNING);
        } else {
            // CAN provides a low-fluid warning bit, not the level in litres.
            setTextIfChanged(washer, "OK");
            setColorIfChanged(washer, GlassSkin.GREEN);
        }
    }

    private TextView metricValue(String text, int color, int lines) {
        TextView value = label(text, 18, color, Typeface.BOLD);
        value.setMaxLines(lines);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setAutoSizeTextTypeUniformWithConfiguration(8, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        return value;
    }

    // Each heading and value belongs to the same equally weighted column.
    // There is deliberately no column background, border or divider.

    private void addWeatherPanel(LinearLayout centerStack) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(6), dp(9), dp(6));
        card.setBackground(GlassSkin.panel(this, false));
        card.setElevation(dp(7));

        LinearLayout current = new LinearLayout(this);
        current.setOrientation(LinearLayout.VERTICAL);
        current.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        // Ortsname exakt im selben Stil und auf derselben Textzeile wie „Sonnenaufgang“.
        weatherLocationName = label("GPS", 11, GlassSkin.ACCENT, Typeface.BOLD);
        weatherLocationName.setGravity(Gravity.CENTER);
        weatherLocationName.setMaxLines(1);
        weatherLocationName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherLocationName.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        current.addView(weatherLocationName, lp(-1, dp(18), 0));

        LinearLayout currentValues = row();
        currentValues.setGravity(Gravity.CENTER);
        weatherCurrentIcon = weatherIcon(2);
        LinearLayout.LayoutParams currentIconLp = lp(dp(42), -1, 0);
        currentIconLp.rightMargin = dp(6);
        currentValues.addView(weatherCurrentIcon, currentIconLp);

        LinearLayout temperatureStack = new LinearLayout(this);
        temperatureStack.setOrientation(LinearLayout.VERTICAL);
        temperatureStack.setGravity(Gravity.CENTER_VERTICAL);
        weatherCurrentTemperature = label("-- °C", 22, GlassSkin.TEXT, Typeface.BOLD);
        weatherCurrentTemperature.setGravity(Gravity.CENTER);
        weatherCurrentTemperature.setAutoSizeTextTypeUniformWithConfiguration(13, 22, 1, TypedValue.COMPLEX_UNIT_SP);
        temperatureStack.addView(weatherCurrentTemperature, lp(-2, 0, 1f));
        weatherHighLow = label("↑ --°   ↓ --°", 9, GlassSkin.MUTED, Typeface.BOLD);
        weatherHighLow.setGravity(Gravity.CENTER);
        weatherHighLow.setMaxLines(1);
        weatherHighLow.setAutoSizeTextTypeUniformWithConfiguration(6, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        temperatureStack.addView(weatherHighLow, lp(-2, dp(14), 0));
        currentValues.addView(temperatureStack, lp(-2, -1, 0));
        current.addView(currentValues, lp(-2, 0, 1f));

        weatherCurrentDescription = label("Wetter wird geladen · ☂ -- %", 9, GlassSkin.MUTED, Typeface.NORMAL);
        weatherCurrentDescription.setGravity(Gravity.CENTER);
        weatherCurrentDescription.setMaxLines(1);
        weatherCurrentDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherCurrentDescription.setAutoSizeTextTypeUniformWithConfiguration(6, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        current.addView(weatherCurrentDescription, lp(-1, dp(16), 0));
        card.addView(current, lp(0, -1, 1.15f));

        // Aktuelles Wetter und Sonne gehören zum selben Tag, daher bewusst ohne Trennlinie.
        LinearLayout solar = new LinearLayout(this);
        solar.setOrientation(LinearLayout.VERTICAL);
        solar.setGravity(Gravity.CENTER_HORIZONTAL);
        solar.setPadding(dp(4), 0, dp(4), 0);

        // Sonnenaufgang beginnt auf derselben oberen Linie wie der Ortsname.
        // Sonnenuntergang endet unten auf Höhe der aktuellen Wetterbeschreibung.
        TextView riseLabel = label("Sonnenaufgang", 11, GlassSkin.ACCENT, Typeface.BOLD);
        riseLabel.setGravity(Gravity.CENTER);
        riseLabel.setMaxLines(1);
        riseLabel.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        solar.addView(riseLabel, lp(-1, dp(18), 0));
        weatherSunrise = label("--:-- Uhr", 13, GlassSkin.TEXT, Typeface.BOLD);
        weatherSunrise.setGravity(Gravity.CENTER);
        weatherSunrise.setMaxLines(1);
        weatherSunrise.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        solar.addView(weatherSunrise, lp(-1, dp(20), 0));

        View sunSpacer = new View(this);
        solar.addView(sunSpacer, lp(-1, 0, 1f));

        TextView setLabel = label("Sonnenuntergang", 11, GlassSkin.ACCENT, Typeface.BOLD);
        setLabel.setGravity(Gravity.CENTER);
        setLabel.setMaxLines(1);
        setLabel.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        solar.addView(setLabel, lp(-1, dp(18), 0));
        weatherSunset = label("--:-- Uhr", 13, GlassSkin.TEXT, Typeface.BOLD);
        weatherSunset.setGravity(Gravity.CENTER);
        weatherSunset.setMaxLines(1);
        weatherSunset.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        solar.addView(weatherSunset, lp(-1, dp(20), 0));
        card.addView(solar, lp(0, -1, 1.08f));

        addWeatherDivider(card);
        addWeatherForecastColumn(card, 0);
        addWeatherDivider(card);
        addWeatherForecastColumn(card, 1);
        addWeatherDivider(card);
        addWeatherForecastColumn(card, 2);

        LinearLayout.LayoutParams weatherCardLp = lp(-1, dp(94), 0);
        weatherCardLp.topMargin = dp(6);
        centerStack.addView(card, weatherCardLp);

        displayedWeather = WeatherStore.load(this);
        if (displayedWeather != null) updateWeatherPanel(displayedWeather);
        displayedLocality = LocalityStore.load(this);
        updateLocalityName(displayedLocality);
    }

    private void addWeatherForecastColumn(LinearLayout card, int index) {
        LinearLayout day = new LinearLayout(this);
        day.setOrientation(LinearLayout.VERTICAL);
        day.setGravity(Gravity.CENTER);

        weatherDayName[index] = label("--", 9, GlassSkin.ACCENT, Typeface.BOLD);
        weatherDayName[index].setGravity(Gravity.CENTER);
        weatherDayName[index].setMaxLines(1);
        weatherDayName[index].setAutoSizeTextTypeUniformWithConfiguration(7, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        day.addView(weatherDayName[index], lp(-1, dp(16), 0));

        weatherDayIcon[index] = weatherIcon(3);
        LinearLayout.LayoutParams iconLp = lp(dp(28), 0, 1f);
        iconLp.topMargin = dp(1);
        iconLp.bottomMargin = dp(1);
        day.addView(weatherDayIcon[index], iconLp);

        weatherDayDescription[index] = label("Wetter", 7, GlassSkin.MUTED, Typeface.NORMAL);
        weatherDayDescription[index].setGravity(Gravity.CENTER);
        weatherDayDescription[index].setMaxLines(1);
        weatherDayDescription[index].setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherDayDescription[index].setAutoSizeTextTypeUniformWithConfiguration(5, 7, 1, TypedValue.COMPLEX_UNIT_SP);
        day.addView(weatherDayDescription[index], lp(-1, dp(12), 0));

        weatherDayTemperature[index] = label("↑ --°  ↓ --°", 10, GlassSkin.TEXT, Typeface.BOLD);
        weatherDayTemperature[index].setGravity(Gravity.CENTER);
        weatherDayTemperature[index].setMaxLines(1);
        weatherDayTemperature[index].setAutoSizeTextTypeUniformWithConfiguration(7, 10, 1, TypedValue.COMPLEX_UNIT_SP);
        day.addView(weatherDayTemperature[index], lp(-1, dp(17), 0));
        card.addView(day, lp(0, -1, .68f));
    }

    private void addWeatherDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setBackgroundColor(0x887fc9ff);
        LinearLayout.LayoutParams dividerLp = lp(dp(1), -1, 0);
        dividerLp.setMargins(dp(5), dp(8), dp(5), dp(8));
        card.addView(divider, dividerLp);
    }

    private void updateLocalityName(Locality locality) {
        if (weatherLocationName == null) return;
        setTextIfChanged(weatherLocationName, locality != null && locality.usable()
                ? locality.name.toUpperCase(Locale.GERMAN) : "GPS");
        weatherLocationName.setContentDescription(locality != null && locality.usable()
                ? "Aktuelles Wetter für " + locality.name : "Aktuelles Wetter am GPS-Standort");
    }

    private void updateWeatherPanel(WeatherForecast forecast) {
        if (forecast == null || !forecast.usable() || weatherCurrentIcon == null) return;
        weatherCurrentIcon.setImageResource(
                weatherDrawable(WeatherForecast.iconKind(forecast.currentCode, forecast.daylight)));
        setTextIfChanged(weatherCurrentTemperature,
                String.format(Locale.GERMANY, "%.0f °C", forecast.currentC));
        if (forecast.hasTodayDetails()) {
            setTextIfChanged(weatherHighLow, String.format(Locale.GERMANY,
                    "↑ %.0f°   ↓ %.0f°", forecast.todayMaximumC, forecast.todayMinimumC));
        } else setTextIfChanged(weatherHighLow, "↑ --°   ↓ --°");

        // Die Einträge sind morgen, übermorgen und der dritte Folgetag
        // (WeatherClient überspringt bewusst Index 0 = heute).
        for (int i = 0; i < 3 && i < forecast.days.length; i++) {
            WeatherForecast.Day day = forecast.days[i];
            if (weatherDayName[i] == null || weatherDayIcon[i] == null) continue;
            setTextIfChanged(weatherDayName[i], WeatherForecast.dayLabel(day.date));
            weatherDayIcon[i].setImageResource(weatherDrawable(WeatherForecast.iconKind(day.weatherCode, true)));
            setTextIfChanged(weatherDayDescription[i], WeatherForecast.description(day.weatherCode));
            setTextIfChanged(weatherDayTemperature[i], String.format(Locale.GERMANY,
                    "↑ %.0f°  ↓ %.0f°", day.maximumC, day.minimumC));
            weatherDayIcon[i].setContentDescription("Wetter " + WeatherForecast.dayLabel(day.date)
                    + ": " + WeatherForecast.description(day.weatherCode));
        }

        setTextIfChanged(weatherSunrise, (forecast.sunrise.isEmpty() ? "--:--" : forecast.sunrise) + " Uhr");
        setTextIfChanged(weatherSunset, (forecast.sunset.isEmpty() ? "--:--" : forecast.sunset) + " Uhr");
        updateWeatherDescription();
        weatherCurrentIcon.setContentDescription("Aktuelles Wetter: "
                + WeatherForecast.description(forecast.currentCode));
    }

    /** Shows current condition and today's rain chance without a duplicate refresh clock. */
    private void updateWeatherDescription() {
        if (weatherCurrentDescription == null || displayedWeather == null) return;
        String rain = displayedWeather.todayPrecipitationProbability >= 0
                ? "☂ " + displayedWeather.todayPrecipitationProbability + " %"
                : "☂ -- %";
        setTextIfChanged(weatherCurrentDescription,
                WeatherForecast.description(displayedWeather.currentCode) + "  ·  " + rain);
        setColorIfChanged(weatherCurrentDescription, GlassSkin.MUTED);
        if (!weatherStatusNote.isEmpty()) {
            weatherCurrentDescription.setContentDescription("Wetterdaten: " + weatherStatusNote);
        } else {
            weatherCurrentDescription.setContentDescription("Aktuelles Wetter und Niederschlagswahrscheinlichkeit");
        }
    }

    /** Which drawable resource renders a given weather pictogram kind, see {@link #weatherIcon}. */
    private static int weatherDrawable(WeatherForecast.IconKind kind) {
        switch (kind) {
            case SUN: return R.drawable.ic_weather_sun;
            case MOON: return R.drawable.ic_weather_moon;
            case SUN_CLOUD: return R.drawable.ic_weather_partly_cloudy_day;
            case MOON_CLOUD: return R.drawable.ic_weather_partly_cloudy_night;
            case CLOUD: return R.drawable.ic_weather_cloud;
            case FOG: return R.drawable.ic_weather_fog;
            case RAIN: return R.drawable.ic_weather_rain;
            case SNOW: return R.drawable.ic_weather_snow;
            case THUNDER: return R.drawable.ic_weather_thunder;
            default: return R.drawable.ic_weather_unknown;
        }
    }

    private void maybeRefreshWeather() {
        if (!activityVisible || weatherRequestInFlight) return;
        if (!mapConnectionOnline) {
            weatherStatusNote = "OFFLINE";
            if (displayedWeather != null) updateWeatherDescription();
            else if (weatherCurrentDescription != null)
                setTextIfChanged(weatherCurrentDescription, "Wartet auf Internet");
            return;
        }
        if ("OFFLINE".equals(weatherStatusNote)) {
            weatherStatusNote = "";
            updateWeatherDescription();
        }
        Location location = currentWeatherLocation();
        if (location == null) {
            if (displayedWeather == null && weatherCurrentDescription != null)
                setTextIfChanged(weatherCurrentDescription, "Wartet auf GPS");
            return;
        }
        long wallNow = WeatherStore.wallTimeMs();
        long elapsedNow = android.os.SystemClock.elapsedRealtime();
        long age = displayedWeather == null ? Long.MAX_VALUE : wallNow - displayedWeather.fetchedAtMs;
        boolean forcedByNewLocality = weatherRefreshForNewLocality;
        boolean refreshDue = forcedByNewLocality || displayedWeather == null
                || age < 0L || age >= WEATHER_REFRESH_MS;
        if (!refreshDue || (!forcedByNewLocality && lastWeatherAttemptElapsed > 0L
                && elapsedNow - lastWeatherAttemptElapsed < WEATHER_RETRY_MS)) return;
        weatherRefreshForNewLocality = false;
        lastWeatherAttemptElapsed = elapsedNow;
        weatherRequestInFlight = true;
        if (displayedWeather == null && weatherCurrentDescription != null)
            setTextIfChanged(weatherCurrentDescription, "Wetter wird geladen");
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        try {
            weatherLoader.execute(() -> {
                WeatherForecast forecast = null;
                String failure = null;
                try {
                    forecast = new WeatherClient().fetch(latitude, longitude);
                    WeatherStore.save(getApplicationContext(), forecast);
                } catch (Exception error) {
                    failure = error.getClass().getSimpleName() + ": " + error.getMessage();
                }
                final WeatherForecast loaded = forecast;
                final String errorText = failure;
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    weatherRequestInFlight = false;
                    if (loaded != null) {
                        weatherStatusNote = "";
                        displayedWeather = loaded;
                        if (!isFinishing() && !isDestroyed()) updateWeatherPanel(loaded);
                        DiagLog.add("Wetter: GPS-Vorhersage aktualisiert");
                    } else if (errorText != null) {
                        weatherStatusNote = "FEHLER";
                        if (displayedWeather != null) updateWeatherDescription();
                        else if (weatherCurrentDescription != null)
                            setTextIfChanged(weatherCurrentDescription, "Wetter nicht verfügbar");
                        DiagLog.add("Wetter: Abruf fehlgeschlagen (" + errorText + ")");
                    }
                });
            });
        } catch (RejectedExecutionException stopped) { weatherRequestInFlight = false; }
    }

    private void maybeRefreshLocality() {
        if (!activityVisible || !mapConnectionOnline || localityRequestInFlight) return;
        Location location = currentWeatherLocation();
        if (location == null) return;
        boolean movedEnough = displayedLocality == null || distanceMeters(location.getLatitude(),
                location.getLongitude(), displayedLocality.latitude, displayedLocality.longitude)
                >= LOCALITY_LOOKUP_DISTANCE_METERS;
        if (!movedEnough) return;
        long elapsedNow = android.os.SystemClock.elapsedRealtime();
        if (lastLocalityAttemptElapsed > 0L
                && elapsedNow - lastLocalityAttemptElapsed < LOCALITY_LOOKUP_MIN_INTERVAL_MS) return;
        lastLocalityAttemptElapsed = elapsedNow;
        localityRequestInFlight = true;
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        try {
            weatherLoader.execute(() -> {
                Locality locality = null;
                String failure = null;
                try {
                    locality = new LocalityClient().fetch(latitude, longitude);
                } catch (Exception error) {
                    failure = error.getClass().getSimpleName() + ": " + error.getMessage();
                }
                final Locality loaded = locality;
                final String errorText = failure;
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    localityRequestInFlight = false;
                    if (loaded != null) {
                        applyLocalityResult(loaded);
                    } else if (errorText != null) {
                        DiagLog.add("Wetter-Ort: Abfrage fehlgeschlagen (" + errorText + ")");
                    }
                });
            });
        } catch (RejectedExecutionException stopped) { localityRequestInFlight = false; }
    }

    /**
     * Übernimmt ein Nominatim-Ergebnis nur, wenn es entweder mit dem aktuell angezeigten Ort
     * übereinstimmt (dann wird nur der Anker-Punkt/Zeitstempel aufgefrischt) oder wenn derselbe
     * abweichende Name zweimal hintereinander gemeldet wurde (dann gilt der Wechsel als
     * bestätigt). Ein einzelner abweichender Treffer merkt sich nur den Kandidaten, ohne
     * displayedLocality (den Anker für die 250-m-Schwelle) zu verändern – dadurch wird nahe
     * einer Ortsgrenze weiterhin regelmäßig neu geprüft, bis sich das Ergebnis bestätigt.
     */
    private void applyLocalityResult(Locality loaded) {
        if (displayedLocality == null || Locality.sameName(displayedLocality, loaded)) {
            boolean changed = displayedLocality == null;
            displayedLocality = loaded;
            pendingLocality = null;
            LocalityStore.save(getApplicationContext(), loaded);
            if (!isFinishing() && !isDestroyed()) updateLocalityName(loaded);
            DiagLog.add("Wetter-Ort: " + loaded.name + (changed ? " (neu)" : " (bestätigt)"));
            if (changed) {
                weatherRefreshForNewLocality = true;
                maybeRefreshWeather();
            }
            return;
        }
        if (pendingLocality != null && Locality.sameName(pendingLocality, loaded)) {
            DiagLog.add("Wetter-Ort: " + loaded.name + " (bestätigt nach 2. Abfrage, vorher "
                    + displayedLocality.name + ")");
            displayedLocality = loaded;
            pendingLocality = null;
            LocalityStore.save(getApplicationContext(), loaded);
            if (!isFinishing() && !isDestroyed()) updateLocalityName(loaded);
            weatherRefreshForNewLocality = true;
            maybeRefreshWeather();
            return;
        }
        pendingLocality = loaded;
        DiagLog.add("Wetter-Ort: " + loaded.name + " abweichend von " + displayedLocality.name
                + " – warte auf Bestätigung");
    }

    private Location currentWeatherLocation() {
        Location map = germanyMap != null ? germanyMap.currentLocation() : null;
        Location fallback = fallbackGps == null ? null : fallbackGps.currentLocation();
        Location newest = newerLocation(map, fallback);
        if (newest == null) return null;
        long age = newest.getTime() <= 0L ? 0L : WeatherStore.wallTimeMs() - newest.getTime();
        return age >= -1_000L && age <= 5L * 60L * 1_000L ? newest : null;
    }

    private static Location newerLocation(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        long firstElapsed = first.getElapsedRealtimeNanos();
        long secondElapsed = second.getElapsedRealtimeNanos();
        if (firstElapsed > 0L && secondElapsed > 0L) return secondElapsed > firstElapsed ? second : first;
        return second.getTime() > first.getTime() ? second : first;
    }

    private static float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView label(String text, float size, int color, int style) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.create("sans-serif", style)); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    /**
     * A weather pictogram (vector drawable) that scales itself to whatever box it ends up
     * in - FIT_CENTER keeps its aspect ratio and shrinks/grows the image, never the
     * surrounding text, and never clips. That replaces the old emoji-glyph TextViews, whose
     * fixed font size didn't always fit the weather panel's height once that height became
     * variable - the sun symbol in particular got cut
     * off at the bottom on some screens. The actual drawable is set later via setImageResource
     * in updateWeatherPanel()/updateLocalityName(), once real weather data is known.
     */
    private ImageView weatherIcon(int paddingDp) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setAdjustViewBounds(true);
        v.setCropToPadding(false);
        // Multi-colour vector drawables must keep their own natural weather colours;
        // a global tint would turn sun, clouds and precipitation into one flat colour.
        v.setImageTintList(null);
        v.clearColorFilter();
        if (paddingDp > 0) v.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return v;
    }

    private void setMetricState(TextView view, String text, boolean fresh, boolean hadData) {
        setTextIfChanged(view, hadData ? text : text.replace(" ALT", ""));
        setColorIfChanged(view, fresh ? GlassSkin.TEXT
                : hadData ? GlassSkin.WARNING : GlassSkin.MUTED);
        view.setContentDescription((fresh ? "Aktuell: " : hadData ? "Veraltet: " : "Keine Daten: ")
                + view.getText());
    }
    private LinearLayout.LayoutParams lp(int width, int height, float weight) { return new LinearLayout.LayoutParams(width, height, weight); }
    private GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (strokeWidth > 0) d.setStroke(strokeWidth, stroke); return d;
    }
    private void applyMapNightMode() {
        Location location = latestMapLocation();
        boolean night = resolveMapNightMode(location);
        boolean changed = appliedMapNightMode == null || appliedMapNightMode != night;
        appliedMapNightMode = night;
        if (germanyMap != null) germanyMap.setNightMode(night);
        if (changed) {
            String configured = MapPreferences.colorMode(this);
            String source = MapPreferences.COLOR_AUTO.equals(configured)
                    ? (location == null ? "Sachsen-Fallback" : "GPS") : "Vorgabe";
            DiagLog.add("Kartenmodus: " + (night ? "Nacht" : "Tag") + " (" + source + ")");
        }
    }

    private boolean resolveMapNightMode(Location location) {
        String mode = MapPreferences.colorMode(this);
        if (MapPreferences.COLOR_DAY.equals(mode)) return false;
        if (MapPreferences.COLOR_NIGHT.equals(mode)) return true;
        // Until the first GPS fix, a point in Saxony gives a sensible startup result.
        double latitude = location == null ? 51.2d : location.getLatitude();
        double longitude = location == null ? 13.6d : location.getLongitude();
        return SolarDaylight.isNight(new Date().getTime(), latitude, longitude);
    }

    private Location latestMapLocation() {
        Location location = germanyMap != null ? germanyMap.currentLocation() : null;
        Location fallback = fallbackGps == null ? null : fallbackGps.currentLocation();
        if (newer(fallback, location)) location = fallback;
        if (location != null) return location;
        if (!LocationAccess.any(this)) return null;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) return null;
        Location gps = LocationAccess.lastKnownGps(this, manager);
        Location network = LocationAccess.lastKnownNetwork(this, manager);
        return newer(gps, network) ? gps : network;
    }

    private static boolean newer(Location candidate, Location current) {
        if (candidate == null) return false;
        if (current == null) return true;
        long candidateElapsed = candidate.getElapsedRealtimeNanos();
        long currentElapsed = current.getElapsedRealtimeNanos();
        if (candidateElapsed > 0L && currentElapsed > 0L) return candidateElapsed > currentElapsed;
        return candidate.getTime() > current.getTime();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static boolean valid(double d) { return !Double.isNaN(d) && !Double.isInfinite(d); }
    private static void setTextIfChanged(TextView view, CharSequence value) {
        if (!android.text.TextUtils.equals(view.getText(), value)) view.setText(value);
    }
    private static void setColorIfChanged(TextView view, int color) {
        if (view.getCurrentTextColor() != color) view.setTextColor(color);
    }
    private void hideSystemUi() {
        View d = getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = d.getWindowInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // Foreground fallback for head units that suppress automatic background service recreation.
        GermanyOfflineStore.autoResumePending(this);
        String currentBackgroundSignature = BackgroundPreferences.signature(this);
        if (!loadedBackgroundSignature.equals(currentBackgroundSignature) && !isFinishing()) {
            GlassBlurCache.invalidate();
            DiagLog.add("Hauptseite neu: Hintergrund geändert");
            recreate();
            return;
        }
        hideSystemUi();
        if (wakeMapRebuildPending) {
            handler.removeCallbacks(rebuildMapAfterWake);
            handler.postDelayed(rebuildMapAfterWake, 2_000L);
        }
        doorFlFault = VehiclePreferences.doorFlFault(this);
        // Full Germany validation already happened when this Activity created its renderer.
        // onResume must stay cheap so returning from another app cannot stall the map UI.
        String germanySignature = GermanyOfflineStore.signature(this);
        if (!loadedGermanySignature.equals(germanySignature) && !isFinishing()) {
            DiagLog.add("Hauptseite neu: Deutschland-Offlinepaket geändert"); recreate(); return;
        }
        mapActivityResumed = true;
        applyMapNightMode();
        updateMapConnection();
        if (germanyMap != null) germanyMap.applySavedZoom();
        if (germanyMap != null) germanyMap.applyHeadingMode();
        if (germanyMap != null) germanyMap.applyViewSettings();
        handler.removeCallbacks(startDabAutomatically);
        handler.postDelayed(startDabAutomatically, 800L);
        mapUpdateResumedAt = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(mapUpdateWatch);
        handler.postDelayed(mapUpdateWatch, MapUpdateSchedule.SETTLE_MS);
    }
    @Override protected void onPause() {
        stopMapUpdateWatch();
        handler.removeCallbacks(startDabAutomatically);
        mapActivityResumed = false;
        if (germanyMap != null) germanyMap.pause();
        super.onPause();
    }

    @Override protected void onStart() {
        super.onStart();
        MapFollowDiagnostics.event("Hauptseite onStart");
        mapActivityStarted = true;
        activityVisible = true;
        RangeTrackingService.ensureStarted(this);
        DabNotificationListener.requestReconnect(this);
        dabWatcher.start();
        if (!runtimeActive) {
            VendorRuntime.acquire(getApplicationContext());
            runtimeActive = true;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        handler.removeCallbacks(refreshMapTheme);
        handler.post(refreshMapTheme);
        handler.removeCallbacks(refreshNetworkState);
        handler.post(refreshNetworkState);
        if (appCatalogDirty) scheduleAppRefresh();
        if (germanyMap != null) germanyMap.start();
        // GPS is stopped in onStop(). Re-attach the camera consumer and restart it right here
        // instead of waiting for the next periodic map-connection pass.
        if (fallbackGps != null && germanyMap != null) {
            fallbackGps.setRawConsumer(germanyMap::acceptAnalysisLocation);
            fallbackGps.setConsumer(this::acceptGermanyCameraPosition);
            fallbackGps.start();
        }
    }
    @Override protected void onStop() {
        MapFollowDiagnostics.event("Hauptseite onStop");
        mapActivityStarted = false;
        activityVisible = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(refreshMapTheme);
        handler.removeCallbacks(refreshNetworkState);
        handler.removeCallbacks(refreshAppPages);
        dabWatcher.stop();
        if (runtimeActive) {
            VendorRuntime.release(getApplicationContext());
            runtimeActive = false;
        }
        if (germanyMap != null) germanyMap.stop();
        if (fallbackGps != null) fallbackGps.stop();
        super.onStop();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        if (germanyMap != null) germanyMap.lowMemory();
    }
    @Override protected void onDestroy() {
        stopMapUpdateWatch();
        dabReturnGeneration++;
        handler.removeCallbacks(dabReturnCheck);
        dabReturnExecutor.shutdownNow();
        if (seatbeltBlink != null) seatbeltBlink.cancel();
        MapFollowDiagnostics.event("Hauptseite onDestroy");
        iconLoader.shutdownNow();
        weatherLoader.shutdownNow();
        if (dabWatcher != null) dabWatcher.stop();
        if (dabScreenReceiverRegistered) {
            unregisterReceiver(dabScreenReceiver);
            dabScreenReceiverRegistered = false;
        }
        if (germanyMap != null) {
            germanyMap.setLocationConsumer(null);
            germanyMap.setSpeedLimitConsumer(null);
            germanyMap.setSpeedCameraConsumer(null);
        }
        if (fallbackGps != null) fallbackGps.setConsumer(null);
        if (fallbackGps != null) fallbackGps.setRawConsumer(null);
        if (germanyRoadMatcher != null) germanyRoadMatcher.close();
        if (germanyMap != null) germanyMap.destroy();
        if (fallbackGps != null) fallbackGps.destroy();
        handler.removeCallbacksAndMessages(null);
        if (appCallbackRegistered && launcherApps != null) {
            try { launcherApps.unregisterCallback(appChanges); } catch (Throwable ignored) {}
        }
        appCallbackRegistered = false;
        if (runtimeActive) {
            VendorRuntime.release(getApplicationContext());
            runtimeActive = false;
        }
        super.onDestroy();
    }
}
