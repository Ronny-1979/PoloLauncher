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

/** Optional online-only map; the installed Mapsforge .map files are never passed to MapLibre. */
final class OnlineVectorMapPanel {
    private static final long LOAD_TIMEOUT_MS = 20_000L;
    private final Context context;
    private final FrameLayout surface;
    private final MapView view;
    private final View marker;
    private final TextView gps;
    private final Runnable stateChanged;
    private final String style;
    private final Runnable timeout;
    private final LoadTimeoutGate timeoutGate = new LoadTimeoutGate();
    private MapLibreMap map;
    private Location lastLocation;
    private SettlementZoomAnalyzer.Area area;
    private boolean following = true;
    private boolean followingBeforeGesture = true;
    private boolean enabled;
    private boolean started;
    private boolean resumed;
    private boolean loaded;
    private boolean failed;
    private boolean destroyed;
    private boolean foreground;
    private long lastFixMs;
    private final TravelBearingTracker course = new TravelBearingTracker();

    OnlineVectorMapPanel(Context context, FrameLayout surface, Runnable stateChanged) {
        this.context = context;
        this.surface = surface;
        this.stateChanged = stateChanged;
        this.style = MapPreferences.vectorStyle(context);
        MapLibre.getInstance(context.getApplicationContext());
        // TextureView obeys the rounded parent clipping and allows the old map
        // to remain above it until the remote style has loaded successfully.
        view = new MapView(context, new MapLibreMapOptions().textureMode(true));
        view.onCreate(null);
        view.setVisibility(View.GONE);
        surface.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));

        marker = new View(context);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.rgb(31, 139, 247));
        dot.setStroke(dp(3), Color.WHITE);
        marker.setBackground(dot);
        marker.setElevation(dp(5));
        marker.setVisibility(View.GONE);
        surface.addView(marker, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));

        gps = new TextView(context);
        gps.setText("◎  GPS");
        gps.setTextSize(13);
        gps.setTextColor(CopperSkin.CREAM);
        gps.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gps.setGravity(Gravity.CENTER);
        gps.setBackground(CopperSkin.touch(context, false));
        FrameLayout.LayoutParams gpsLp = new FrameLayout.LayoutParams(dp(76), dp(37), Gravity.BOTTOM | Gravity.RIGHT);
        gpsLp.setMargins(0, 0, dp(12), dp(12));
        surface.addView(gps, gpsLp);
        gps.setVisibility(View.GONE);
        gps.setOnClickListener(v -> {
            following = true;
            applySavedZoom();
            if (lastLocation != null) center(lastLocation, false);
        });

        timeout = () -> {
            timeoutGate.clear();
            if (destroyed || !enabled || loaded || failed) return;
            fail("Zeitüberschreitung beim Laden");
        };
        view.addOnDidFailLoadingMapListener(this::fail);
        view.addOnDidFinishLoadingMapListener(() -> {
            if (destroyed || failed) return;
            loaded = true;
            cancelTimeout();
            surface.post(stateChanged);
        });
        view.getMapAsync(readyMap -> {
            if (destroyed || failed) return;
            try {
                map = readyMap;
                map.setMinZoomPreference(MapPreferences.MIN_ZOOM);
                map.setMaxZoomPreference(MapPreferences.MAX_ZOOM);
                map.addOnCameraMoveStartedListener(reason -> {
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)
                        following = false;
                });
                map.addOnCameraIdleListener(() -> {
                    if (destroyed || !enabled || map == null || following) return;
                    if (!MapPreferences.autoZoom(context) || area == null)
                        MapPreferences.setZoom(context, (int) Math.round(map.getCameraPosition().zoom));
                });
                map.getUiSettings().setCompassEnabled(false);
                map.getUiSettings().setLogoEnabled(false);
                // MainActivity provides the visible startup credit and a
                // permanent, left-aligned info button with source links.
                map.getUiSettings().setAttributionEnabled(false);
                // Liberty contains building extrusions; 3D adds a tilted camera.
                String remoteStyle = "3d".equals(style) ? "liberty" : style;
                map.setStyle("https://tiles.openfreemap.org/styles/" + remoteStyle, s -> {
                    if (destroyed || failed) return;
                    if ("3d".equals(style))
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(
                                new CameraPosition.Builder(map.getCameraPosition()).tilt(45d).build()));
                    applySavedZoom();
                    if (lastLocation != null) center(lastLocation, false);
                });
            } catch (Throwable t) {
                fail("Renderer: " + t.getClass().getSimpleName());
            }
        });
    }

    String style() { return style; }
    void beginPageGesture() { followingBeforeGesture = following; }
    void restorePageGesture() { following = followingBeforeGesture; }
    boolean ready() { return enabled && loaded && !failed; }
    boolean failed() { return failed; }

    void setEnabled(boolean value) {
        if (destroyed || enabled == value) return;
        enabled = value;
        if (!value) {
            cancelTimeout();
            if (resumed) { view.onPause(); resumed = false; }
            if (started) { view.onStop(); started = false; }
            view.setVisibility(View.GONE);
            marker.setVisibility(View.GONE);
            gps.setVisibility(View.GONE);
        } else {
            // Keep Mapsforge visible while MapLibre loads its first complete frame.
            view.setVisibility(View.VISIBLE);
            scheduleTimeout();
        }
    }

    void setVisible(boolean show) {
        if (show && !foreground) {
            view.setVisibility(View.VISIBLE);
            view.bringToFront();
            marker.bringToFront();
            gps.bringToFront();
        } else if (!show && foreground) {
            // Keep the loading renderer attached behind the working raster map.
            surface.removeView(view);
            surface.addView(view, 0, new FrameLayout.LayoutParams(-1, -1));
        }
        foreground = show;
        if (!show && (!enabled || failed)) view.setVisibility(View.GONE);
        gps.setVisibility(show ? View.VISIBLE : View.GONE);
        marker.setVisibility(show && following && lastLocation != null ? View.VISIBLE : View.GONE);
    }

    void start() {
        if (enabled && !started) { view.onStart(); started = true; }
        scheduleTimeout();
    }
    void resume() {
        start();
        if (enabled && !resumed) { view.onResume(); resumed = true; }
    }
    void pause() {
        cancelTimeout();
        if (resumed) { view.onPause(); resumed = false; }
    }
    void stop() {
        pause();
        cancelTimeout();
        if (started) { view.onStop(); started = false; }
    }
    void lowMemory() { if (!destroyed) view.onLowMemory(); }

    void acceptLocation(Location location) {
        if (destroyed || location == null) return;
        course.update(location);
        lastLocation = new Location(location);
        if (!following || !ready() || map == null) return;
        long now = SystemClock.elapsedRealtime();
        boolean animate = lastFixMs > 0 && now - lastFixMs < 5_000L;
        lastFixMs = now;
        center(location, animate);
    }

    void applyHeadingMode() {
        if (map == null || destroyed) return;
        double wanted = MapPreferences.headingUp(context) && course.known() ? course.bearing() : 0d;
        int zoom = wantedZoom();
        if (Math.abs(((wanted - map.getCameraPosition().bearing + 540d) % 360d) - 180d) < 0.5d
                && Math.abs(map.getCameraPosition().zoom - zoom) < 0.1d) return;
        map.easeCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(map.getCameraPosition()).bearing(wanted).zoom(zoom).build()), 550, false);
    }

    void acceptArea(SettlementZoomAnalyzer.Area nextArea) {
        area = nextArea;
        if (ready() && following && MapPreferences.autoZoom(context)) applySavedZoom();
    }

    void applySavedZoom() {
        if (map == null || destroyed) return;
        int zoom = wantedZoom();
        if (Math.abs(map.getCameraPosition().zoom - zoom) > 0.1)
            map.animateCamera(CameraUpdateFactory.zoomTo(zoom), 800);
    }

    private int wantedZoom() {
        int zoom = MapPreferences.zoom(context);
        if (MapPreferences.autoZoom(context) && area != null) {
            if (area == SettlementZoomAnalyzer.Area.SETTLEMENT)
                zoom = MapPreferences.settlementZoom(context);
            else if (area == SettlementZoomAnalyzer.Area.OUTSIDE)
                zoom = MapPreferences.outsideZoom(context);
        }
        return following ? MapPreferences.clampFollowZoom(zoom) : zoom;
    }

    private void center(Location location, boolean animate) {
        if (map == null || !following) return;
        LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
        double bearing = MapPreferences.headingUp(context) && course.known() ? course.bearing() : 0d;
        // Keep target, heading and follow zoom in one atomic camera update. A road-matched
        // position arrives asynchronously and must not cancel an in-progress zoom animation
        // while preserving MapLibre's initial country-scale zoom.
        CameraPosition next = new CameraPosition.Builder(map.getCameraPosition())
                .target(target).bearing(bearing).zoom(wantedZoom()).build();
        // Same smooth transition as before the forward-projection experiment.
        if (animate) map.easeCamera(CameraUpdateFactory.newCameraPosition(next), 950, false);
        else map.moveCamera(CameraUpdateFactory.newCameraPosition(next));
        marker.setVisibility(ready() && view.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void fail(String detail) {
        if (destroyed || failed) return;
        DiagLog.add("OpenFreeMap: " + detail + " – Offline-Karte wird angezeigt, falls vorhanden");
        failed = true;
        loaded = false;
        cancelTimeout();
        surface.post(stateChanged);
    }

    private void scheduleTimeout() {
        if (!destroyed && enabled && started && !loaded && !failed && timeoutGate.arm())
            surface.postDelayed(timeout, LOAD_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        surface.removeCallbacks(timeout);
        timeoutGate.clear();
    }

    void destroy() {
        destroyed = true;
        cancelTimeout();
        stop();
        view.onDestroy();
        surface.removeView(view);
        surface.removeView(marker);
        surface.removeView(gps);
    }
    private int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
}
