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

        root.addView(SettingsUi.title(this, "OBD2-ADAPTER"), new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout connection = SettingsUi.card(this, root, "VERBINDUNG");
        status = SettingsUi.text(this, "", 16, GlassSkin.TEXT, true);
        connection.addView(status, new LinearLayout.LayoutParams(-1, -2));
        SettingsUi.button(this, connection, "Bluetooth-Berechtigung erteilen", this::requestBluetoothPermission);

        LinearLayout devices = SettingsUi.card(this, root, "GEKOPPELTE GERÄTE");
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        devices.addView(deviceList);
        SettingsUi.button(this, devices, "Liste aktualisieren", this::loadPairedDevices);
        SettingsUi.hint(this, devices, "Den ELM327 zuerst in Android koppeln (PIN meist 1234 oder 0000), dann hier antippen. "
                + "Bei einer Unterbrechung verbindet sich der Hintergrunddienst automatisch neu.");

        LinearLayout consumption = SettingsUi.card(this, root, "VERBRAUCH");
        SettingsUi.toggle(this, consumption, "Schubabschaltung schätzen", "Im Schub wird kein Kraftstoff gezählt",
                () -> Obd2Client.dfcoEnabled(this), on -> Obd2Client.setDfcoEnabled(this, on));
        SettingsUi.hint(this, consumption, "Der Verbrauch wird aus Drehzahl, Geschwindigkeit, Saugrohrdruck und Ansauglufttemperatur geschätzt. "
                + "Ø 100 KM ist der gleitende Mittelwert der letzten 100 gefahrenen Kilometer und bleibt über Neustarts erhalten.");

        SettingsUi.button(this, root, "Zurück", this::finish);

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
            deviceList.addView(SettingsUi.text(this, "Bluetooth-Berechtigung fehlt (Knopf oben)", 15, GlassSkin.MUTED, false));
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            deviceList.addView(SettingsUi.text(this, "Kein Bluetooth auf diesem Gerät", 15, GlassSkin.MUTED, false));
            return;
        }
        if (!adapter.isEnabled()) {
            deviceList.addView(SettingsUi.text(this, "Bluetooth ist ausgeschaltet", 15, GlassSkin.MUTED, false));
            return;
        }
        Set<BluetoothDevice> bonded;
        try { bonded = adapter.getBondedDevices(); }
        catch (SecurityException noPermission) {
            deviceList.addView(SettingsUi.text(this, "Bluetooth-Berechtigung fehlt (Knopf oben)", 15, GlassSkin.MUTED, false));
            return;
        }
        if (bonded.isEmpty()) {
            deviceList.addView(SettingsUi.text(this, "Keine gekoppelten Geräte. Erst in Android-Bluetooth koppeln.", 15, GlassSkin.MUTED, false));
            return;
        }
        String current = Obd2Client.deviceAddress(this);
        for (BluetoothDevice device : bonded) {
            String name;
            String address = device.getAddress();
            try { name = device.getName(); } catch (SecurityException noPermission) { name = null; }
            if (name == null || name.isEmpty()) name = address;
            boolean selected = address.equals(current);
            TextView row = SettingsUi.text(this, (selected ? "✓ " : "") + name + "\n" + address, 16,
                    selected ? GlassSkin.GREEN : GlassSkin.TEXT, true);
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
