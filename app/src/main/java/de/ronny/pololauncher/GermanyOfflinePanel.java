package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Single seamless offline Germany map built from the installed Shortbread archives. */
final class GermanyOfflinePanel {
    private static final long LOAD_TIMEOUT_MS = 45_000L;
    /** Active (visible and started) time one style attempt may take before the next fallback level is tried. */
    private static final long ATTEMPT_ACTIVE_MS = 12_000L;
    // Attempt ladder: way of delivering the tiles (direct mbtiles:// or the local tile server) x style level.
    private static final boolean[] ATT_HTTP = {false, true, true, true};
    private static final int[] ATT_LEVEL = {0, 0, 1, 2};
    private static final int ATT_PROBE = 4;
    private static final String[] ATT_NAMES = {"direkt mbtiles://, voll", "Kachelserver, voll",
            "Kachelserver, ohne Icons", "Kachelserver, nur Flächen und Linien", "nur Hintergrund (Probe)"};

    private final Context context;
    private final FrameLayout surface;
    private final MapView view;
    private final View nightShade;
    private final MarkerArtView marker;
    private final TextView gps;
    private final TextView notice;
    private final GermanyOfflineAnalyzer analyzer;
    private final Runnable timeout;
    private final Runnable stateChanged;
    private final TravelBearingTracker course = new TravelBearingTracker();

    private MapLibreMap map;
    private Location lastLocation;
    private MapArea area;
    private boolean following = true;
    private boolean visible;
    private boolean started;
    private boolean resumed;
    private boolean loaded;
    private boolean styleApplied;
    private boolean failed;
    private boolean destroyed;
    private boolean nightMode;
    private boolean styleNight;
    private int styleBuildingHeight = -1;
    private final BearingSmoother bearingSmoother = new BearingSmoother();
    private double smoothIntervalMs;
    private List<String> styleRegions = new ArrayList<>();
    private boolean styleLite;
    private boolean styleReloading;
    private int level;                 // index into the attempt ladder
    private int workingLevel;
    private LocalTileServer server;
    private MbtilesTileLookup lookup;
    private int generation;
    private int loadErrorsLogged;
    private long activeMs;
    private boolean selfTestStarted;
    private Runnable attemptTick;
    private boolean slowShown;
    private long lastStyleLoadMs;
    private long lastPositionSavedMs;
    private long styleAppliedAtMs;
    private long lastMoveAtMs;
    private final CameraAim cameraAim = new CameraAim();
    private Runnable zoomStep;
    /** Wanted zoom level while a settlement/outside change is gliding in; -1 = none. Applied inside the per-fix glide. */
    private int zoomGoal = -1;
    private static final double ZOOM_LEVELS_PER_SECOND = 0.5d;
    private Consumer<Location> locationConsumer;

    GermanyOfflinePanel(Context context, FrameLayout surface, Runnable stateChanged, boolean nightMode) {
        if (!GermanyOfflineStore.markedReady(context))
            throw new IllegalStateException("Deutschland-Paket nicht vollständig");
        this.context = context;
        this.surface = surface;
        this.stateChanged = stateChanged;
        this.nightMode = nightMode;
        MapLibre.getInstance(context.getApplicationContext());
        MapFollowDiagnostics.offlineCreated();

        view = new MapView(context, new MapLibreMapOptions().textureMode(true));
        view.onCreate(null);
        // Start hidden on purpose. MainActivity explicitly promotes the finished Germany
        // package as the first visible map before any online renderer is allowed to take over.
        // Keeping visible=true here used to make the first setVisible(true) a no-op, leaving
        // the MapLibre TextureView behind the dashboard until the online map appeared.
        view.setVisibility(View.GONE);
        // Never show a black rectangle while tiles are still loading: paper colour behind the texture.
        view.setBackgroundColor(Color.parseColor(nightMode ? OfflineStyleComposer.BACKGROUND_DARK : OfflineStyleComposer.BACKGROUND));
        surface.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));

        nightShade = new View(context);
        nightShade.setBackgroundColor(Color.argb(64, 0, 36, 32));
        nightShade.setClickable(false);
        nightShade.setFocusable(false);
        nightShade.setVisibility(View.GONE);
        surface.addView(nightShade, new FrameLayout.LayoutParams(-1, -1));

        marker = new MarkerArtView(context);
        marker.setElevation(dp(5));
        marker.setVisibility(View.GONE);
        surface.addView(marker, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        applyMarkerStyle();

        gps = new TextView(context);
        gps.setText("◎  GPS");
        gps.setTextSize(13);
        gps.setTextColor(GlassSkin.TEXT);
        gps.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gps.setGravity(Gravity.CENTER);
        gps.setContentDescription("Auf GPS-Position zentrieren");
        // Ovaler Rahmen im Stil des Info-Knopfs: nur Linie, keine Füllfläche; ein dunkler Saum
        // unter der hellen Linie hält sie auf heller Karte lesbar.
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.RECTANGLE);
        halo.setCornerRadius(dp(40));
        halo.setColor(Color.TRANSPARENT);
        halo.setStroke(Math.round(dp(1) * 3.4f), Color.argb(110, 0, 0, 0));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius(dp(40));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.round(dp(1) * 1.8f), GlassSkin.TEXT);
        android.graphics.drawable.LayerDrawable frame =
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {halo, ring});
        int inset = Math.round(dp(1) * 0.8f);
        frame.setLayerInset(1, inset, inset, inset, inset);
        gps.setBackground(frame);
        gps.setShadowLayer(dp(3), 0f, 0f, 0xCC000000);
        FrameLayout.LayoutParams gpsLp = new FrameLayout.LayoutParams(dp(80), dp(34),
                Gravity.BOTTOM | Gravity.RIGHT);
        gpsLp.setMargins(0, 0, dp(10), dp(9));
        surface.addView(gps, gpsLp);
        gps.setVisibility(View.GONE);
        gps.setOnClickListener(v -> {
            following = true;
            applySavedZoom();
            if (lastLocation != null) center(lastLocation, false);
        });

        notice = new TextView(context);
        notice.setText("Offline-Karte wird geladen …");
        notice.setTextSize(18);
        notice.setTextColor(Color.rgb(70, 66, 60));
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(24), dp(12), dp(24), dp(12));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(14));
        pill.setColor(Color.argb(215, 255, 255, 255));
        notice.setBackground(pill);
        notice.setVisibility(View.GONE);
        FrameLayout.LayoutParams noticeLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        noticeLp.topMargin = dp(18);
        surface.addView(notice, noticeLp);

        analyzer = new GermanyOfflineAnalyzer(context, surface);
        // Navigation must never wait for the comparatively expensive traffic lookup.
        // Raw/filtered GPS is delivered to the visible map immediately in acceptLocation();
        // the analyzer runs independently for speed limits, cameras and area classification.
        analyzer.setAreaConsumer(this::deliverArea);

        timeout = () -> {
            // A visible, style-ready local map is already usable for navigation.
            // MapLibre's fully-rendered callback is only a migration confirmation and
            // may legitimately lag or never become true while tiles are still settling.
            if (destroyed || styleApplied || failed) return;
            // A hidden/stopped MapView never applies its style (online map is shown). That is not
            // a failure; failing here used to tear down Germany and hand GPS to the old map.
            if (!visible) { rearmLoadTimeout(); return; }
            fail("Zeitüberschreitung beim Laden der Deutschlandkarte");
        };
        view.addOnDidFailLoadingMapListener(message -> {
            if (destroyed || failed) return;
            if (styleReloading) {
                DiagLog.add("Offline-Karte Ladefehler (Versuch " + level + "): " + message);
                advance("Ladefehler: " + message);
            } else if (loadErrorsLogged++ < 5) {
                DiagLog.add("Offline-Karte Ladefehler nach dem Stil: " + message);
            }
        });
        view.addOnDidFinishRenderingMapListener(fully -> {
            // Any finished render pass after the style is in means the beige/paper map is on screen.
            if (!destroyed && !failed && styleApplied && !slowShown) notice.setVisibility(View.GONE);
            if (destroyed || failed || loaded || !styleApplied || !fully) return;
            loaded = true;
            slowShown = false;
            notice.setVisibility(View.GONE);
            surface.removeCallbacks(timeout);
            GermanyOfflineStore.confirmRendered(context);
            MapFollowDiagnostics.offlineRendered();
            DiagLog.add("Offline Deutschland MapLibre: vollständig gerendert");
            surface.post(stateChanged);
        });
        view.getMapAsync(ready -> {
            if (destroyed || failed) return;
            try {
                map = ready;
                map.setMinZoomPreference(MapPreferences.MIN_ZOOM);
                map.setMaxZoomPreference(MapPreferences.MAX_ZOOM);
                map.getUiSettings().setCompassEnabled(false);
                map.getUiSettings().setLogoEnabled(false);
                map.getUiSettings().setAttributionEnabled(false);
                map.addOnCameraMoveListener(MapFollowDiagnostics::cameraFrame);
                // Prove a real Germany tile before deleting the former Mapsforge map.
                // Without a GPS fix MapLibre would otherwise render its default 0/0 view successfully.
                double[] known = MapPreferences.lastKnown(context);
                // Start where the radio was switched off: position, heading and zoom of the last session.
                float savedZoom = MapPreferences.lastZoom(context);
                float savedBearing = MapPreferences.lastBearing(context);
                map.moveCamera(CameraUpdateFactory.newCameraPosition(known != null
                        ? new CameraPosition.Builder().target(new LatLng(known[0], known[1]))
                                .zoom(Float.isNaN(savedZoom) ? 12d : savedZoom)
                                .bearing(MapPreferences.headingUp(context) && !Float.isNaN(savedBearing) ? savedBearing : 0d)
                                .build()
                        : new CameraPosition.Builder().target(new LatLng(51.1657d, 10.4515d)).zoom(6d).build()));
                // Follow mode is intentionally permanent on the car dashboard.
                // Do not derive follow state from MapLibre camera callbacks: on some
                // head units/page gestures they can report a gesture even though the
                // driver never intended to leave navigation follow mode.
                map.addOnCameraIdleListener(() -> {
                    if (destroyed || map == null || following) return;
                    if (area == null)
                        MapPreferences.setZoom(context, (int) Math.round(map.getCameraPosition().zoom));
                });
                loadStyle();
            } catch (Throwable t) {
                fail("Renderer: " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        });
    }

    /**
     * Builds the style for the packages around the current (or last known) position and applies it.
     * Only the neighbouring federal states are bound as sources; crossing into another one reloads it.
     * If a style does not finish loading, simpler fallback levels are tried and every step is logged.
     */
    private void loadStyle() {
        // First start: use the delivery way that worked last time on this radio.
        if (!styleApplied && workingLevel == 0 && "http".equals(MapPreferences.offlineTransport(context))) workingLevel = 1;
        startAttempt(workingLevel);
    }

    private boolean ensureServer() {
        if (server != null) return true;
        try {
            lookup = new MbtilesTileLookup(context);
            server = new LocalTileServer(lookup, lookup);
            DiagLog.add("Kachelserver gestartet: " + server.base());
            final LocalTileServer started = server;
            final double[] known = MapPreferences.lastKnown(context);
            Thread check = new Thread(() -> {
                String path = "/f/sprites/sprite.json";
                String a = started.selfCheck(path);
                String tilePath = "/t/sachsen/6/34/20.pbf";
                if (known != null) {
                    java.util.List<String> keys = GermanyRegionIndex.select(known[0], known[1], 0.12d, 1);
                    if (!keys.isEmpty()) tilePath = "/t/" + keys.get(0) + "/14/" + MvtRoadDecoder.tileX(known[1], 14)
                            + "/" + MvtRoadDecoder.tileY(known[0], 14) + ".pbf";
                }
                DiagLog.add("Kachelserver Selbstabruf: " + path + " → " + a + " · " + tilePath + " → " + started.selfCheck(tilePath));
            }, "tile-server-check");
            check.setDaemon(true);
            check.start();
            return true;
        } catch (Throwable t) {
            DiagLog.add("Kachelserver nicht startbar: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return false;
        }
    }

    private void startAttempt(int lvl) {
        if (map == null || destroyed) return;
        level = lvl;
        final int gen = ++generation;
        if (attemptTick != null) surface.removeCallbacks(attemptTick);
        Location ref = lastLocation;
        double[] known = MapPreferences.lastKnown(context);
        boolean hasPos = ref != null || known != null;
        double lat = ref != null ? ref.getLatitude() : known != null ? known[0] : 0d;
        double lon = ref != null ? ref.getLongitude() : known != null ? known[1] : 0d;
        if (!selfTestStarted) {
            selfTestStarted = true;
            OfflineMapSelfTest.start(context, hasPos, lat, lon);
        }
        final GermanyOfflineStyle.Built built;
        final String json;
        long t0 = SystemClock.elapsedRealtime();
        if (lvl >= ATT_PROBE) {
            built = null;
            json = OfflineStyleComposer.BACKGROUND_ONLY_STYLE;
        } else {
            try {
                if (ATT_HTTP[lvl] && !ensureServer()) { advance("Kachelserver nicht startbar"); return; }
                styleBuildingHeight = MapPreferences.buildingHeight(context);
                styleNight = nightMode;
                built = GermanyOfflineStyle.build(context, hasPos, lat, lon, styleBuildingHeight, styleNight, ATT_LEVEL[lvl],
                        ATT_HTTP[lvl] ? server.base() : null);
            } catch (Throwable t) {
                fail("Stil nicht erstellbar: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return;
            }
            json = built.json;
        }
        styleReloading = true;
        activeMs = 0L;
        lastStyleLoadMs = SystemClock.elapsedRealtime();
        DiagLog.add("Offline-Stil Versuch " + lvl + " (" + ATT_NAMES[lvl] + ")"
                + (built == null ? "" : ": " + built.layerCount + " Ebenen, Pakete " + built.regions
                + (built.lite ? " (grob, keine Position)" : "")
                + (styleNight ? ", Nacht" : ", Tag") + ", Neigung " + MapPreferences.tilt(context) + "°, Gebäude " + (styleBuildingHeight > 0 ? styleBuildingHeight + " m" : "flach")
                + ", zusammengebaut in " + (lastStyleLoadMs - t0) + " ms, " + json.length() / 1024 + " kB"));
        // Only time while the map is visible and started counts: a stopped MapView cannot load anything.
        attemptTick = new Runnable() {
            @Override public void run() {
                if (gen != generation || destroyed || failed) return;
                if (visible && started) activeMs += 2_000L;
                if (activeMs >= ATTEMPT_ACTIVE_MS) {
                    advance("keine Antwort nach " + (ATTEMPT_ACTIVE_MS / 1000) + " s aktiver Zeit");
                    return;
                }
                surface.postDelayed(this, 2_000L);
            }
        };
        surface.postDelayed(attemptTick, 2_000L);
        map.setStyle(new Style.Builder().fromJson(json), style -> {
            if (gen != generation || destroyed || failed || map == null) return;
            if (attemptTick != null) surface.removeCallbacks(attemptTick);
            styleReloading = false;
            if (lvl >= ATT_PROBE) {
                fail("MapLibre kann die Kartendateien nicht öffnen (Hintergrund-Probe ok)");
                return;
            }
            workingLevel = lvl;
            if (styleNight != nightMode) {
                // the day/night switch came while this style was loading: load the matching theme next
                surface.post(() -> { if (!destroyed && !failed && !styleReloading) loadStyle(); });
            }
            MapPreferences.setOfflineTransport(context, ATT_HTTP[lvl] ? "http" : "direct");
            styleRegions = built.regions;
            styleLite = built.lite;
            styleApplied = true;
            styleAppliedAtMs = SystemClock.elapsedRealtime();
            surface.removeCallbacks(timeout);
            MapFollowDiagnostics.offlineStyle();
            DiagLog.add("Offline Deutschland MapLibre: Stil Versuch " + lvl + " (" + ATT_NAMES[lvl] + ") angewendet nach "
                    + (styleAppliedAtMs - lastStyleLoadMs) + " ms (sichtbar=" + visible
                    + ", gestartet=" + started + ", fortgesetzt=" + resumed + ")");
            // Tell MainActivity right away: the online map is only created once this one is ready.
            surface.post(stateChanged);
            applySavedZoom();
            applyTilt();
            if (lastLocation != null) center(lastLocation, false);
            if (visible) requestRenderFrame();
            if (!loaded) surface.postDelayed(slowLoadNotice, 20_000L);
            if (ATT_LEVEL[lvl] > 0) {
                notice.setText("Offline-Karte eingeschränkt: " + ATT_NAMES[lvl]);
                notice.setVisibility(visible ? View.VISIBLE : View.GONE);
                surface.postDelayed(() -> {
                    if (!destroyed && !failed && !slowShown) notice.setVisibility(View.GONE);
                }, 8_000L);
            }
        });
    }

    /** The current attempt did not work: try the next simpler level, last a background-only probe. */
    private void advance(String reason) {
        if (destroyed || failed) return;
        if (attemptTick != null) surface.removeCallbacks(attemptTick);
        DiagLog.add("Offline-Stil Versuch " + level + " (" + ATT_NAMES[level] + ") fehlgeschlagen: " + reason);
        if (level < ATT_PROBE) { startAttempt(level + 1); return; }
        fail("MapLibre lädt gar keinen Stil (" + reason + ")");
    }

    private final Runnable slowLoadNotice = new Runnable() {
        @Override public void run() {
            if (destroyed || loaded || failed) return;
            slowShown = true;
            notice.setText("Offline-Karte lädt noch …\nBleibt das so: Kartendaten im Menü prüfen oder neu laden.");
            notice.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    };

    private void applyTilt() {
        if (map == null || destroyed) return;
        double wanted = MapPreferences.tilt(context);
        CameraPosition current = map.getCameraPosition();
        if (Math.abs(current.tilt - wanted) > 0.5d)
            map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder(current).tilt(wanted).build()));
    }

    /** Back from the map menu: apply a changed tilt right away and rebuild the style for a changed building height. */
    /** Applies the chosen marker look (dot, pumpkin, sleigh) and, for the sleigh, its heading. */
    void applyMarkerStyle() {
        String id = MapPreferences.markerStyle(context);
        marker.setStyle(id);
        int[] size = MarkerStyle.sizeDp(id);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(size[0]), dp(size[1]), Gravity.CENTER);
        marker.setLayoutParams(lp);
        marker.setPivotX(dp(size[0]) / 2f);
        marker.setPivotY(dp(size[1]) / 2f);
        updateMarkerRotation();
    }

    private void updateMarkerRotation() {
        float rotation = 0f;
        if (MarkerStyle.rotates(MapPreferences.markerStyle(context)) && map != null && course.known()) {
            double diff = course.bearing() - map.getCameraPosition().bearing;
            rotation = (float) (((diff % 360d) + 360d) % 360d);
        }
        marker.setRotation(rotation);
    }

    void applyViewSettings() {
        if (map == null || destroyed || failed) return;
        applyMarkerStyle();
        if (styleApplied && !styleReloading && styleBuildingHeight >= 0
                && styleBuildingHeight != MapPreferences.buildingHeight(context)) {
            DiagLog.add("Offline-Stil: Gebäudehöhe geändert (" + styleBuildingHeight + " → "
                    + MapPreferences.buildingHeight(context) + " m) – Stil wird neu geladen");
            loadStyle();
        }
        if (cameraReady() && visible) {
            CameraPosition current = map.getCameraPosition();
            double wanted = MapPreferences.tilt(context);
            if (Math.abs(current.tilt - wanted) > 0.5d)
                map.easeCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder(current).tilt(wanted).build()), 600, false);
        }
    }

    /** Remembers position, heading, zoom and area so the next start continues exactly there. */
    private void saveLastState(Location location) {
        if (location == null) return;
        MapPreferences.setLastState(context, location.getLatitude(), location.getLongitude(),
                course.known() ? (float) course.bearing() : Float.NaN, wantedZoom(), area);
    }

    /** Saves the current state immediately (screen off, standby, app leaving). */
    void saveState() {
        if (destroyed) return;
        saveLastState(lastLocation);
    }

    /** Called with every fix: remember the position, and rebuild the style when a new state comes near. */
    private void maintainStyle(Location location) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastPositionSavedMs > 5_000L) {
            lastPositionSavedMs = now;
            saveLastState(location);
        }
        if (map == null || !styleApplied || styleReloading || failed || destroyed) return;
        if (now - lastStyleLoadMs < 15_000L) return;
        boolean needed = styleLite
                ? GermanyRegionIndex.insideGermanyPackages(location.getLatitude(), location.getLongitude())
                : !GermanyRegionIndex.covered(styleRegions, location.getLatitude(), location.getLongitude(),
                        GermanyOfflineStyle.NEED_MARGIN_DEG, GermanyOfflineStyle.MAX_REGIONS)
                  && GermanyRegionIndex.insideGermanyPackages(location.getLatitude(), location.getLongitude());
        if (needed) {
            DiagLog.add("Offline-Stil: neues Bundesland in Sicht – Stil wird neu geladen");
            loadStyle();
        }
    }

    private void rearmLoadTimeout() { surface.postDelayed(timeout, LOAD_TIMEOUT_MS); }

    void setLocationConsumer(Consumer<Location> value) { locationConsumer = value; }
    void setSpeedLimitConsumer(Consumer<Integer> value) { analyzer.setSpeedConsumer(value); }
    void setSpeedCameraConsumer(Consumer<Integer> value) { analyzer.setCameraConsumer(value); }

    void restorePageGesture() { following = true; }

    /**
     * Camera follow must not wait for MapLibre's "fully rendered" callback.
     * Local MBTiles can already be visible and usable while that confirmation is still pending.
     */
    private boolean cameraReady() { return map != null && styleApplied && !failed && !destroyed; }
    boolean failed() { return failed; }
    Location currentLocation() {
        Location location = lastLocation;
        return location == null ? analyzer.currentLocation() : new Location(location);
    }

    /**
     * Already camera-filtered GPS fix. This is deliberately kept separate from the raw fix
     * used by traffic/settlement analysis, matching the proven v0.31.49 Sachsen path.
     */
    void acceptLocation(Location location) {
        if (destroyed || location == null) return;
        deliverLocation(location);
    }

    /** Raw GPS fix for speed-limit, camera and settlement analysis. Never camera-filter it twice. */
    void acceptAnalysisLocation(Location location) {
        if (destroyed || location == null) return;
        analyzer.accept(location);
    }

    private void deliverLocation(Location location) {
        if (destroyed || location == null) return;
        lastLocation = new Location(location);
        course.update(location);
        maintainStyle(location);
        Consumer<Location> consumer = locationConsumer;
        if (consumer != null) consumer.accept(new Location(location));
        // Exactly like the old Sachsen renderer: a hidden/offline fallback may keep analysing
        // and forwarding GPS, but it must not waste camera work behind the visible map.
        if (!visible || !following || !cameraReady()) MapFollowDiagnostics.germanyDropped(visible, following);
        if (!visible || !following || !cameraReady()) return;
        center(location, true);
    }

    private void deliverArea(MapArea next) {
        area = next;
        if (visible && cameraReady() && following)
            changeZoom(wantedZoom());
    }

    void setVisible(boolean show) {
        if (visible == show) return;
        visible = show;
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        nightShade.setVisibility(View.GONE); // the night theme is a real dark style, no veil
        gps.setVisibility(show ? View.VISIBLE : View.GONE);
        notice.setVisibility(show && (!loaded && !styleApplied || failed || slowShown) ? View.VISIBLE : View.GONE);
        if (show) notice.bringToFront();
        marker.setVisibility(show && following && lastLocation != null ? View.VISIBLE : View.GONE);
        if (show) {
            view.bringToFront();
            nightShade.bringToFront();
            marker.bringToFront();
            gps.bringToFront();
            applySavedZoom();
            if (following && lastLocation != null && cameraReady()) center(lastLocation, false);
            applyHeadingMode();
            requestRenderFrame();
        } else {
            cancelZoomStep();
            lastMoveAtMs = 0L;
        }
    }

    private void requestRenderFrame() {
        if (destroyed) return;
        if (map != null) {
            try { map.triggerRepaint(); }
            catch (Throwable ignored) {}
        }
        view.postInvalidate();
    }

    void start() {
        if (!started) { view.onStart(); started = true; }
    }
    void resume() {
        start();
        if (!resumed) {
            view.onResume();
            resumed = true;
            // HCT/ACC quick-start may keep the Java MapView but discard its TextureView frame.
            // Request a fresh frame whenever the local fallback returns to the foreground.
            if (visible) {
                requestRenderFrame();
                if (following && lastLocation != null && cameraReady()) center(lastLocation, false);
            }
        }
    }
    void pause() {
        saveState();
        if (resumed) { view.onPause(); resumed = false; }
    }
    void stop() {
        pause();
        cancelZoomStep();
        lastMoveAtMs = 0L;
        if (started) { view.onStop(); started = false; }
    }
    void lowMemory() { if (!destroyed) view.onLowMemory(); }

    void setNightMode(boolean night) {
        boolean changed = nightMode != night;
        nightMode = night;
        nightShade.setVisibility(View.GONE);
        view.setBackgroundColor(Color.parseColor(night ? OfflineStyleComposer.BACKGROUND_DARK : OfflineStyleComposer.BACKGROUND));
        if (changed && map != null && styleApplied && !styleReloading && !failed && !destroyed) {
            DiagLog.add("Offline-Stil: " + (night ? "Nacht" : "Tag") + "-Theme – Stil wird neu geladen");
            loadStyle();
        }
    }

    void applySavedZoom() {
        if (map == null || destroyed || !visible) return;
        cancelZoomStep();
        int wanted = wantedZoom();
        if (Math.abs(map.getCameraPosition().zoom - wanted) > 0.1d)
            map.moveCamera(CameraUpdateFactory.zoomTo(wanted));
    }

    void applyHeadingMode() {
        if (map == null || destroyed || !visible) return;
        double wantedBearing = MapPreferences.headingUp(context) && course.known()
                ? course.bearing() : 0d;
        bearingSmoother.reset();
        CameraPosition current = map.getCameraPosition();
        if (Math.abs(((wantedBearing - current.bearing + 540d) % 360d) - 180d) < 0.5d) return;
        map.easeCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(current).bearing(wantedBearing).build()), 700, false);
    }

    private int wantedZoom() {
        int zoom = MapPreferences.zoom(context);
        // Until the first area analysis of a new session, use the area of the last session.
        MapArea effective = area != null && area != MapArea.UNKNOWN ? area : MapPreferences.lastArea(context);
        if (effective == MapArea.SETTLEMENT)
            zoom = MapPreferences.settlementZoom(context);
        else if (effective == MapArea.OUTSIDE)
            zoom = MapPreferences.outsideZoom(context);
        else if (!Float.isNaN(MapPreferences.lastZoom(context)))
            zoom = Math.round(MapPreferences.lastZoom(context)); // nothing detected yet: keep the last zoom
        return following ? MapPreferences.clampFollowZoom(zoom) : zoom;
    }

    private void center(Location location, boolean animate) {
        if (map == null || !following || !visible || !cameraReady()) return;
        CameraPosition current = map.getCameraPosition();
        // Distance check uses the real fix; the camera aims slightly ahead (see below).
        LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
        float[] distance = new float[1];
        if (current.target != null)
            Location.distanceBetween(current.target.getLatitude(), current.target.getLongitude(),
                    target.getLatitude(), target.getLongitude(), distance);
        else distance[0] = Float.POSITIVE_INFINITY;

        long now = SystemClock.elapsedRealtime();
        long interval = lastMoveAtMs == 0L ? 0L : now - lastMoveAtMs;
        lastMoveAtMs = now;
        MapFollowDiagnostics.fixArrived(location.hasSpeed() ? location.getSpeed() : Float.NaN, interval);

        // Same safety behaviour as v0.31.49: ignore tiny jitter, glide only for normal nearby
        // GPS cadence and snap after a long gap or a large displacement. A stuck animation can
        // therefore never leave the map at the journey's start point.
        if (animate && CameraMotion.isJitter(distance[0], location.hasSpeed() ? location.getSpeed() : Float.NaN)) {
            marker.setVisibility(View.VISIBLE);
            return;
        }
        double targetBearing = MapPreferences.headingUp(context) && course.known() ? course.bearing() : 0d;
        double zoom = current.zoom < MapPreferences.FOLLOW_MIN_ZOOM ? wantedZoom() : current.zoom;
        boolean glide = animate && interval > 0L && interval < 5_000L && distance[0] <= 250f;
        // One continuous glide per fix: it lasts slightly longer than the usual fix interval, so the
        // next one starts before this one has stopped (no stand-still between the steps), and the
        // heading follows the course through a low-pass filter instead of jumping per fix.
        if (glide) smoothIntervalMs = smoothIntervalMs == 0d ? interval : 0.6d * smoothIntervalMs + 0.4d * interval;
        else smoothIntervalMs = 0d;
        long duration = Math.max(450L, Math.min(2_500L, Math.round((smoothIntervalMs > 0d ? smoothIntervalMs : interval) * 1.1d)));
        // Zoom changes (town in/out) are folded into the same continuous glide instead of separate
        // hard 1-level jumps: no extra stutters, and the per-fix ease no longer fights a stepped zoom.
        if (zoomGoal > 0) {
            if (!glide) {
                zoom = zoomGoal;
                zoomGoal = -1;
            } else {
                double diff = zoomGoal - zoom;
                double maxStep = ZOOM_LEVELS_PER_SECOND * duration / 1000d;
                if (Math.abs(diff) <= Math.max(0.02d, maxStep)) {
                    zoom = zoomGoal;
                    zoomGoal = -1;
                } else {
                    zoom += Math.signum(diff) * maxStep;
                }
            }
        }
        double bearing;
        if (glide && MapPreferences.headingUp(context) && course.known()) {
            bearing = bearingSmoother.next(current.bearing, targetBearing, interval);
        } else {
            bearingSmoother.reset();
            bearing = targetBearing;
        }
        double[] aim = glide ? cameraAim.next(location, duration, now) : cameraAim.hold(location, now);
        target = new LatLng(aim[0], aim[1]);
        CameraPosition next = new CameraPosition.Builder(current)
                .target(target).bearing(bearing).zoom(zoom).tilt(MapPreferences.tilt(context)).build();
        if (glide) {
            map.easeCamera(CameraUpdateFactory.newCameraPosition(next), (int) duration, false);
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(next));
        }
        MapFollowDiagnostics.moved("germany", location.getLatitude(), location.getLongitude());
        marker.setVisibility(View.VISIBLE);
        updateMarkerRotation();
    }

    private void cancelZoomStep() {
        if (zoomStep != null) surface.removeCallbacks(zoomStep);
        zoomStep = null;
        zoomGoal = -1;
    }

    /**
     * Sets the new zoom goal. The following GPS fixes glide towards it together with the position.
     * Only if no fix arrives (standing still, glide impossible) a single smooth ease finishes the job.
     */
    private void changeZoom(int wanted) {
        cancelZoomStep();
        zoomGoal = wanted;
        zoomStep = new Runnable() {
            @Override public void run() {
                zoomStep = null;
                if (zoomGoal < 0) return; // already reached through the fix glide
                int goal = zoomGoal;
                zoomGoal = -1;
                if (!visible || !following || !cameraReady()) return;
                if (Math.abs(map.getCameraPosition().zoom - goal) < 0.05d) return;
                map.easeCamera(CameraUpdateFactory.zoomTo(goal), 1_800, false);
            }
        };
        surface.postDelayed(zoomStep, 4_500L);
    }

    private void fail(String detail) {
        if (destroyed || failed) return;
        failed = true;
        loaded = false;
        styleApplied = false;
        DiagLog.add("Offline Deutschland MapLibre: " + detail);
        notice.setText("Offline-Karte konnte nicht angezeigt werden:\n" + detail);
        notice.setVisibility(visible ? View.VISIBLE : View.GONE);
        surface.removeCallbacks(timeout);
        surface.post(stateChanged);
    }

    void destroy() {
        destroyed = true;
        surface.removeCallbacks(timeout);
        surface.removeCallbacks(slowLoadNotice);
        if (attemptTick != null) surface.removeCallbacks(attemptTick);
        final LocalTileServer oldServer = server;
        final MbtilesTileLookup oldLookup = lookup;
        server = null;
        lookup = null;
        if (oldServer != null) oldServer.close();
        // closing SQLite may wait for a running tile read: never on the UI thread
        if (oldLookup != null) new Thread(oldLookup::close, "tile-lookup-close").start();
        cancelZoomStep();
        analyzer.setLocationConsumer(null);
        analyzer.setSpeedConsumer(null);
        analyzer.setCameraConsumer(null);
        analyzer.close();
        stop();
        view.onDestroy();
        surface.removeView(view);
        surface.removeView(nightShade);
        surface.removeView(marker);
        surface.removeView(gps);
        surface.removeView(notice);
    }

    private int dp(int n) {
        return Math.round(n * context.getResources().getDisplayMetrics().density);
    }
}
