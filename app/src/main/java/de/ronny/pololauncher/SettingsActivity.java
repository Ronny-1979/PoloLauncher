package de.ronny.pololauncher;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_BACKGROUND_IMAGE = 73;
    private TextView homeStatus;
    private TextView mediaStatus;
    private TextView backgroundStatus;
    private TextView gpsStatus;
    private TextView notificationStatus;
    private TextView carPlayUsageStatus;
    private String page = "";
    private boolean backgroundChangeInFlight;
    private final ExecutorService backgroundIo = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "settings-background-io");
        thread.setDaemon(true);
        return thread;
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Polo Launcher Einstellungen");
        if (state != null) page = state.getString("settings_page", "");
        showPage(page);
    }

    private void showPage(String selected) {
        if ("Tank & Verbrauch".equals(selected) || "OBD-Verbindung".equals(selected))
            selected = "Tank, Verbrauch & OBD";
        page = selected;
        homeStatus = mediaStatus = gpsStatus = notificationStatus = backgroundStatus = carPlayUsageStatus = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(14));
        scroll.addView(root);

        root.addView(SettingsUi.title(this, page.isEmpty() ? "EINSTELLUNGEN" : page.toUpperCase(java.util.Locale.GERMANY)),
                new LinearLayout.LayoutParams(-1, dp(46)));
        if (page.isEmpty()) {
            addCategory(root, "Karte & GPS", "Kartenstil · Zoom · Fahrtrichtung · Offline-Karten");
            addCategory(root, "Tank, Verbrauch & OBD", "Reichweite · Reserve · Adapter · Tankstellen-Preise");
            addCategory(root, "Darstellung & Medien", "Hintergrund · DABdream+ · CarPlay");
            addCategory(root, "System & Diagnose", "Standard-Launcher · Berechtigungen · Logs · Version");
        } else if (page.equals("Karte & GPS")) {
            LinearLayout gps = SettingsUi.card(this, root, "STANDORT");
            gpsStatus = addStatusButton(gps, this::requestGpsPermission);
            LinearLayout map = SettingsUi.card(this, root, "KARTE");
            addButton(map, "Kartenstil, Zoom und Fahrtrichtung",
                    () -> startActivity(new Intent(this, MapSettingsActivity.class)));
        } else if (page.equals("Tank, Verbrauch & OBD")) {
            LinearLayout car = SettingsUi.card(this, root, "FAHRZEUG");
            addButton(car, "Tank und Reichweite", () -> startActivity(new Intent(this, RangeSettingsActivity.class)));
            addButton(car, "OBD2-Adapter und Verbrauch", () -> startActivity(new Intent(this, Obd2SettingsActivity.class)));

            LinearLayout fuel = SettingsUi.card(this, root, "TANKSTELLEN-PREISE (E10)");
            TextView keyState = SettingsUi.text(this, FuelPriceStore.apiKey(this).isEmpty()
                    ? "⚠ Schlüssel fehlt" : "✓ Schlüssel gespeichert", 15,
                    FuelPriceStore.apiKey(this).isEmpty() ? GlassSkin.WARNING : GlassSkin.GREEN, true);
            fuel.addView(keyState, new LinearLayout.LayoutParams(-1, dp(32)));
            addButton(fuel, "Tankerkönig-Schlüssel eintragen", this::editFuelKey);
            SettingsUi.divider(this, fuel);
            SettingsUi.stepper(this, fuel, "Aktualisierung",
                    () -> FuelPriceStore.intervalMinutes(this), v -> FuelPriceStore.setIntervalMinutes(this, v),
                    5, 30, 5, v -> "alle " + v + " Minuten");
            SettingsUi.hint(this, fuel, "Zeigt E10 von MK-T (Marktkauf Oschatz) und HEM (Dahlen) unter der Fahrzeugkachel. "
                    + "Kostenloser Schlüssel: creativecommons.tankerkoenig.de. Preise: Tankerkönig / MTS-K (CC BY 4.0).");
        } else if (page.equals("Darstellung & Medien")) {
            LinearLayout background = SettingsUi.card(this, root, "HINTERGRUND");
            backgroundStatus = text("", 15, GlassSkin.MUTED, Typeface.BOLD);
            background.addView(backgroundStatus, new LinearLayout.LayoutParams(-1, dp(34)));
            addButton(background, "Eigenes Hintergrundbild auswählen", this::selectBackgroundImage);
            addButton(background, "Standard-Hintergrund verwenden", this::useDefaultBackground);
            SettingsUi.hint(this, background, "Das Bild wird in den Launcher kopiert und bleibt nach Neustarts erhalten. "
                    + "Der blaue Glass-Hintergrund steht immer zur Verfügung.");

            LinearLayout carplay = SettingsUi.card(this, root, "CARPLAY");
            SettingsUi.toggle(this, carplay, "Nach automatischer CarPlay-Verbindung zum Launcher zurück", null,
                    () -> CarPlayAutoReturn.enabled(this), enabled -> {
                        CarPlayAutoReturn.setEnabled(this, enabled);
                        if (enabled) {
                            RangeTrackingService.ensureStarted(this);
                            if (!CarPlayAutoReturn.hasUsageAccess(this)) {
                                Toast.makeText(this,
                                        "Bitte einmal Nutzungszugriff für Polo Launcher erlauben",
                                        Toast.LENGTH_LONG).show();
                                openUsageAccessSettings();
                            }
                        }
                        refreshStatus();
                    });
            SettingsUi.hint(this, carplay, "Öffnet sich CarPlay von selbst, wartet der Launcher 5 Sekunden auf den Verbindungsaufbau "
                    + "und holt danach einmal den Launcher zurück. Öffnest du CarPlay selbst, bleibt es im Vordergrund.");
            carPlayUsageStatus = addStatusButton(carplay, this::openUsageAccessSettings);

            LinearLayout dab = SettingsUi.card(this, root, "DABDREAM+ UND MEDIEN");
            SettingsUi.toggle(this, dab, "DABdream beim Launcher-Start öffnen und zurückkehren", null,
                    () -> getSharedPreferences("cockpit_media", MODE_PRIVATE)
                            .getBoolean("dab_start_and_return", false),
                    enabled -> getSharedPreferences("cockpit_media", MODE_PRIVATE).edit()
                            .putBoolean("dab_start_and_return", enabled).apply());
            SettingsUi.hint(this, dab, "Startet DABdream nach bestätigtem Internet einmal und kehrt nach 3 Sekunden zurück. "
                    + "Den eigenen Autostart in DABdream dafür ausschalten.");
            mediaStatus = addStatusButton(dab, this::openNotificationAccessSettings);
        } else if (page.equals("System & Diagnose")) {
            LinearLayout system = SettingsUi.card(this, root, "BERECHTIGUNGEN UND SYSTEM");
            homeStatus = addStatusButton(system, this::requestHomeRole);
            if (Build.VERSION.SDK_INT >= 33)
                notificationStatus = addStatusButton(system, this::requestNotificationPermission);
            addButton(system, "Android-Einstellungen", () -> AppLauncher.openAndroidSettings(this));

            LinearLayout vehicle = SettingsUi.card(this, root, "FAHRZEUG");
            SettingsUi.toggle(this, vehicle, "Tür vorn links: Kontakt defekt", "Die Türwarnung für diese Tür wird ausgeblendet",
                    () -> VehiclePreferences.doorFlFault(this), enabled -> VehiclePreferences.setDoorFlFault(this, enabled));

            LinearLayout info = SettingsUi.card(this, root, "HILFE UND INFO");
            addButton(info, "Polo-Launcher-Diagnose", () -> startActivity(new Intent(this, DiagnosticsActivity.class)));
            TextView version = text("Polo Launcher · Version " + appVersion(), 15, GlassSkin.ACCENT, Typeface.BOLD);
            version.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(version, new LinearLayout.LayoutParams(-1, dp(1)));
            root.removeView(version);
            info.addView(version, new LinearLayout.LayoutParams(-1, dp(40)));
            SettingsUi.hint(this, info, "Launcher3 bitte installiert lassen, bis alle Funktionen auf dem Radio geprüft sind.");
        }
        addButton(root, page.isEmpty() ? "Zurück zum Launcher" : "Zurück zu den Einstellungen",
                this::navigateBack);
        refreshStatus();
        FrameLayout host = new FrameLayout(this);
        host.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
        host.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
    }

    private void addCategory(LinearLayout root, String title, String description) {
        TextView card = addButton(root, title + "  ›\n" + description, () -> showPage(title));
        card.setTextSize(15);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setMinHeight(dp(72));
        card.setLayoutParams(new LinearLayout.LayoutParams(card.getLayoutParams().width, -2));
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = dp(5);
    }

    private void editFuelKey() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(FuelPriceStore.apiKey(this));
        input.setHint("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
        GlassSkin.dialog(this)
                .setTitle("Tankerkönig-API-Schlüssel")
                .setView(input)
                .setPositiveButton("Speichern", (d, w) -> {
                    FuelPriceStore.setApiKey(this, input.getText().toString());
                    Toast.makeText(this, "Gespeichert – Preise werden geladen", Toast.LENGTH_SHORT).show();
                    showPage(page);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void navigateBack() {
        if (page.isEmpty()) finish(); else showPage("");
    }

    @Override public void onBackPressed() {
        if (backgroundChangeInFlight) {
            Toast.makeText(this, "Hintergrund wird noch gespeichert – bitte kurz warten",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        navigateBack();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("settings_page", page);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
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
        if (backgroundStatus != null) {
            boolean custom = BackgroundPreferences.hasCustom(this);
            backgroundStatus.setText(custom ? "Hintergrund · Eigenes Bild" : "Hintergrund · Standard Glass");
            backgroundStatus.setTextColor(custom ? GlassSkin.ACCENT : GlassSkin.MUTED);
        }
        boolean usage = CarPlayAutoReturn.hasUsageAccess(this);
        updateStatus(carPlayUsageStatus, "CarPlay-Automatik – Nutzungszugriff", usage,
                usage ? "✓ Erlaubt" : "⚠ Einmal erlauben");

        boolean media = DabNotificationListener.accessGranted(this);
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
        card.setTextColor(good ? GlassSkin.GREEN : 0xffffa06b);
        card.setBackground(GlassSkin.touch(this, good));
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

    private void openUsageAccessSettings() {
        Intent direct = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (AppLauncher.startSafely(this, direct)) return;
        if (AppLauncher.startSafely(this, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) return;

        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        if (AppLauncher.startSafely(this, details)) {
            Toast.makeText(this,
                    "Nutzungszugriff ist auf diesem Radio eventuell unter Spezialzugriff zu finden",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Nutzungszugriff-Einstellungen nicht verfügbar",
                Toast.LENGTH_LONG).show();
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

    private void selectBackgroundImage() {
        if (backgroundChangeInFlight) {
            Toast.makeText(this, "Hintergrund wird gerade verarbeitet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(pick, REQUEST_BACKGROUND_IMAGE);
        } catch (Throwable error) {
            Toast.makeText(this, "Bildauswahl konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show();
        }
    }

    private void useDefaultBackground() {
        if (backgroundChangeInFlight) {
            Toast.makeText(this, "Hintergrund wird gerade verarbeitet", Toast.LENGTH_SHORT).show();
            return;
        }
        backgroundChangeInFlight = true;
        final android.content.Context app = getApplicationContext();
        final WeakReference<SettingsActivity> owner = new WeakReference<>(this);
        try {
            backgroundIo.execute(() -> {
                Throwable failure = null;
                try { BackgroundPreferences.useDefault(app); }
                catch (Throwable error) { failure = error; }
                final Throwable result = failure;
                SettingsActivity activity = owner.get();
                if (activity == null) return;
                activity.runOnUiThread(() -> activity.finishBackgroundChange(
                        result, "Standard-Hintergrund aktiviert"));
            });
        } catch (RejectedExecutionException busy) {
            backgroundChangeInFlight = false;
            Toast.makeText(this, "Hintergrundverarbeitung ist nicht verfügbar",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_BACKGROUND_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || backgroundChangeInFlight) return;

        backgroundChangeInFlight = true;
        final android.content.Context app = getApplicationContext();
        final WeakReference<SettingsActivity> owner = new WeakReference<>(this);
        try {
            backgroundIo.execute(() -> {
                Throwable failure = null;
                try { BackgroundPreferences.importFromUri(app, uri); }
                catch (Throwable error) { failure = error; }
                final Throwable result = failure;
                SettingsActivity activity = owner.get();
                if (activity == null) return;
                activity.runOnUiThread(() -> activity.finishBackgroundChange(result,
                        "Hintergrund gespeichert – bleibt auch nach Neustart erhalten"));
            });
        } catch (RejectedExecutionException busy) {
            backgroundChangeInFlight = false;
            Toast.makeText(this, "Hintergrundverarbeitung ist nicht verfügbar",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void finishBackgroundChange(Throwable failure, String successMessage) {
        backgroundChangeInFlight = false;
        if (isFinishing() || isDestroyed()) return;
        if (failure == null) {
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
            recreate();
            return;
        }
        DiagLog.add("Hintergrund konnte nicht gespeichert werden: "
                + failure.getClass().getSimpleName());
        Toast.makeText(this, "Hintergrundbild konnte nicht dauerhaft gespeichert werden",
                Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        // Let a transaction that already started finish its atomic temp/backup swap.
        backgroundIo.shutdown();
        super.onDestroy();
    }

    private TextView addStatusButton(LinearLayout root, Runnable action) {
        TextView card = text("", 14, GlassSkin.TEXT, Typeface.BOLD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(5), dp(14), dp(5));
        card.setElevation(dp(4));
        card.setOnClickListener(v -> action.run());
        card.setBackground(GlassSkin.touch(this, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(6);
        root.addView(card, params);
        return card;
    }

    private TextView addButton(LinearLayout root, String label, Runnable action) {
        TextView button = text(label, 16, GlassSkin.TEXT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(GlassSkin.touch(this, false));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(6);
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
