package de.ronny.pololauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Rotation;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.util.function.Consumer;

/** Installed Mapsforge map and shared GPS feed for the MapLibre online renderer. */
final class OfflineMapPanel {
    private final Context context;
    private final FrameLayout surface;
    private final MapView mapView;
    private final MapDataStore mapData;
    private final SettlementZoomAnalyzer settlementZoom;
    // Keep the weakly referenced callbacks alive exactly as long as this panel lives.
    // The self-reference through these lambdas is collectable once the panel is gone.
    private final SettlementZoomAnalyzer.Listener settlementListener;
    private final RoadPositionMatcher roadMatcher;
    private final RoadPositionMatcher.Listener roadPositionListener;
    private final TileRendererLayer offlineTiles;
    private final TextView unavailable;
    private final LocationManager locationManager;
    private final LocationListener listener;
    private final View marker;
    private boolean following = true;
    private boolean followingBeforeGesture = true;
    private boolean running;
    private boolean persistZoom = true;
    private boolean visible = true;
    private ValueAnimator positionAnimator;
    private ValueAnimator rotationAnimator;
    private float currentRotation;
    private long lastRotationFrameMs;
    private final TravelBearingTracker course = new TravelBearingTracker();
    private Location lastLocation;
    private Location lastCameraLocation;
    private final GpsFollowFilter gpsFollowFilter = new GpsFollowFilter();
    private long lastGpsReceivedMs;
    private long lastMoveAtMs;
    private Runnable zoomStep;
    private Consumer<Location> locationConsumer;
    private Consumer<SettlementZoomAnalyzer.Area> areaConsumer;
    private final TextView recenter;

    OfflineMapPanel(Context context, FrameLayout surface, File file) {
        this.context = context;
        this.surface = surface;
        AndroidGraphicFactory.createInstance(context.getApplicationContext());
        mapData = file == null ? null : new MapFile(file);
        settlementListener = area -> {
            if (visible && following && MapPreferences.autoZoom(context)) changeZoom(targetZoom(area));
            if (areaConsumer != null) areaConsumer.accept(area);
        };
        SettlementZoomAnalyzer analyzer = null;
        if (file != null) {
            try {
                analyzer = new SettlementZoomAnalyzer(file, surface, settlementListener);
            } catch (RuntimeException e) {
                DiagLog.add("Ortszoom nicht verfügbar: " + e.getClass().getSimpleName());
            }
        }
        settlementZoom = analyzer;
        roadPositionListener = (source, display, snapped) ->
                deliverCameraPosition(MapPreferences.roadSnap(context) ? display : source);
        RoadPositionMatcher matcher = null;
        if (file != null) {
            try {
                matcher = new RoadPositionMatcher(file, surface, roadPositionListener);
            } catch (RuntimeException e) {
                DiagLog.add("Straßenkorrektur nicht verfügbar: " + e.getClass().getSimpleName());
            }
        }
        roadMatcher = matcher;
        mapView = new MapView(context);
        mapView.setBuiltInZoomControls(false);
        mapView.setZoomLevelMin((byte) 5);
        mapView.setZoomLevelMax((byte) 19);
        // Leave room around the frame for rotated corners, as in Mapsforge's rotation sample.
        mapView.getModel().frameBufferModel.setOverdrawFactor(1.5);
        TileCache offlineCache = AndroidUtil.createTileCache(context, "offline_tiles",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
        if (mapData != null) {
            offlineTiles = new TileRendererLayer(offlineCache, mapData,
                    mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            offlineTiles.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);
            mapView.getLayerManager().getLayers().add(offlineTiles);
            mapView.setCenter(mapData.startPosition());
        } else {
            offlineTiles = null;
            mapView.setCenter(new LatLong(51.0, 10.0));
        }
        // Following starts enabled. Apply its safety limit before the first GPS fix too,
        // so the map file's initial position can never appear at country scale.
        mapView.setZoomLevel((byte) MapPreferences.clampFollowZoom(MapPreferences.zoom(context)));
        surface.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        unavailable = new TextView(context);
        unavailable.setText("Online-Karte lädt oder ist nicht erreichbar\nKeine Offline-Karte gewählt – unter Einstellungen herunterladen");
        unavailable.setTextSize(16);
        unavailable.setTextColor(CopperSkin.CREAM);
        unavailable.setGravity(Gravity.CENTER);
        unavailable.setBackgroundColor(Color.rgb(40, 32, 28));
        unavailable.setVisibility(file == null ? View.VISIBLE : View.GONE);
        surface.addView(unavailable, new FrameLayout.LayoutParams(-1, -1));

        marker = new View(context);
        GradientDrawable spot = new GradientDrawable();
        spot.setShape(GradientDrawable.OVAL);
        spot.setColor(Color.rgb(31, 139, 247));
        spot.setStroke(dp(3), Color.WHITE);
        marker.setBackground(spot);
        marker.setElevation(dp(6));
        marker.setVisibility(View.GONE);
        surface.addView(marker, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        recenter = new TextView(context);
        recenter.setText("◎  GPS");
        recenter.setTextSize(13);
        recenter.setTextColor(CopperSkin.CREAM);
        recenter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recenter.setGravity(Gravity.CENTER);
        recenter.setBackground(CopperSkin.touch(context, false));
        FrameLayout.LayoutParams control = new FrameLayout.LayoutParams(dp(76), dp(37), Gravity.BOTTOM | Gravity.RIGHT);
        control.setMargins(0, 0, dp(12), dp(12));
        surface.addView(recenter, control);
        recenter.setOnClickListener(v -> {
            following = true;
            applySavedZoom();
            applyHeadingMode();
            showLastPosition();
        });

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { acceptLocation(location); }
        };
        // A tap or a page swipe must not disable GPS following. Only an actual
        // map pan or a pinch gesture should pause following until GPS is tapped.
        final float[] touchDown = new float[2];
        final int panSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mapView.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchDown[0] = e.getX();
                    touchDown[1] = e.getY();
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (Math.hypot(e.getX() - touchDown[0], e.getY() - touchDown[1]) > panSlop) {
                        following = false;
                        cancelPositionAnimation();
                        cancelRotationAnimation();
                        cancelZoomStep();
                    }
                    break;
                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    following = false;
                    cancelPositionAnimation();
                    cancelRotationAnimation();
                    cancelZoomStep();
                    break;
            }
            return false;
        });
    }

    void setLocationConsumer(Consumer<Location> consumer) { locationConsumer = consumer; }
    void beginPageGesture() { followingBeforeGesture = following; }
    void restorePageGesture() { following = followingBeforeGesture; }
    void setAreaConsumer(Consumer<SettlementZoomAnalyzer.Area> consumer) { areaConsumer = consumer; }
    Location currentLocation() {
        Location location = lastCameraLocation != null ? lastCameraLocation : lastLocation;
        return location == null ? null : new Location(location);
    }
    SettlementZoomAnalyzer.Area currentArea() { return settlementZoom == null ? null : settlementZoom.current(); }
    void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        mapView.setVisibility(visible ? View.VISIBLE : View.GONE);
        unavailable.setVisibility(visible && offlineTiles == null ? View.VISIBLE : View.GONE);
        recenter.setVisibility(visible && offlineTiles != null ? View.VISIBLE : View.GONE);
        marker.setVisibility(visible && following && lastCameraLocation != null && offlineTiles != null
                ? View.VISIBLE : View.GONE);
        if (!visible) {
            cancelPositionAnimation();
            cancelRotationAnimation();
        } else {
            applySavedZoom();
            if (following && lastCameraLocation != null) moveTo(lastCameraLocation, false);
            applyHeadingMode();
        }
    }
    void setPersistZoom(boolean value) { persistZoom = value; }

    void start() {
        if (running || locationManager == null || !LocationAccess.any(context)) return;
        try {
            showLastPosition();
            // GPS needs the precise permission; with "approximate" only the network provider runs.
            if (LocationAccess.fine(context) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, listener);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0f, listener);
            running = true;
        } catch (SecurityException ignored) { /* Permission revoked while foregrounded. */ }
    }
    private void showLastPosition() {
        // Recenter an already accepted display fix directly. Resubmitting its raw
        // timestamp would correctly be rejected as a duplicate by acceptLocation.
        if (recent(lastCameraLocation, 15_000)) {
            deliverCameraPosition(lastCameraLocation);
            return;
        }
        if (locationManager == null) return;
        try {
            // Reprocess the latest raw fix, never a previously road-snapped display
            // position, otherwise repeated resumes could pull the stored GPS point.
            Location location = recent(lastLocation, 20_000) ? lastLocation : null;
            if (location == null) {
                Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                location = recent(gps, 15_000) ? gps : recent(network, 15_000) ? network : null;
            }
            if (location != null) {
                acceptLocation(location);
            }
        } catch (SecurityException ignored) {}
    }
    private static boolean recent(Location location, long limitMs) {
        if (location == null) return false;
        long ageMs;
        if (location.getElapsedRealtimeNanos() > 0)
            ageMs = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000L;
        else if (location.getTime() > 0)
            ageMs = System.currentTimeMillis() - location.getTime();
        else return false;
        return ageMs >= -1000L && ageMs < limitMs;
    }
    private void acceptLocation(Location location) {
        // Live callbacks without a usable timestamp are still valid on some OEM head units.
        if (location == null || (location.getElapsedRealtimeNanos() > 0 || location.getTime() > 0)
                && !recent(location, 15_000)) return;
        long now = SystemClock.elapsedRealtime();
        if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())
                && lastGpsReceivedMs > 0 && now - lastGpsReceivedMs < 8_000L) return;
        if (lastLocation != null && location.getElapsedRealtimeNanos() > 0
                && lastLocation.getElapsedRealtimeNanos() > 0
                && location.getElapsedRealtimeNanos() <= lastLocation.getElapsedRealtimeNanos()) return;
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) lastGpsReceivedMs = now;
        lastLocation = new Location(location);
        Location cameraFix = gpsFollowFilter.update(location);
        if (cameraFix == null) return;
        course.update(cameraFix);
        if (MapPreferences.roadSnap(context) && roadMatcher != null) roadMatcher.accept(cameraFix);
        else {
            if (roadMatcher != null) roadMatcher.cancelPending();
            deliverCameraPosition(cameraFix);
        }
        if (following && MapPreferences.autoZoom(context) && settlementZoom != null)
            settlementZoom.accept(location);
    }

    /**
     * Shared, already filtered GPS input while MapLibre is visible. The hidden renderer
     * stays inactive, but road matching and settlement detection continue. Do not pass
     * this through {@link #acceptLocation(Location)}: FallbackLocationFeed has already
     * applied the same movement filter and filtering twice noticeably delays the camera.
     */
    void acceptExternalLocation(Location cameraFix) {
        if (cameraFix == null) return;
        lastLocation = new Location(cameraFix);
        course.update(cameraFix);
        if (MapPreferences.roadSnap(context) && roadMatcher != null) roadMatcher.accept(cameraFix);
        else {
            if (roadMatcher != null) roadMatcher.cancelPending();
            deliverCameraPosition(cameraFix);
        }
        if (following && MapPreferences.autoZoom(context) && settlementZoom != null)
            settlementZoom.accept(cameraFix);
    }

    private void deliverCameraPosition(Location location) {
        lastCameraLocation = new Location(location);
        if (locationConsumer != null) locationConsumer.accept(new Location(location));
        if (visible) {
            // An asynchronous road match may return after a lifecycle/map transition.
            // Never let that first camera update remain at the map file's country-scale start.
            int currentZoom = mapView.getModel().mapViewPosition.getZoomLevel();
            if (following && currentZoom < MapPreferences.FOLLOW_MIN_ZOOM)
                mapView.setZoomLevel((byte) targetZoom(
                        settlementZoom == null ? null : settlementZoom.current()));
            moveTo(location, true);
            if (following) applyHeadingMode();
        }
    }
    private void moveTo(Location location, boolean animate) {
        if (!following || (mapData == null
                || !mapData.boundingBox().contains(new LatLong(location.getLatitude(), location.getLongitude())))) {
            cancelPositionAnimation();
            marker.setVisibility(View.GONE);
            return;
        }
        LatLong start = mapView.getModel().mapViewPosition.getCenter();
        LatLong target = new LatLong(location.getLatitude(), location.getLongitude());
        float[] distance = new float[1];
        Location.distanceBetween(start.latitude, start.longitude, target.latitude, target.longitude, distance);
        long now = SystemClock.elapsedRealtime();
        long interval = lastMoveAtMs == 0 ? 0 : now - lastMoveAtMs;
        lastMoveAtMs = now;
        cancelPositionAnimation();
        // Ignore sub-two-metre GPS jitter instead of snapping the map a few pixels.
        if (animate && distance[0] < 2f) {
            marker.setVisibility(mapView.getVisibility() == View.VISIBLE
                    ? View.VISIBLE : View.GONE);
            return;
        }
        // The first/very distant fix snaps; regular nearby fixes glide linearly.
        // Long gaps also snap instead of animating a stale location across town.
        if (animate && interval > 0 && interval < 5_000L && distance[0] <= 250f) {
            final double dLat = target.latitude - start.latitude;
            double dLon = target.longitude - start.longitude;
            if (dLon > 180d) dLon -= 360d;
            if (dLon < -180d) dLon += 360d;
            final double longitudeStep = dLon;
            positionAnimator = ValueAnimator.ofFloat(0f, 1f);
            positionAnimator.setInterpolator(new LinearInterpolator());
            // Restore the pre-latency-compensation glide: avoid a long stop between GPS fixes.
            positionAnimator.setDuration(Math.max(450L, Math.min(2_200L, Math.round(interval * 0.85f))));
            positionAnimator.addUpdateListener(animation -> {
                if (!following) return;
                float progress = (Float) animation.getAnimatedValue();
                double longitude = start.longitude + longitudeStep * progress;
                if (longitude > 180d) longitude -= 360d;
                if (longitude < -180d) longitude += 360d;
                mapView.setCenter(new LatLong(start.latitude + dLat * progress, longitude));
            });
            positionAnimator.start();
        } else {
            mapView.setCenter(target);
        }
        marker.setVisibility(mapView.getVisibility() == View.VISIBLE
                ? View.VISIBLE : View.GONE);
    }
    private void cancelPositionAnimation() {
        if (positionAnimator != null) {
            positionAnimator.cancel();
            positionAnimator = null;
        }
    }
    void stop() {
        // The automatically selected zoom must never replace the user's base setting.
        if (persistZoom && (!MapPreferences.autoZoom(context) || settlementZoom == null))
            MapPreferences.setZoom(context, mapView.getModel().mapViewPosition.getZoomLevel());
        cancelZoomStep();
        cancelPositionAnimation();
        cancelRotationAnimation();
        lastMoveAtMs = 0;
        if (locationManager != null && running) {
            try { locationManager.removeUpdates(listener); }
            catch (SecurityException ignored) { /* Permission revoked during standby. */ }
        }
        running = false;
    }
    void applySavedZoom() {
        if (!visible) return;
        cancelZoomStep();
        int requested = MapPreferences.autoZoom(context) && settlementZoom != null && following
                ? targetZoom(settlementZoom.current()) : MapPreferences.zoom(context);
        int wanted = following ? MapPreferences.clampFollowZoom(requested) : requested;
        if (mapView.getModel().mapViewPosition.getZoomLevel() != wanted)
            mapView.setZoomLevel((byte) wanted);
    }
    void applyHeadingMode() {
        if (!visible || offlineTiles == null) return;
        float wanted = (float) (MapPreferences.headingUp(context) && course.known() ? -course.bearing() : 0d);
        float delta = (float) TravelBearingTracker.shortest(currentRotation, wanted);
        if (Math.abs(delta) < 0.8f) return;
        cancelRotationAnimation();
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) return;
        if (Math.abs(delta) > 110f || mapView.getVisibility() != View.VISIBLE) {
            setRotation(wanted);
            return;
        }
        final float start = currentRotation;
        rotationAnimator = ValueAnimator.ofFloat(0f, 1f);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setDuration(700L);
        rotationAnimator.addUpdateListener(animation -> {
            long now = SystemClock.elapsedRealtime();
            if (animation.getAnimatedFraction() < 1f && now - lastRotationFrameMs < 80L) return;
            lastRotationFrameMs = now;
            setRotation((float) TravelBearingTracker.normalize(start + delta * (float) animation.getAnimatedValue()));
        });
        rotationAnimator.start();
    }
    private void setRotation(float angle) {
        currentRotation = angle;
        mapView.rotate(new Rotation(angle, mapView.getWidth() * 0.5f, mapView.getHeight() * 0.5f));
        mapView.getLayerManager().redrawLayers();
    }
    private void cancelRotationAnimation() {
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
    }
    private int targetZoom(SettlementZoomAnalyzer.Area area) {
        if (area == SettlementZoomAnalyzer.Area.SETTLEMENT)
            return MapPreferences.settlementZoom(context);
        if (area == SettlementZoomAnalyzer.Area.OUTSIDE)
            return MapPreferences.outsideZoom(context);
        // Until the offline analyzer has a confirmed result, keep the saved
        // base zoom but always enforce the GPS-follow safety limit.
        return MapPreferences.clampFollowZoom(MapPreferences.zoom(context));
    }
    private void cancelZoomStep() {
        if (zoomStep != null) surface.removeCallbacks(zoomStep);
        zoomStep = null;
    }
    private void changeZoom(int wanted) {
        cancelZoomStep();
        zoomStep = new Runnable() {
            @Override public void run() {
                if (!following || !MapPreferences.autoZoom(context)) return;
                int current = mapView.getModel().mapViewPosition.getZoomLevel();
                if (current == wanted) { zoomStep = null; return; }
                mapView.setZoomLevel((byte) (current + Integer.signum(wanted - current)));
                surface.postDelayed(this, 950L);
            }
        };
        surface.post(zoomStep);
    }
    void destroy() {
        stop();
        if (settlementZoom != null) settlementZoom.close();
        if (roadMatcher != null) roadMatcher.close();
        mapView.destroyAll();
        // The active TileRendererLayer owns and closes the MapFile; otherwise close it ourselves.
        if (mapData != null && offlineTiles == null) mapData.close();
        surface.removeView(mapView);
    }
    private int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
}
