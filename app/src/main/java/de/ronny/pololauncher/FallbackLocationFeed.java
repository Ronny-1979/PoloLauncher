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
    private Consumer<Location> rawConsumer;
    private Location lastLocation;
    private long lastGpsReceivedMs;
    private boolean running;
    private long fixesSeen;

    FallbackLocationFeed(Context context) {
        this.context = context;
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { accept(location); }
        };
    }

    void setConsumer(Consumer<Location> value) {
        if ((consumer == null) != (value == null)) MapFollowDiagnostics.event("GPS-Empfänger " + (value == null ? "ENTFERNT" : "gesetzt"));
        consumer = value;
    }
    void setRawConsumer(Consumer<Location> value) { rawConsumer = value; }
    Location currentLocation() { return lastLocation == null ? null : new Location(lastLocation); }

    void start() {
        if (running || manager == null || !LocationAccess.any(context)) return;

        // Approximate-only permission must never make the GPS_PROVIDER exception abort the
        // NETWORK_PROVIDER fallback. Resolve and subscribe to each provider independently.
        Location gps = LocationAccess.lastKnownGps(context, manager);
        Location network = LocationAccess.lastKnownNetwork(context, manager);
        if (recent(gps, 15_000L)) accept(gps);
        else if (recent(network, 15_000L)) accept(network);

        if (LocationAccess.fine(context)
                && LocationAccess.providerEnabled(manager, LocationManager.GPS_PROVIDER)) {
            try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener); }
            catch (SecurityException ignored) {}
        }
        if (LocationAccess.providerEnabled(manager, LocationManager.NETWORK_PROVIDER)) {
            try { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, listener); }
            catch (SecurityException ignored) {}
        }
        running = true;
        MapFollowDiagnostics.feedRestart();
        MapFollowDiagnostics.event("GPS gestartet");
    }

    void stop() {
        if (running) MapFollowDiagnostics.event("GPS gestoppt");
        if (manager != null && running) {
            try { manager.removeUpdates(listener); } catch (SecurityException ignored) {}
        }
        running = false;
    }

    void destroy() { stop(); consumer = null; rawConsumer = null; }

    private void accept(Location location) {
        if (location == null || ((location.getElapsedRealtimeNanos() > 0L || location.getTime() > 0L)
                && !recent(location, 15_000L))) return;
        long now = SystemClock.elapsedRealtime();
        if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())
                && lastGpsReceivedMs > 0L && now - lastGpsReceivedMs < 8_000L) return;
        if (lastLocation != null && !newerThan(location, lastLocation)) return;
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) lastGpsReceivedMs = now;
        MapFollowDiagnostics.rawFix();
        if (++fixesSeen % 20 == 1) MapFollowDiagnostics.event("GPS-Fix #" + fixesSeen + (consumer == null ? " (ohne Empfänger)" : ""));
        Consumer<Location> raw = rawConsumer;
        if (raw != null) raw.accept(new Location(location));
        Location cameraFix = filter.update(location);
        if (cameraFix == null) return;
        lastLocation = new Location(cameraFix);
        MapFollowDiagnostics.cameraFix();
        Consumer<Location> target = consumer;
        if (target != null) target.accept(new Location(cameraFix));
        else MapFollowDiagnostics.noConsumer();
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
