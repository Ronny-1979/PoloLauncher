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
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
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
    private int ACCENT;
    private int MUTED;
    private int loadedSkin;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final DabStartupGate dabStartupGate = new DabStartupGate();
    private boolean dabWaitingLogged;
    private boolean dabScreenReceiverRegistered;
    private static volatile boolean dabScreenOff;
    static void dabScreenChanged(boolean off) {
        dabScreenOff = off;
        if (off) dabStartupGate.screenOff();
    }
    private final BroadcastReceiver dabScreenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                dabScreenChanged(true);
                dabWaitingLogged = false;
                handler.removeCallbacks(startDabAutomatically);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                dabScreenChanged(false);
                if (activityVisible) {
                    handler.removeCallbacks(startDabAutomatically);
                    handler.postDelayed(startDabAutomatically, 800L);
                }
            }
        }
    };
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
            DiagLog.add("DAB-Autostart: geöffnet, Rückkehr in 3 Sekunden");
            handler.postDelayed(this::returnAfterDabStartup, 3000L);
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
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd. MMMM", Locale.GERMANY);

    private TextView clock;
    private TextView date;
    private TextView fuel;
    private TextView estimatedRange;
    private TextView voltage;
    private TextView outside;
    private TextView washer;
    private TextView consumption;
    private TextView avgConsumptionView;
    private TextView dabTitle;
    private TextView dabSubtitle;
    private ImageView dabArtwork;
    private Bitmap lastDabArtwork;
    private TextView dabPrevious;
    private TextView dabPlayPause;
    private TextView dabNext;
    private TextView seatbeltStatus;
    private TextView warning;
    private PagedLauncherLayout pager;
    private LauncherApps launcherApps;
    private boolean appCallbackRegistered;
    private boolean activityVisible;
    private boolean appCatalogDirty;
    private String appCatalogSignature = "";
    private boolean runtimeActive;
    private OfflineMapPanel offlineMap;
    private FallbackLocationFeed fallbackGps;
    private OnlineVectorMapPanel vectorMap;
    private TextView mapMode;
    private long mapCreditShownAt;
    private String mapCreditText = "";
    private String loadedMapSignature = "";
    private String loadedVectorStyle;
    private FrameLayout mapSurface;
    private boolean mapActivityStarted;
    private boolean mapActivityResumed;
    private boolean mapConnectionOnline;
    private long lastMapNetworkMs;
    private long lastVectorFailureMs;
    private int vectorFailureCount;
    private static final long VECTOR_RETRY_MIN_MS = 30_000L;
    private static final long VECTOR_RETRY_MAX_MS = 300_000L;
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
        super.onCreate(state);
        IntentFilter dabScreenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        dabScreenFilter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                registerReceiver(dabScreenReceiver, dabScreenFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(dabScreenReceiver, dabScreenFilter);
            dabScreenReceiverRegistered = true;
        } catch (RuntimeException error) { DiagLog.add("DAB-Standby-Empfänger: " + error); }
        DiagLog.add("Hauptseite Activity neu erstellt");
        CopperSkin.apply(this);
        loadedSkin = CopperSkin.selection(this);
        ACCENT = CopperSkin.GOLD;
        MUTED = CopperSkin.MUTED;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        MediaInfo.restore(this);
        dabWatcher = new DabSessionWatcher(this);
        doorFlFault = VehiclePreferences.doorFlFault(this);
        buildUi(state);
        finishMapDownloadInBackground();
        registerAppChangeListener();
    }

    private void buildUi(Bundle state) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(7), dp(12), dp(8));
        root.setBackground(CopperSkin.background());

        LinearLayout content = row();
        content.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = columnPanel();
        LinearLayout timeCard = new LinearLayout(this);
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setGravity(Gravity.CENTER_VERTICAL);
        timeCard.setPadding(dp(12), dp(6), dp(12), dp(5));
        timeCard.setBackground(CopperSkin.panel(this, true));
        timeCard.setElevation(dp(5));
        clock = label("--:--", 45, CopperSkin.CREAM, Typeface.BOLD);
        date = label("", 12, MUTED, Typeface.NORMAL);
        clock.setGravity(Gravity.CENTER);
        date.setGravity(Gravity.CENTER);
        // Fit long German weekday/month names in the existing date row without wrapping.
        date.setMaxLines(1);
        date.setHorizontallyScrolling(false);
        date.setAutoSizeTextTypeUniformWithConfiguration(6, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        timeCard.addView(clock, lp(-1, 0, 1f));
        timeCard.addView(date, lp(-1, dp(24), 0));
        LinearLayout.LayoutParams timeLp = lp(-1, 0, 1.4f);
        timeLp.setMargins(0, 0, 0, dp(5));
        left.addView(timeCard, timeLp);
        addTankMetric(left);
        addConsumptionMetric(left);
        addMetric(left, "BORDNETZ", "-- V", v -> voltage = v);
        addMetric(left, "AUSSEN", "-- °C", v -> outside = v);
        content.addView(left, lp(0, -1, 0.24f));

        FrameLayout mapFrame = new FrameLayout(this);
        mapFrame.setBackground(CopperSkin.panel(this, true));
        // Clip the rendered map, GPS button and warnings to the same rounded
        // outline as the copper card, including all four corners.
        mapFrame.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        mapFrame.setClipToOutline(true);
        mapFrame.setElevation(dp(5));
        LinearLayout.LayoutParams mapLp = lp(0, -1, 0.52f);
        mapLp.setMargins(dp(12), 0, dp(12), 0);
        content.addView(mapFrame, mapLp);

        mapSurface = new FrameLayout(this);
        mapFrame.addView(mapSurface, new FrameLayout.LayoutParams(-1, -1));
        java.io.File localMap = OfflineMapStore.selectedFile(this);
        loadedMapSignature = OfflineMapStore.selectedSignature(this);
        loadedVectorStyle = MapPreferences.vectorStyle(this);
        try { offlineMap = new OfflineMapPanel(this, mapSurface, localMap); }
        catch (Throwable t) {
            DiagLog.add("Offline-Kartendatei fehlerhaft: " + t.getClass().getSimpleName() + " " + t.getMessage());
            // A broken downloaded file must not also disable the online map and GPS feed.
            try { offlineMap = new OfflineMapPanel(this, mapSurface, null); }
            catch (Throwable fallback) {
                DiagLog.add("Karten-Grundrenderer nicht verfügbar: " + fallback.getClass().getSimpleName());
            }
        }
        // A lightweight GPS source takes over whenever the online renderer is active.
        // This lets the full Mapsforge renderer sleep instead of running two map engines.
        try { fallbackGps = new FallbackLocationFeed(this); }
        catch (Throwable t) { DiagLog.add("GPS-Ersatzquelle nicht verfügbar: " + t.getClass().getSimpleName()); }
        if (offlineMap == null) {
            LinearLayout noMap = new LinearLayout(this);
            noMap.setOrientation(LinearLayout.VERTICAL);
            noMap.setGravity(Gravity.CENTER);
            noMap.setPadding(dp(20), dp(20), dp(20), dp(20));
            noMap.setBackground(CopperSkin.panel(this, true));
            TextView pin = label("⌖", 56, ACCENT, Typeface.NORMAL);
            pin.setGravity(Gravity.CENTER);
            TextView title = label("KARTE", 22, CopperSkin.CREAM, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            TextView hint = label("Karte konnte nicht geöffnet werden\nBitte Diagnose prüfen", 14, MUTED, Typeface.NORMAL);
            hint.setGravity(Gravity.CENTER);
            noMap.addView(pin);
            noMap.addView(title);
            noMap.addView(hint);
            noMap.setOnClickListener(v -> startActivity(new Intent(this, OfflineMapsActivity.class)));
            mapSurface.addView(noMap, new FrameLayout.LayoutParams(-1, -1));
        }

        View mapBorder = new View(this);
        mapBorder.setBackground(rounded(Color.TRANSPARENT, dp(16),
                loadedSkin == 0 ? Color.rgb(185, 132, 78) : CopperSkin.GOLD, dp(2)));
        mapFrame.addView(mapBorder, new FrameLayout.LayoutParams(-1, -1));

        warning = label("", 14, Color.WHITE, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setVisibility(View.GONE);
        warning.setBackground(rounded(Color.argb(235, 190, 42, 42), dp(12), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams warnLp = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        warnLp.setMargins(dp(10), dp(10), dp(10), 0);
        mapFrame.addView(warning, warnLp);

        // Offline map settings remain available through the settings menu.

        mapMode = label("", 10, CopperSkin.CREAM, Typeface.BOLD);
        mapMode.setBackground(rounded(Color.argb(215, 49, 36, 28), dp(7), Color.TRANSPARENT, 0));
        mapMode.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))); }
            catch (Exception e) { DiagLog.add("OSM-Lizenzseite: " + e.getClass().getSimpleName()); }
        });
        FrameLayout.LayoutParams modeLp = new FrameLayout.LayoutParams(-2, dp(25), Gravity.BOTTOM | Gravity.LEFT);
        // Mapsforge draws its own distance scale at the bottom-left; leave it visible.
        modeLp.setMargins(dp(12), 0, 0, dp(43));
        mapFrame.addView(mapMode, modeLp);
        TextView mapInfo = label("ⓘ", 20, CopperSkin.CREAM, Typeface.BOLD);
        mapInfo.setGravity(Gravity.CENTER);
        mapInfo.setContentDescription("Kartenquellen und Lizenzen");
        mapInfo.setBackground(rounded(Color.argb(215, 49, 36, 28), dp(10), Color.TRANSPARENT, 0));
        mapInfo.setOnClickListener(v -> showMapCredits());
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.LEFT);
        infoLp.setMargins(dp(6), 0, 0, dp(6));
        mapFrame.addView(mapInfo, infoLp);
        updateMapConnection();

        LinearLayout rightStack = new LinearLayout(this);
        rightStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = columnPanel();
        dabArtwork = new ImageView(this);
        dabArtwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Larger logo with a little extra breathing room above it.
        // Reserve this area even without artwork so controls never jump.
        dabArtwork.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams artworkLp = lp(-1, dp(128), 0);
        artworkLp.topMargin = dp(6);
        right.addView(dabArtwork, artworkLp);
        dabTitle = label("DABdream+", 22, CopperSkin.CREAM, Typeface.BOLD);
        dabTitle.setGravity(Gravity.CENTER);
        dabTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        dabTitle.setMaxLines(4);
        dabTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dabTitle.setAutoSizeTextTypeUniformWithConfiguration(8, 22, 1, TypedValue.COMPLEX_UNIT_SP);
        dabSubtitle = label("Interpret nicht verfügbar", 16, MUTED, Typeface.NORMAL);
        dabSubtitle.setGravity(Gravity.CENTER);
        dabSubtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        dabSubtitle.setMaxLines(2);
        dabSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dabSubtitle.setAutoSizeTextTypeUniformWithConfiguration(8, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout dabMetadata = new LinearLayout(this);
        dabMetadata.setOrientation(LinearLayout.VERTICAL);
        dabMetadata.setGravity(Gravity.CENTER_HORIZONTAL);
        dabMetadata.setPadding(0, dp(4), 0, 0);
        // Give both fields a measured height so Android can shrink the font
        // before the interpreter is clipped by the playback controls.
        dabMetadata.addView(dabTitle, lp(-1, 0, 3f));
        LinearLayout.LayoutParams subtitleLp = lp(-1, 0, 2f);
        subtitleLp.topMargin = dp(2);
        dabMetadata.addView(dabSubtitle, subtitleLp);
        right.addView(dabMetadata, lp(-1, 0, 1f));
        LinearLayout dabButtons = row();
        dabButtons.setPadding(0, dp(3), 0, dp(3));
        dabPrevious = dabButton("◀◀", "Vorheriger DAB-Sender", DabControls.PREVIOUS);
        dabPlayPause = dabButton("▶", "DAB abspielen oder pausieren", DabControls.TOGGLE);
        dabNext = dabButton("▶▶", "Nächster DAB-Sender", DabControls.NEXT);
        LinearLayout.LayoutParams previousParams = lp(0, -1, 1f);
        previousParams.rightMargin = dp(4);
        dabButtons.addView(dabPrevious, previousParams);
        LinearLayout.LayoutParams playParams = lp(0, -1, 1f);
        playParams.leftMargin = dp(4);
        playParams.rightMargin = dp(4);
        dabButtons.addView(dabPlayPause, playParams);
        LinearLayout.LayoutParams nextParams = lp(0, -1, 1f);
        nextParams.leftMargin = dp(4);
        dabButtons.addView(dabNext, nextParams);
        right.addView(dabButtons, lp(-1, dp(48), 0));
        right.setOnClickListener(v -> AppLauncher.launchDab(this));
        rightStack.addView(right, lp(-1, 0, 1f));

        LinearLayout beltCard = columnPanel();
        beltCard.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout statusColumns = row();
        seatbeltStatus = metricValue("Kein Signal", MUTED, 2);
        washer = metricValue("--", MUTED, 2);
        addMetricColumn(statusColumns, "GURT", seatbeltStatus);
        addMetricColumn(statusColumns, "SCHEIBENWASSER", washer);
        beltCard.addView(statusColumns, lp(-1, -1, 0));
        LinearLayout.LayoutParams beltLp = lp(-1, dp(76), 0);
        beltLp.topMargin = dp(8);
        rightStack.addView(beltCard, beltLp);
        content.addView(rightStack, lp(0, -1, 0.24f));

        root.addView(content, lp(-1, 0, 1f));

        View bronzeTrim = new View(this);
        bronzeTrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                loadedSkin == 0 ? new int[]{Color.rgb(75, 52, 37), Color.rgb(207, 157, 94),
                        Color.rgb(88, 61, 39), Color.rgb(207, 157, 94), Color.rgb(57, 42, 34)}
                        : new int[]{CopperSkin.PANEL_DARK, CopperSkin.GOLD, CopperSkin.PANEL,
                        CopperSkin.GOLD, CopperSkin.PANEL_DARK}));
        bronzeTrim.setElevation(dp(2));
        LinearLayout.LayoutParams trimLp = lp(-1, dp(2), 0);
        trimLp.setMargins(0, dp(8), 0, 0);
        root.addView(bronzeTrim, trimLp);

        LinearLayout dock = row();
        dock.setPadding(0, dp(6), 0, 0);
        addDockButton(dock, DockIconView.VEHICLE, "Fahrzeug", () -> AppLauncher.launchFirst(this, "Polo CAN", "de.ronny.polocan", "de.ronny.polocan.debug"));
        addDockButton(dock, DockIconView.DAB, "DABdream+", () -> AppLauncher.launchDab(this));
        addDockButton(dock, DockIconView.MAPS, "Google Maps", () -> AppLauncher.launchMaps(this));
        addDockButton(dock, DockIconView.CARPLAY, "CarPlay", () -> AppLauncher.launchCarPlay(this));
        addDockButton(dock, DockIconView.YOUTUBE, "YouTube", () -> AppLauncher.launchFirst(this, "YouTube", "com.google.android.youtube"));
        addDockButton(dock, DockIconView.SETTINGS, "Einstellungen", () -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(dock, lp(-1, dp(62), 0));

        List<AppEntry> launchableApps = loadLaunchableApps();
        appCatalogSignature = appSignature(launchableApps);
        final int appsPerPage = 18;
        int appPageCount = Math.max(1, (launchableApps.size() + appsPerPage - 1) / appsPerPage);

        pager = new PagedLauncherLayout(this);
        pager.setPageGestureListener(new PagedLauncherLayout.PageGestureListener() {
            @Override public void onGestureStart() {
                if (offlineMap != null) offlineMap.beginPageGesture();
                if (vectorMap != null) vectorMap.beginPageGesture();
            }
            @Override public void onPageSwipe() {
                if (offlineMap != null) offlineMap.restorePageGesture();
                if (vectorMap != null) vectorMap.restorePageGesture();
            }
        });
        pager.addView(root);
        for (int page = 0; page < appPageCount; page++) {
            int from = page * appsPerPage;
            int to = Math.min(launchableApps.size(), from + appsPerPage);
            pager.addView(buildAppsPage(new ArrayList<>(launchableApps.subList(from, to)), page));
        }

        FrameLayout host = new FrameLayout(this);
        host.setBackground(CopperSkin.background());
        host.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
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
            if (connected) {
                lastVectorFailureMs = 0L;
                vectorFailureCount = 0;
            }
            DiagLog.add("Kartenverbindung: " + (connected ? "online" : "offline"));
        }
        if (vectorMap != null && vectorMap.failed()) {
            lastVectorFailureMs = now;
            vectorFailureCount = Math.min(20, vectorFailureCount + 1);
            if (offlineMap != null) {
                offlineMap.setLocationConsumer(null);
                offlineMap.setAreaConsumer(null);
            }
            if (fallbackGps != null) fallbackGps.setConsumer(null);
            vectorMap.destroy();
            vectorMap = null;
        }
        boolean vectorWanted = connected && (offlineMap != null || fallbackGps != null);
        boolean retryDue = lastVectorFailureMs == 0L || now - lastVectorFailureMs >= vectorRetryDelayMs();
        if (vectorWanted && vectorMap == null && retryDue) {
            try {
                vectorMap = new OnlineVectorMapPanel(this, mapSurface, this::updateMapConnection);
                if (offlineMap != null) {
                    offlineMap.setLocationConsumer(vectorMap::acceptLocation);
                    offlineMap.setAreaConsumer(vectorMap::acceptArea);
                    vectorMap.acceptLocation(offlineMap.currentLocation());
                    vectorMap.acceptArea(offlineMap.currentArea());
                } else if (fallbackGps != null) {
                    fallbackGps.setConsumer(vectorMap::acceptLocation);
                    vectorMap.acceptLocation(fallbackGps.currentLocation());
                }
            } catch (Throwable t) {
                DiagLog.add("MapLibre nicht verfügbar: " + t.getClass().getSimpleName() + " " + t.getMessage());
                if (vectorMap != null) { vectorMap.destroy(); vectorMap = null; }
                lastVectorFailureMs = now;
                vectorFailureCount = Math.min(20, vectorFailureCount + 1);
                vectorWanted = false;
            }
        }
        if (vectorMap != null) {
            vectorMap.setEnabled(vectorWanted && !vectorMap.failed());
            if (mapActivityStarted) vectorMap.start();
            if (mapActivityResumed) vectorMap.resume();
        }
        boolean showVector = vectorWanted && vectorMap != null && vectorMap.ready();
        if (showVector) {
            lastVectorFailureMs = 0L;
            vectorFailureCount = 0;
        }
        if (offlineMap != null) {
            offlineMap.setVisible(!showVector);
            offlineMap.setPersistZoom(!showVector);
        }
        if (showVector) {
            if (offlineMap != null) {
                offlineMap.stop();
                if (fallbackGps != null) fallbackGps.setConsumer(offlineMap::acceptExternalLocation);
            } else if (fallbackGps != null) fallbackGps.setConsumer(vectorMap::acceptLocation);
            if (fallbackGps != null && mapActivityStarted) fallbackGps.start();
        } else {
            if (fallbackGps != null) {
                fallbackGps.setConsumer(null);
                fallbackGps.stop();
            }
            if (offlineMap != null && mapActivityStarted) offlineMap.start();
        }
        if (vectorMap != null) vectorMap.setVisible(showVector);
        if (showVector) showMapCreditBriefly("  ONLINE · © OpenStreetMap · © OpenMapTiles  ");
        else showMapCreditBriefly(OfflineMapStore.selectedFile(this) != null
                ? "  OFFLINE · © OpenStreetMap-Mitwirkende  "
                : connected && !retryDue ? "  ONLINE-KARTE: NEUER VERSUCH GLEICH  "
                : connected ? "  ONLINE-KARTE LÄDT  " : "  KEINE KARTE · © OpenStreetMap-Mitwirkende  ");
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
        TextView credits = label("", 16, CopperSkin.CREAM, Typeface.NORMAL);
        credits.setPadding(dp(20), dp(16), dp(20), dp(16));
        credits.setText(android.text.Html.fromHtml(
                "Kartendaten: © <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap-Mitwirkende</a> (ODbL)<br><br>"
                + "Online-Karte: <a href=\"https://openfreemap.org/\">OpenFreeMap</a><br>"
                + "© <a href=\"https://openmaptiles.org/\">OpenMapTiles</a><br><br>"
                + "Offline-Darstellung: Mapsforge / OpenStreetMap",
                android.text.Html.FROM_HTML_MODE_LEGACY));
        credits.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        credits.setLinkTextColor(CopperSkin.GOLD);
        CopperSkin.dialog(this).setTitle("Kartenquellen und Lizenzen")
                .setView(credits).setPositiveButton("Schließen", null).show();
    }

    private long vectorRetryDelayMs() {
        if (vectorFailureCount <= 0) return 0L;
        int shifts = Math.min(4, vectorFailureCount - 1);
        return Math.min(VECTOR_RETRY_MAX_MS, VECTOR_RETRY_MIN_MS << shifts);
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
        screen.setBackground(CopperSkin.background());

        LinearLayout header = row();
        TextView title = label("ALLE APPS", 22, CopperSkin.CREAM, Typeface.BOLD);
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

        TextView hint = label(page == 0 ? "Nach rechts wischen: zurück zum Polo-Dashboard" : "Horizontal wischen: weitere Seiten", 12, MUTED, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        screen.addView(hint, lp(-1, dp(28), 0));
        return screen;
    }

    private View buildAppTile(AppEntry app) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5), dp(7), dp(5), dp(5));
        tile.setBackground(CopperSkin.touch(this, false));
        tile.setElevation(dp(3));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        loadIconAsync(app.info, icon);
        tile.addView(icon, lp(dp(58), 0, 1f));

        TextView name = label(app.label, 12, CopperSkin.CREAM, Typeface.BOLD);
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
            updateMapConnection();
            Date now = new Date();
            setTextIfChanged(clock, timeFormat.format(now));
            setTextIfChanged(date, capitalize(dateFormat.format(now)));

            VehicleState s = VehicleRepository.snapshot();
            double shownTank = valid(s.fuelLiters) ? s.fuelLiters : RangeDisplayStore.tankLitres(MainActivity.this);
            setTextIfChanged(fuel, valid(shownTank) ? String.format(Locale.GERMANY, "%.0f L", shownTank) : "-- L");
            int remaining = RangeTrackingService.remainingKm(MainActivity.this);
            setTextIfChanged(estimatedRange, remaining >= 0 ? "ca. " + remaining + " km" : "ca. -- km");
            updateConsumption(s);
            setTextIfChanged(voltage, valid(s.voltage) ? String.format(Locale.GERMANY, "%.1f V", s.voltage) : "-- V");
            setTextIfChanged(outside, valid(s.outsideC) ? String.format(Locale.GERMANY, "%.1f °C", s.outsideC) : "-- °C");
            updateWasherStatus(s);
            updateSeatbeltStatus(s);

            showDoorWarning(s);
            // Event-driven: the watcher only retries registration here (cheap), no Binder call per tick.
            dabWatcher.poll();
            dabControlsStatus = dabWatcher.status();
            updateDabButtons();
            setTextIfChanged(dabTitle, MediaInfo.title());
            setTextIfChanged(dabSubtitle, MediaInfo.subtitle());
            Bitmap artwork = MediaInfo.artwork();
            if (lastDabArtwork != artwork) {
                lastDabArtwork = artwork;
                dabArtwork.setImageBitmap(artwork);
                dabArtwork.setVisibility(artwork == null ? View.INVISIBLE : View.VISIBLE);
            }
            if (activityVisible) handler.postDelayed(this, 500L);
        }
    };

    private TextView dabButton(String symbol, String description, int command) {
        TextView button = label(symbol, 18, CopperSkin.CREAM, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(CopperSkin.touch(this, command == DabControls.TOGGLE));
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

    private void updateSeatbeltStatus(VehicleState s) {
        if (seatbeltStatus == null) return;
        long age = VehicleRepository.now() - s.seatbeltUpdatedAtMs;
        if (!s.seatbeltKnown || age < 0 || age > 15000L) {
            setTextIfChanged(seatbeltStatus, "Kein Signal");
            setColorIfChanged(seatbeltStatus, MUTED);
        } else if (s.seatbeltOpen) {
            setTextIfChanged(seatbeltStatus, "⚠ Gurtwarnung");
            setColorIfChanged(seatbeltStatus, Color.rgb(255, 139, 100));
        } else {
            // CAN has only a shared warning bit; no individual seat states.
            setTextIfChanged(seatbeltStatus, "Keine Warnung");
            setColorIfChanged(seatbeltStatus, CopperSkin.GREEN);
        }
        seatbeltStatus.setContentDescription("Gurtstatus: " + seatbeltStatus.getText());
    }

    private final AverageDisplayFilter averageDisplayFilter = new AverageDisplayFilter();

    private void updateConsumption(VehicleState s) {
        if (consumption == null) return;
        // Mature dashboard display only: raw cumulative totals/range stay unchanged.
        double average = averageDisplayFilter.update(Obd2Runtime.reliableDisplayAverage(this),
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
            setColorIfChanged(washer, MUTED);
        } else if (s.washerLow) {
            setTextIfChanged(washer, "NIEDRIG");
            setColorIfChanged(washer, Color.rgb(255, 139, 100));
        } else {
            // CAN provides a low-fluid warning bit, not the level in litres.
            setTextIfChanged(washer, "OK");
            setColorIfChanged(washer, CopperSkin.GREEN);
        }
    }

    private interface TextCapture { void set(TextView value); }

    private TextView metricHeading(String text) {
        TextView heading = label(text, 10, ACCENT, Typeface.BOLD);
        heading.setMaxLines(1);
        heading.setAutoSizeTextTypeUniformWithConfiguration(6, 10, 1, TypedValue.COMPLEX_UNIT_SP);
        return heading;
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
    private void addMetricColumn(LinearLayout columns, String caption, TextView value) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(2), 0, dp(2), 0);
        TextView heading = metricHeading(caption);
        heading.setGravity(Gravity.CENTER);
        heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        value.setGravity(Gravity.CENTER);
        value.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        column.addView(heading, lp(-1, dp(22), 0));
        column.addView(value, lp(-1, 0, 1f));
        columns.addView(column, lp(0, -1, 1f));
    }

    private void addTankMetric(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(5), dp(12), dp(5));
        box.setBackground(CopperSkin.panel(this, false));
        box.setElevation(dp(4));
        LinearLayout columns = row();
        fuel = metricValue("-- L", CopperSkin.CREAM, 1);
        estimatedRange = metricValue("ca. -- km", CopperSkin.CREAM, 1);
        addMetricColumn(columns, "TANK", fuel);
        addMetricColumn(columns, "REICHWEITE", estimatedRange);
        box.addView(columns, lp(-1, -1, 0));
        LinearLayout.LayoutParams params = lp(-1, 0, 1f);
        params.setMargins(0, dp(4), 0, dp(4));
        parent.addView(box, params);
    }

    private void addConsumptionMetric(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(5), dp(12), dp(5));
        box.setBackground(CopperSkin.panel(this, false));
        box.setElevation(dp(4));
        LinearLayout columns = row();
        consumption = metricValue("-- l", CopperSkin.CREAM, 1);
        avgConsumptionView = metricValue("-- l", CopperSkin.CREAM, 1);
        addMetricColumn(columns, "AKTUELL", consumption);
        addMetricColumn(columns, "Ø 100KM", avgConsumptionView);
        box.addView(columns, lp(-1, -1, 0));
        LinearLayout.LayoutParams consumptionParams = lp(-1, 0, 1f);
        consumptionParams.setMargins(0, dp(4), 0, dp(4));
        parent.addView(box, consumptionParams);
    }

    private void addMetric(LinearLayout parent, String caption, String initial, TextCapture capture) {
        LinearLayout.LayoutParams params = lp(-1, 0, 1f);
        params.setMargins(0, dp(4), 0, dp(4));
        parent.addView(metric(caption, initial, capture), params);
    }

    private View metric(String caption, String initial, TextCapture capture) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(5), dp(12), dp(5));
        box.setBackground(CopperSkin.panel(this, false));
        box.setElevation(dp(4));
        TextView cap = metricHeading(caption);
        TextView value = metricValue(initial, CopperSkin.CREAM, 1);
        capture.set(value);
        box.addView(cap, lp(-1, dp(22), 0));
        box.addView(value, lp(-1, 0, 1));
        return box;
    }

    private void addDockButton(LinearLayout dock, int icon, String description, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setContentDescription(description);
        b.setBackground(CopperSkin.touch(this, false));
        b.setElevation(dp(4));
        DockIconView graphic = new DockIconView(this, icon);
        graphic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        b.addView(graphic, iconParams);
        b.setOnClickListener(v -> action.run());
        b.setClickable(true);
        b.setFocusable(true);
        LinearLayout.LayoutParams p = lp(0, -1, 1f);
        p.setMargins(dp(8), 0, dp(8), 0);
        dock.addView(b, p);
    }

    private LinearLayout columnPanel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(10), dp(12), dp(10));
        l.setBackground(CopperSkin.panel(this, false));
        l.setElevation(dp(4));
        return l;
    }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView label(String text, float size, int color, int style) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.create("sans-serif", style)); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private LinearLayout.LayoutParams lp(int width, int height, float weight) { return new LinearLayout.LayoutParams(width, height, weight); }
    private GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); if (strokeWidth > 0) d.setStroke(strokeWidth, stroke); return d;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static boolean valid(double d) { return !Double.isNaN(d) && !Double.isInfinite(d); }
    private static void setTextIfChanged(TextView view, CharSequence value) {
        if (!android.text.TextUtils.equals(view.getText(), value)) view.setText(value);
    }
    private static void setColorIfChanged(TextView view, int color) {
        if (view.getCurrentTextColor() != color) view.setTextColor(color);
    }
    private static String capitalize(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
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
        if (loadedSkin != CopperSkin.selection(this) && !isFinishing()) {
            DiagLog.add("Hauptseite neu: Skin bewusst gewechselt"); recreate(); return;
        }
        hideSystemUi();
        doorFlFault = VehiclePreferences.doorFlFault(this);
        finishMapDownloadInBackground();
        if (!loadedMapSignature.equals(OfflineMapStore.selectedSignature(this)) && !isFinishing()) {
            DiagLog.add("Hauptseite neu: Offline-Region bewusst gewechselt"); recreate(); return;
        }
        if (!loadedVectorStyle.equals(MapPreferences.vectorStyle(this)) && !isFinishing()) {
            DiagLog.add("Hauptseite neu: Online-Kartenstil bewusst gewechselt"); recreate(); return;
        }
        mapActivityResumed = true;
        handler.removeCallbacks(checkMapDownload);
        handler.postDelayed(checkMapDownload, 5_000L);
        updateMapConnection();
        if (offlineMap != null) offlineMap.applySavedZoom();
        if (offlineMap != null) offlineMap.applyHeadingMode();
        if (vectorMap != null) vectorMap.applySavedZoom();
        if (vectorMap != null) vectorMap.applyHeadingMode();
        handler.removeCallbacks(startDabAutomatically);
        handler.postDelayed(startDabAutomatically, 800L);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(startDabAutomatically);
        mapActivityResumed = false;
        handler.removeCallbacks(checkMapDownload);
        if (vectorMap != null) vectorMap.pause();
        super.onPause();
    }

    private void finishMapDownloadInBackground() {
        // The callback does not capture this Activity. OfflineMapStore resolves its weak
        // owner only after validation has finished, so a slow map file cannot retain us.
        OfflineMapStore.finishIfCompleteAsync(this, (owner, message) ->
                ((MainActivity) owner).handleFinishedMapDownload(message));
    }

    private final Runnable checkMapDownload = new Runnable() {
        @Override public void run() {
            if (!mapActivityResumed) return;
            if (OfflineMapStore.activeId(MainActivity.this) >= 0L) finishMapDownloadInBackground();
            handler.postDelayed(this, 5_000L);
        }
    };

    private void handleFinishedMapDownload(String message) {
        DiagLog.add(message);
        if (loadedMapSignature != null
                && !loadedMapSignature.equals(OfflineMapStore.selectedSignature(this))) {
            DiagLog.add("Hauptseite neu: Offline-Download abgeschlossen");
            recreate();
        }
    }
    @Override protected void onStart() {
        super.onStart();
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
        if (appCatalogDirty) scheduleAppRefresh();
        if (vectorMap != null) vectorMap.start();
    }
    @Override protected void onStop() {
        mapActivityStarted = false;
        activityVisible = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(refreshAppPages);
        dabWatcher.stop();
        if (runtimeActive) {
            VendorRuntime.release(getApplicationContext());
            runtimeActive = false;
        }
        if (offlineMap != null) offlineMap.stop();
        if (fallbackGps != null) fallbackGps.stop();
        if (vectorMap != null) vectorMap.stop();
        super.onStop();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        if (vectorMap != null) vectorMap.lowMemory();
    }
    @Override protected void onDestroy() {
        iconLoader.shutdownNow();
        if (dabWatcher != null) dabWatcher.stop();
        if (dabScreenReceiverRegistered) {
            unregisterReceiver(dabScreenReceiver);
            dabScreenReceiverRegistered = false;
        }
        if (offlineMap != null) {
            offlineMap.setLocationConsumer(null);
            offlineMap.setAreaConsumer(null);
        }
        if (fallbackGps != null) fallbackGps.setConsumer(null);
        if (vectorMap != null) vectorMap.destroy();
        if (offlineMap != null) offlineMap.destroy();
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
