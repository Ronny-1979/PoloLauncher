package de.ronny.pololauncher;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.SystemClock;

import java.util.function.Consumer;

/** GPS source used when Mapsforge itself could not be constructed. */
final class FallbackLocationFeed {
    private final Context context;
    private final LocationManager manager;
    private final GpsFollowFilter filter = new GpsFollowFilter();
    private final LocationListener listener;
    private Consumer<Location> consumer;
    private Location lastLocation;
    private long lastGpsReceivedMs;
    private boolean running;

    FallbackLocationFeed(Context context) {
        this.context = context;
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { accept(location); }
        };
    }

    void setConsumer(Consumer<Location> value) { consumer = value; }
    Location currentLocation() { return lastLocation == null ? null : new Location(lastLocation); }

    void start() {
        if (running || manager == null || !LocationAccess.any(context)) return;
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (recent(gps, 15_000L)) accept(gps);
            else if (recent(network, 15_000L)) accept(network);
            if (LocationAccess.fine(context) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener);
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, listener);
            running = true;
        } catch (SecurityException ignored) {}
    }

    void stop() {
        if (manager != null && running) {
            try { manager.removeUpdates(listener); } catch (SecurityException ignored) {}
        }
        running = false;
    }

    void destroy() { stop(); consumer = null; }

    private void accept(Location location) {
        if (location == null || ((location.getElapsedRealtimeNanos() > 0L || location.getTime() > 0L)
                && !recent(location, 15_000L))) return;
        long now = SystemClock.elapsedRealtime();
        if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())
                && lastGpsReceivedMs > 0L && now - lastGpsReceivedMs < 8_000L) return;
        if (lastLocation != null && !newerThan(location, lastLocation)) return;
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) lastGpsReceivedMs = now;
        Location cameraFix = filter.update(location);
        if (cameraFix == null) return;
        lastLocation = new Location(cameraFix);
        if (consumer != null) consumer.accept(new Location(cameraFix));
    }

    private static boolean newerThan(Location next, Location previous) {
        long nextElapsed = next.getElapsedRealtimeNanos();
        long previousElapsed = previous.getElapsedRealtimeNanos();
        if (nextElapsed > 0L && previousElapsed > 0L) return nextElapsed > previousElapsed;
        long nextWall = next.getTime();
        long previousWall = previous.getTime();
        return nextWall <= 0L || previousWall <= 0L || nextWall > previousWall;
    }

    private static boolean recent(Location location, long limitMs) {
        if (location == null) return false;
        long age;
        if (location.getElapsedRealtimeNanos() > 0L)
            age = (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000L;
        else if (location.getTime() > 0L) age = System.currentTimeMillis() - location.getTime();
        else return false;
        return age >= -1_000L && age < limitMs;
    }
}
