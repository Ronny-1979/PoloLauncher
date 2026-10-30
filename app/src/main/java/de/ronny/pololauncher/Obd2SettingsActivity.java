package de.ronny.pololauncher;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Set;

public final class Obd2SettingsActivity extends Activity {
    private LinearLayout deviceList;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(GlassSkin.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(24));
        scroll.addView(root);

        root.addView(text("OBD2-ADAPTER UND VERBRAUCH", 25, true));
        status = text("", 16, false);
        root.addView(status);

        TextView permission = text("Bluetooth-Berechtigung erteilen", 18, true);
        permission.setPadding(dp(16), dp(12), dp(16), dp(12));
        permission.setBackground(GlassSkin.touch(this, false));
        permission.setOnClickListener(v -> requestBluetoothPermission());
        root.addView(permission, topMargin());

        root.addView(text("Gekoppelte Bluetooth-Geräte:", 18, true));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        root.addView(deviceList);

        TextView rescan = text("Liste aktualisieren", 17, true);
        rescan.setPadding(dp(16), dp(12), dp(16), dp(12));
        rescan.setBackground(GlassSkin.touch(this, false));
        rescan.setOnClickListener(v -> loadPairedDevices());
        root.addView(rescan, topMargin());

        root.addView(text("Den ELM327 zuerst in Android koppeln (PIN meist 1234 oder 0000) und danach hier auswählen. "
                + "Der Verbrauch wird aus Drehzahl, Geschwindigkeit, Saugrohrdruck und Ansauglufttemperatur geschätzt. "
                + "Der sichtbare Durchschnitt (Ø 100 KM) ist der gleitende Mittelwert der letzten 100 gefahrenen Kilometer und bleibt über Neustarts erhalten. "
                + "Die Restreichweite verwendet dasselbe gleitende Fahrprofil. "
                + "Bei einer Unterbrechung verbindet sich der Hintergrunddienst automatisch neu.", 14, false));

        android.widget.Switch fuelCut = new android.widget.Switch(this);
        fuelCut.setText("Schubabschaltung schätzen (im Schub keinen Kraftstoff zählen)");
        fuelCut.setTextSize(16);
        fuelCut.setTextColor(GlassSkin.TEXT);
        fuelCut.setChecked(Obd2Client.dfcoEnabled(this));
        fuelCut.setOnCheckedChangeListener((button, enabled) -> Obd2Client.setDfcoEnabled(this, enabled));
        root.addView(fuelCut, topMargin());
        root.addView(text("Beim Rollen mit hoher Drehzahl, niedrigem Saugrohrdruck und mindestens 10 km/h kann die "
                + "Schubabschaltung als 0 l erkannt werden. Die Diagnose zeigt, wie oft diese Schätzung verwendet wurde.",
                14, false));

        TextView back = text("Zurück", 20, true);
        back.setPadding(dp(16), dp(14), dp(16), dp(14));
        back.setBackground(GlassSkin.touch(this, false));
        back.setOnClickListener(v -> finish());
        root.addView(back, topMargin());

        setContentView(scroll);
        loadPairedDevices();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 71) loadPairedDevices();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean connected = Obd2Runtime.isConnected();
        status.setText(String.format(Locale.GERMANY, "Status: %s", Obd2Runtime.status()));
        status.setTextColor(connected ? GlassSkin.GREEN : GlassSkin.TEXT);
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT < 31) {
            Toast.makeText(this, "Auf diesem Android nicht nötig", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasBluetoothPermission()) {
            Toast.makeText(this, "Berechtigung bereits erteilt", Toast.LENGTH_SHORT).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 71);
        }
    }

    private void loadPairedDevices() {
        deviceList.removeAllViews();
        if (!hasBluetoothPermission()) {
            deviceList.addView(text("Bluetooth-Berechtigung fehlt (Knopf oben)", 15, false));
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            deviceList.addView(text("Kein Bluetooth auf diesem Gerät", 15, false));
            return;
        }
        if (!adapter.isEnabled()) {
            deviceList.addView(text("Bluetooth ist ausgeschaltet", 15, false));
            return;
        }
        Set<BluetoothDevice> bonded;
        try { bonded = adapter.getBondedDevices(); }
        catch (SecurityException noPermission) {
            deviceList.addView(text("Bluetooth-Berechtigung fehlt (Knopf oben)", 15, false));
            return;
        }
        if (bonded.isEmpty()) {
            deviceList.addView(text("Keine gekoppelten Geräte. Erst in Android-Bluetooth koppeln.", 15, false));
            return;
        }
        String current = Obd2Client.deviceAddress(this);
        for (BluetoothDevice device : bonded) {
            String name;
            String address = device.getAddress();
            try { name = device.getName(); } catch (SecurityException noPermission) { name = null; }
            if (name == null || name.isEmpty()) name = address;
            boolean selected = address.equals(current);
            TextView row = text((selected ? "✓ " : "") + name + "\n" + address, 16, selected);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setBackground(GlassSkin.touch(this, selected));
            row.setOnClickListener(v -> {
                Obd2Client.setDeviceAddress(Obd2SettingsActivity.this, address);
                Toast.makeText(Obd2SettingsActivity.this, "OBD2-Adapter: " + address, Toast.LENGTH_SHORT).show();
                loadPairedDevices();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(6);
            deviceList.addView(row, params);
        }
    }

    private LinearLayout.LayoutParams topMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        return params;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(bold ? GlassSkin.ACCENT : GlassSkin.TEXT);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
