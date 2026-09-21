package de.ronny.pololauncher;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private TextView homeStatus;
    private TextView mediaStatus;
    private TextView gpsStatus;
    private TextView notificationStatus;
    private int shownSkin;
    private String page = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        shownSkin = CopperSkin.selection(this);
        setTitle("Polo Launcher Einstellungen");
        if (state != null) page = state.getString("settings_page", "");
        showPage(page);
    }

    private void showPage(String selected) {
        page = selected;
        homeStatus = mediaStatus = gpsStatus = notificationStatus = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(CopperSkin.background());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(24));
        scroll.addView(root);

        root.addView(text(page.isEmpty() ? "EINSTELLUNGEN" : page, 25, CopperSkin.CREAM, Typeface.BOLD),
                new LinearLayout.LayoutParams(-1, dp(48)));
        if (page.isEmpty()) {
            addCategory(root, "Karte & GPS", "Kartenstil · Zoom · Fahrtrichtung · Offline-Karten");
            addCategory(root, "Tank & Verbrauch", "Reichweitenmodus · Reserve · Lernwerte");
            addCategory(root, "OBD-Verbindung", "Adapter · Verbindungsstatus · OBD-Durchschnitt");
            addCategory(root, "Darstellung & Medien", "Skins · Farben · DAB-Medienzugriff");
            addCategory(root, "System & Diagnose", "Standard-Launcher · Berechtigungen · Logs · Version");
        } else if (page.equals("Karte & GPS")) {
        gpsStatus = addStatusButton(root, this::requestGpsPermission);
        addButton(root, "Kartenstil, Zoom und Fahrtrichtung",
                () -> startActivity(new Intent(this, MapSettingsActivity.class)));
        addButton(root, "Offline-Karten verwalten",
                () -> startActivity(new Intent(this, OfflineMapsActivity.class)));
        } else if (page.equals("Tank & Verbrauch")) {
        addButton(root, "Tank und Reichweite", () -> startActivity(new Intent(this, RangeSettingsActivity.class)));
        addButton(root, "OBD-Durchschnitt und Adapter", () -> startActivity(new Intent(this, Obd2SettingsActivity.class)));
        } else if (page.equals("OBD-Verbindung")) {
        addButton(root, "OBD2-Adapter und Durchschnitt", () -> startActivity(new Intent(this, Obd2SettingsActivity.class)));
        addButton(root, "Verbindungsdiagnose", () -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        } else if (page.equals("Darstellung & Medien")) {
        addButton(root, "Skins und Farben", () -> startActivity(new Intent(this, SkinSettingsActivity.class)));
        android.widget.Switch dabStartup = new android.widget.Switch(this);
        dabStartup.setText("DABdream beim Launcher-Start öffnen und zurückkehren");
        dabStartup.setTextSize(17);
        dabStartup.setTextColor(CopperSkin.CREAM);
        dabStartup.setPadding(dp(18), dp(12), dp(18), dp(12));
        dabStartup.setBackground(CopperSkin.panel(this, false));
        dabStartup.setChecked(getSharedPreferences("cockpit_media", MODE_PRIVATE)
                .getBoolean("dab_start_and_return", false));
        dabStartup.setOnCheckedChangeListener((button, enabled) ->
                getSharedPreferences("cockpit_media", MODE_PRIVATE).edit()
                        .putBoolean("dab_start_and_return", enabled).apply());
        LinearLayout.LayoutParams dabParams = new LinearLayout.LayoutParams(-1, -2);
        dabParams.topMargin = dp(12);
        root.addView(dabStartup, dabParams);
        TextView dabNote = text("Wartet immer auf von Android bestätigten Internetzugang und startet dann einmal pro Launcher-Prozess. "
                + "Ohne Internet bleibt der Launcher bedienbar. Nach 3 Sekunden wird der Launcher wieder geöffnet. "
                + "Autostart in DABdream deaktivieren; automatische Wiedergabe dort einstellen. "
                + "Manuelles Öffnen über den DAB-Button bleibt unverändert.", 14, CopperSkin.MUTED, Typeface.NORMAL);
        dabNote.setPadding(dp(4), dp(10), dp(4), dp(10));
        root.addView(dabNote);
        mediaStatus = addStatusButton(root,
                this::openNotificationAccessSettings);
        } else if (page.equals("System & Diagnose")) {
        section(root, "BERECHTIGUNGEN UND SYSTEM");
        homeStatus = addStatusButton(root, this::requestHomeRole);
        mediaStatus = addStatusButton(root,
                this::openNotificationAccessSettings);
        gpsStatus = addStatusButton(root, this::requestGpsPermission);
        if (Build.VERSION.SDK_INT >= 33)
            notificationStatus = addStatusButton(root, this::requestNotificationPermission);
        addButton(root, "Android-Einstellungen", () -> AppLauncher.openAndroidSettings(this));
        section(root, "FAHRZEUG UND DIAGNOSE");
        addSwitch(root, "Tür vorn links: Kontakt defekt (Warnung ausblenden)",
                VehiclePreferences.doorFlFault(this), (button, enabled) -> VehiclePreferences.setDoorFlFault(this, enabled));
        addSwitch(root, "Sniffer-Heuristik für unbekannte Broadcasts (nur Diagnose)",
                getSharedPreferences(SystemCanReceiver.PREFS_DEBUG, MODE_PRIVATE).getBoolean(SystemCanReceiver.KEY_SNIFFER, false),
                (button, enabled) -> getSharedPreferences(SystemCanReceiver.PREFS_DEBUG, MODE_PRIVATE).edit()
                        .putBoolean(SystemCanReceiver.KEY_SNIFFER, enabled).apply());
        TextView snifferNote = text("Aus (Standard): nur bestätigte HCT-Frames und bekannte Broadcasts ändern Fahrzeugwerte. "
                + "An: Schlüsselnamen unbekannter Broadcasts werden geraten und können Anzeigewerte überschreiben.",
                13, CopperSkin.MUTED, Typeface.NORMAL);
        snifferNote.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(snifferNote);
        section(root, "HILFE UND INFO");
        addButton(root, "Polo-Launcher-Diagnose", () -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        TextView version = text("Polo Launcher · Version " + appVersion(), 15, CopperSkin.GOLD, Typeface.BOLD);
        version.setGravity(Gravity.CENTER_VERTICAL);
        version.setPadding(dp(16), 0, dp(16), 0);
        version.setBackground(CopperSkin.panel(this, false));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, dp(46));
        versionParams.topMargin = dp(7);
        root.addView(version, versionParams);
        TextView note = text("Launcher3 bitte installiert lassen, bis alle Funktionen auf dem Radio geprüft sind.",
                13, CopperSkin.MUTED, Typeface.NORMAL);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);
        }
        addButton(root, page.isEmpty() ? "Zurück zum Launcher" : "Zurück zu den Einstellungen",
                this::navigateBack);
        refreshStatus();
        setContentView(scroll);
    }

    private void addCategory(LinearLayout root, String title, String description) {
        TextView card = addButton(root, title + "  ›\n" + description, () -> showPage(title));
        card.setTextSize(16);
        card.setPadding(dp(18), dp(10), dp(18), dp(10));
        card.setMinHeight(dp(68));
        card.setLayoutParams(new LinearLayout.LayoutParams(card.getLayoutParams().width, -2));
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = dp(8);
    }

    private void navigateBack() {
        if (page.isEmpty()) finish(); else showPage("");
    }

    @Override public void onBackPressed() { navigateBack(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("settings_page", page);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (shownSkin != CopperSkin.selection(this)) { recreate(); return; }
        refreshStatus();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 41 || requestCode == 42) refreshStatus();
    }

    private void refreshStatus() {
        boolean home = isDefaultHome();
        updateStatus(homeStatus, "Standard-Launcher", home,
                home ? "✓ Aktiv" : "⚠ Nicht als Standard festgelegt");
        boolean media = notificationAccessGranted();
        updateStatus(mediaStatus, "DAB-Medienzugriff", media, media ? "✓ Erlaubt" : "⚠ Zugriff fehlt");
        boolean gps = gpsGranted();
        updateStatus(gpsStatus, "GPS-Zugriff", gps,
                gps ? "✓ Erlaubt" : LocationAccess.any(this) ? "⚠ Nur ungefähr – „Genau“ erlauben" : "⚠ Zugriff fehlt");
        boolean notifications = notificationsGranted();
        updateStatus(notificationStatus, "Benachrichtigung (Hintergrunddienst)", notifications,
                notifications ? "✓ Erlaubt" : "⚠ Nicht erlaubt (Dienst läuft trotzdem)");
    }

    private void updateStatus(TextView card, String title, boolean good, String value) {
        if (card == null) return;
        card.setText(title + "\n" + value);
        card.setTextColor(good ? CopperSkin.GREEN : 0xffffa06b);
        card.setBackground(CopperSkin.touch(this, good));
        card.setContentDescription(title + ": " + value);
    }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) { return "unbekannt"; }
    }

    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME))
                    return roles.isRoleHeld(RoleManager.ROLE_HOME);
            } catch (Throwable ignored) {}
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                    && getPackageName().equals(resolved.activityInfo.packageName);
        } catch (Throwable ignored) { return false; }
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!roles.isRoleHeld(RoleManager.ROLE_HOME))
                        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 90);
                    else Toast.makeText(this, "Polo Launcher ist bereits Standard", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Throwable ignored) {}
        }
        if (!AppLauncher.startSafely(this, new Intent(Settings.ACTION_HOME_SETTINGS)))
            AppLauncher.openAndroidSettings(this);
    }

    /** Notification access screen, with fallbacks for firmware that removed it. */
    private void openNotificationAccessSettings() {
        if (AppLauncher.startSafely(this, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return;
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", getPackageName(), null));
        if (AppLauncher.startSafely(this, details)) {
            Toast.makeText(this, "Benachrichtigungszugriff dort unter Berechtigungen suchen", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Auf diesem Radio nicht verfügbar – Medienzugriff per ADB freigeben", Toast.LENGTH_LONG).show();
    }

    private boolean notificationAccessGranted() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName mine = new ComponentName(this, DabNotificationListener.class);
        for (String value : flat.split(":")) {
            ComponentName enabled = ComponentName.unflattenFromString(value);
            if (mine.equals(enabled)) return true;
        }
        return false;
    }

    /** GPS needs the PRECISE permission; an approximate-only grant does not count. */
    private boolean gpsGranted() {
        return LocationAccess.fine(this);
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted())
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        else Toast.makeText(this, "Benachrichtigungen sind bereits erlaubt", Toast.LENGTH_SHORT).show();
    }

    private void requestGpsPermission() {
        if (!gpsGranted()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 41);
        } else Toast.makeText(this, "GPS-Zugriff ist bereits erlaubt", Toast.LENGTH_SHORT).show();
    }

    private void addSwitch(LinearLayout root, String title, boolean checked,
                           android.widget.CompoundButton.OnCheckedChangeListener listener) {
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setText(title);
        toggle.setTextSize(16);
        toggle.setTextColor(CopperSkin.CREAM);
        toggle.setPadding(dp(18), dp(12), dp(18), dp(12));
        toggle.setBackground(CopperSkin.panel(this, false));
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        root.addView(toggle, params);
    }

    private TextView addStatusButton(LinearLayout root, Runnable action) {
        TextView card = text("", 16, CopperSkin.CREAM, Typeface.BOLD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(7), dp(18), dp(7));
        card.setElevation(dp(4));
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.topMargin = dp(7);
        root.addView(card, params);
        return card;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = text(title, 17, CopperSkin.GOLD, Typeface.BOLD);
        heading.setPadding(dp(2), dp(17), 0, dp(2));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(47)));
    }

    private TextView addButton(LinearLayout root, String label, Runnable action) {
        TextView button = text(label, 17, CopperSkin.CREAM, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(CopperSkin.touch(this, false));
        button.setElevation(dp(4));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(7);
        root.addView(button, params);
        return button;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
