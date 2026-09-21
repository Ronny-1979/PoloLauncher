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
    private TextView calibration;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(CopperSkin.background());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(24));
        scroll.addView(root);

        root.addView(text("VERBRAUCH ÜBER OBD2", 25, true));
        status = text("", 16, false);
        root.addView(status);

        TextView permission = text("Bluetooth-Berechtigung erteilen", 18, true);
        permission.setPadding(dp(16), dp(12), dp(16), dp(12));
        permission.setBackground(CopperSkin.touch(this, false));
        permission.setOnClickListener(v -> requestBluetoothPermission());
        root.addView(permission, topMargin());

        root.addView(text("Gekoppelte Bluetooth-Geräte:", 18, true));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        root.addView(deviceList);

        TextView rescan = text("Liste aktualisieren", 17, true);
        rescan.setPadding(dp(16), dp(12), dp(16), dp(12));
        rescan.setBackground(CopperSkin.touch(this, false));
        rescan.setOnClickListener(v -> loadPairedDevices());
        root.addView(rescan, topMargin());

        root.addView(text("Der ELM327-Adapter muss vorher in den normalen Android-Bluetooth-"
                + "Einstellungen gekoppelt sein (PIN meist 1234 oder 0000). Danach hier aus der "
                + "Liste auswählen.\n\n"
                + "Dieses Fahrzeug (VW Polo V 1.2, Motor CGPB) hat laut Steuergerät keinen "
                + "Luftmassenmesser und keine OBD2-Tankstandsanzeige (per Adapter-Diagnose "
                + "bestätigt). Ausgelesen werden daher Drehzahl, Geschwindigkeit, Saugrohrdruck "
                + "(PID 0x0B) und Ansauglufttemperatur (PID 0x0F); daraus wird die Luftmasse und "
                + "damit der Verbrauch geschätzt (Speed-Density-Verfahren mit angenommenem "
                + "volumetrischen Wirkungsgrad – eine Schätzung, keine Messung wie bei einem "
                + "echten MAF-Sensor). Der Tankstand kommt weiterhin über CAN. Nur für Benziner "
                + "(feste Luftzahl 14,7:1) – für einen TDI wäre eine andere Berechnung nötig.\n\n"
                + "Der Ø-Verbrauch läuft streckengewichtet über Zündungszyklen und Neustarts "
                + "hinweg weiter (nicht nur die Anzeige – die zugrunde liegende Summe aus Litern "
                + "und Kilometern wird gespeichert und beim nächsten Start fortgesetzt), damit die "
                + "Restkilometer-Schätzung durchgehend mit echten Werten weiterrechnet. Für eine "
                + "frische Zahl unten zurücksetzen.\n\n"
                + "Die Verbindung läuft im selben Hintergrunddienst wie die CAN-Erfassung und "
                + "startet automatisch, sobald der Launcher oder die Diagnose geöffnet ist. Bricht "
                + "ein günstiger Adapter die Verbindung ab, verbindet die App nach kurzer Pause "
                + "selbst neu, statt die Verbindung im Sekundentakt zu erzwingen.", 15, false));

        TextView resetAverage = text("Ø-Verbrauch (OBD2) zurücksetzen", 18, true);
        resetAverage.setPadding(dp(16), dp(12), dp(16), dp(12));
        resetAverage.setBackground(CopperSkin.touch(this, false));
        resetAverage.setOnClickListener(v -> CopperSkin.dialog(this)
                .setTitle("Ø-Verbrauch zurücksetzen?")
                .setMessage("Die bisher über OBD2 gesammelte Strecke und Kraftstoffmenge für den "
                        + "Ø-Verbrauch werden gelöscht. Die Reichweitenschätzung (CAN-Lernwert unter "
                        + "Tank und Reichweite) ist davon nicht betroffen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Zurücksetzen", (dialog, which) ->
                        Obd2Runtime.resetAverageConsumption(this))
                .show());
        root.addView(resetAverage, topMargin());

        android.widget.Switch fuelCut = new android.widget.Switch(this);
        fuelCut.setText("Schubabschaltung schätzen (im Schub keinen Kraftstoff zählen)");
        fuelCut.setTextSize(16);
        fuelCut.setTextColor(CopperSkin.CREAM);
        fuelCut.setChecked(Obd2Client.dfcoEnabled(this));
        fuelCut.setOnCheckedChangeListener((button, enabled) -> Obd2Client.setDfcoEnabled(this, enabled));
        root.addView(fuelCut, topMargin());
        root.addView(text("Das Speed-Density-Verfahren sieht im Schub (hohe Drehzahl, niedriger Saugrohrdruck, "
                + "Fahrt) Luft und würde Kraftstoff zählen, obwohl die Einspritzung abgeschaltet ist. Die Schätzung "
                + "erkennt das über Schwellen für Drehzahl (ab 1300/min), Saugrohrdruck (bis 30 kPa) und Geschwindigkeit "
                + "(ab 10 km/h). Die Schwellen sind nicht am Auto verifiziert: Die Diagnose zeigt, wie viele Runden so "
                + "eingestuft wurden.\n\n"
                + "Modellkorrektur: Nach jedem bestätigten CAN-Lernabschnitt (mindestens 100 km und 5 L echter "
                + "Tankabnahme) vergleicht die App die vom OBD-Modell im selben Abschnitt gezählten Liter mit der "
                + "echten Tankabnahme und korrigiert den angenommenen volumetrischen Wirkungsgrad um ein Viertel der "
                + "Abweichung (Faktor 0,6 bis 1,5).", 15, false));
        calibration = text("", 16, false);
        root.addView(calibration);
        TextView resetCalibration = text("Modellkorrektur zurücksetzen", 18, true);
        resetCalibration.setPadding(dp(16), dp(12), dp(16), dp(12));
        resetCalibration.setBackground(CopperSkin.touch(this, false));
        resetCalibration.setOnClickListener(v -> CopperSkin.dialog(this)
                .setTitle("Modellkorrektur zurücksetzen?")
                .setMessage("Der gelernte Korrekturfaktor wird auf 1,000 gesetzt. Die gesammelte Strecke und der "
                        + "Ø-Verbrauch bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Zurücksetzen", (dialog, which) -> {
                    Obd2Runtime.resetCalibration(this);
                    refreshStatus();
                })
                .show());
        root.addView(resetCalibration, topMargin());

        TextView back = text("Zurück", 20, true);
        back.setPadding(dp(16), dp(14), dp(16), dp(14));
        back.setBackground(CopperSkin.touch(this, false));
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
        status.setTextColor(connected ? CopperSkin.GREEN : CopperSkin.CREAM);
        if (calibration != null)
            calibration.setText(String.format(Locale.GERMANY, "Modellkorrektur: Faktor %.3f (1,000 = unkorrigiert)",
                    Obd2Runtime.veScale(this)));
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
            row.setBackground(CopperSkin.touch(this, selected));
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
        view.setTextColor(bold ? CopperSkin.GOLD : CopperSkin.CREAM);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
