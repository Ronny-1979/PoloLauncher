package de.ronny.pololauncher;

import android.content.Context;

/** Vehicle-specific user settings (not sensor data). */
final class VehiclePreferences {
    private static final String NAME = "cockpit_vehicle";
    private static final String KEY_DOOR_FL_FAULT = "door_fl_fault";

    private VehiclePreferences() {}

    /**
     * The front-left door contact of this Polo is defective, so the dashboard ignores that bit.
     * Default on; switch it off in Einstellungen -> System & Diagnose once the contact is repaired.
     */
    static boolean doorFlFault(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_DOOR_FL_FAULT, true);
    }

    static void setDoorFlFault(Context context, boolean fault) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DOOR_FL_FAULT, fault).apply();
    }
}
