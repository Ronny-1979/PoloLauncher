package de.ronny.pololauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

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
}
