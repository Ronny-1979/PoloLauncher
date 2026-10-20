package de.ronny.pololauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

/**
 * Android 12+ lets the user grant only an approximate position. GPS_PROVIDER needs the FINE
 * permission (requesting it with COARSE only throws SecurityException), the network provider works
 * with either.
 */
final class LocationAccess {
    private LocationAccess() {}

    static boolean fine(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean any(Context context) {
        return fine(context)
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static Location lastKnownGps(Context context, LocationManager manager) {
        if (manager == null || !fine(context)) return null;
        try { return manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (SecurityException ignored) { return null; }
    }

    static Location lastKnownNetwork(Context context, LocationManager manager) {
        if (manager == null || !any(context)) return null;
        try { return manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }
        catch (SecurityException ignored) { return null; }
    }

    static boolean providerEnabled(LocationManager manager, String provider) {
        if (manager == null) return false;
        try { return manager.isProviderEnabled(provider); }
        catch (RuntimeException ignored) { return false; }
    }
}
