package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DiagnosticsActivity extends Activity {
    private static final int MAX_VISIBLE_LOG_LINES = 600;
    private TextView output;
    private boolean runtimeActive;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable delayedRefresh = this::refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackground(CopperSkin.background());

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Polo Launcher · Diagnose");
        title.setTextColor(CopperSkin.CREAM);
        title.setTextSize(23);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));
        addToolbarButton(toolbar, "NEU", this::refresh);
        addToolbarButton(toolbar, "KOPIEREN", this::copyDiagnosis);
        addToolbarButton(toolbar, "TEILEN", this::shareDiagnosis);
        addToolbarButton(toolbar, "ZURÜCK", this::finish);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(CopperSkin.CREAM);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        if (!runtimeActive) {
            VendorRuntime.acquire(getApplicationContext());
            runtimeActive = true;
        }
        // Give the vendor binder a moment to reconnect before showing live counters.
        handler.removeCallbacks(delayedRefresh);
        handler.postDelayed(delayedRefresh, 800L);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(delayedRefresh);
        if (runtimeActive) {
            VendorRuntime.release(getApplicationContext());
            runtimeActive = false;
        }
        super.onStop();
    }

    private void refresh() {
        StringBuilder b = new StringBuilder();
        try { b.append("POLO LAUNCHER v").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append("\n"); }
        catch (PackageManager.NameNotFoundException ignored) { b.append("POLO LAUNCHER\n"); }
        b.append("Zeit: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(new Date())).append("\n\n");
        b.append("Aktueller Standard-Launcher: ").append(defaultHomePackage()).append("\n\n");
        b.append("Offline-Karte: ").append(OfflineMapStore.selected(this)).append(" (Datei: ").append(OfflineMapStore.selectedFile(this) != null).append(")\n");
        b.append("Ortsabhängiger Zoom: ").append(MapPreferences.autoZoom(this) ? "AN" : "AUS")
                .append("  Grundzoom: ").append(MapPreferences.zoom(this))
                .append("  Innerorts: ").append(MapPreferences.settlementZoom(this))
                .append("  Außerorts: ").append(MapPreferences.outsideZoom(this)).append("\n");
        b.append("Karten: MapLibre/OpenFreeMap online, Mapsforge nur heruntergeladen offline")
                .append("  Stil: ").append(MapPreferences.vectorStyle(this)).append("\n");
        b.append("GPS-Glättung: IMMER AN  Fahrtrichtung oben: ")
                .append(MapPreferences.headingUp(this) ? "AN" : "AUS").append("\n");
        b.append("Straßenkorrektur (Test): ").append(MapPreferences.roadSnap(this) ? "AN" : "AUS").append("\n");
        b.append("Verbrauchs-Hintergrunddienst: ").append(RangeTrackingService.isRunning() ? "LÄUFT" : "NICHT AKTIV").append("\n");
        b.append("Kartendownload: ").append(OfflineMapStore.activeKey(this)).append("\n");
        b.append("Reichweitenmodus: ").append(RangeStore.includeObd2InRange(this) ? "CAN + OBD2" : "NUR CAN").append("\n");
        b.append("\n").append(Obd2Runtime.diagnostics(this));
        boolean bluetoothAllowed = android.os.Build.VERSION.SDK_INT < 31
                || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        b.append("OBD-Bluetooth-Berechtigung: ").append(bluetoothAllowed ? "ERLAUBT" : "FEHLT").append("\n");
        VehicleState obd = VehicleRepository.snapshot();
        b.append("OBD Momentanverbrauch: ").append(ConsumptionDisplay.instantaneous(obd)).append("\n");
        b.append("OBD Durchschnitt: ").append(num(obd.avgConsumption, 1)).append(" l/100 km\n");
        b.append("Aktuelle Reichweitenquelle: ").append(RangeStore.includeObd2InRange(this)
                ? (ConsumptionLearner.validRate(obd.avgConsumption) ? "CAN + OBD-Durchschnitt"
                : ConsumptionLearner.validRate(Obd2Runtime.rangeAverage(this)) ? "CAN + gespeicherter OBD-Durchschnitt"
                : "CAN + OBD / wartet auf OBD-Durchschnitt") : "CAN / Grundverbrauch").append("\n\n");
        b.append(String.format(Locale.GERMANY, "Reichweitenverbrauch: %.1f l/100 km (Automatik %s, Abschnitte %d)\n%s\n",
                RangeStore.consumption(this, VehicleRepository.snapshot()), RangeStore.automaticLearning(this) ? "AN" : "AUS",
                RangeStore.learnedWindows(this), RangeStore.learningStatus(this)));
        Intent appQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        b.append("Sichtbare startbare Apps: ").append(getPackageManager().queryIntentActivities(appQuery, 0).size()).append("\n\n");
        b.append("DABdream+ Paket: ").append(installed(DabNotificationListener.DAB_PACKAGE) ? "JA" : "NEIN").append("  [").append(DabNotificationListener.DAB_PACKAGE).append("]\n");
        b.append("Medienzugriff: ").append(notificationAccessGranted() ? "ERLAUBT" : "NICHT ERLAUBT").append("\n");
        b.append("DAB Titel: ").append(MediaInfo.title()).append("\n");
        b.append("DAB Zusatz: ").append(MediaInfo.subtitle()).append("\n");
        b.append("DAB spielt: ").append(MediaInfo.playing()).append("\n");
        b.append("DAB letzte Daten: ").append(MediaInfo.updatedAt() == 0 ? "nie" : new Date(MediaInfo.updatedAt())).append("\n\n");
        DabControls.Status controls = DabControls.status(this);
        b.append("DAB-Steuerung: Session=").append(controls.found)
                .append(" Zurück=").append(controls.previous)
                .append(" Play/Pause=").append(controls.toggle)
                .append(" Weiter=").append(controls.next)
                .append(" Aktionen=0x").append(Long.toHexString(controls.actions)).append("\n\n");
        appendActiveMediaSessions(b);

        b.append("HCT CANBUS Binder: ").append(HctCanbusBinderRuntime.isConnected() ? "VERBUNDEN" : "NICHT VERBUNDEN").append("\n");
        b.append("HCT Callback Frames: ").append(HctCanbusBinderRuntime.callbackFrameCount()).append("\n");
        b.append("HCT Funktionen: ").append(HctCanbusBinderRuntime.callbackSummary()).append("\n");
        b.append("OEM Poll: ").append(HctOemAutoPoller.summary()).append("\n");
        VehicleState s = VehicleRepository.snapshot();
        b.append(String.format(Locale.GERMANY, "Tank=%s L  Spannung=%s V  Außen=%s °C  Quelle=%s\n\n",
                num(s.fuelLiters, 1), num(s.voltage, 2), num(s.outsideC, 1), s.source));

        b.append("Wichtige Apps:\n");
        appendPackage(b, "Google Maps", "com.google.android.apps.maps");
        appendPackage(b, "Polo CAN", "de.ronny.polocan");
        appendPackage(b, "Polo CAN Debug", "de.ronny.polocan.debug");
        appendPackage(b, "ZLink", "com.zjinnova.zlink");
        appendPackage(b, "ZLink5", "com.zjinnova.zlink5");
        appendPackage(b, "SpeedPlay", "com.suding.speedplay");
        appendPackage(b, "AutoKit", "com.autokit");

        b.append("\nMögliche CarPlay-/Radio-Apps anhand Name/Paket:\n");
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            int found = 0;
            for (ApplicationInfo app : apps) {
                String label = String.valueOf(pm.getApplicationLabel(app));
                String hay = (label + " " + app.packageName).toLowerCase(Locale.ROOT);
                if (containsAny(hay, "dab", "radio", "zlink", "tlink", "autokit", "speedplay", "carplay", "carlink")) {
                    b.append("- ").append(label).append(" = ").append(app.packageName).append("\n");
                    found++;
                }
            }
            if (found == 0) b.append("- keine Treffer\n");
        } catch (Throwable t) { b.append("- Paketliste Fehler: ").append(t.getClass().getSimpleName()).append("\n"); }

        b.append("\nLetzte interne Meldungen (max. ")
                .append(MAX_VISIBLE_LOG_LINES).append("):\n")
                .append(DiagLog.text(MAX_VISIBLE_LOG_LINES));
        output.setText(b.toString());
    }

    private void copyDiagnosis() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Zwischenablage nicht verfügbar", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Polo Launcher Diagnose", output.getText()));
        Toast.makeText(this, "Diagnose wurde kopiert", Toast.LENGTH_SHORT).show();
    }

    private void shareDiagnosis() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Polo Launcher Diagnose");
        share.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
        try { startActivity(Intent.createChooser(share, "Diagnose teilen")); }
        catch (Throwable t) { Toast.makeText(this, "Keine App zum Teilen gefunden", Toast.LENGTH_SHORT).show(); }
    }

    private void addToolbarButton(LinearLayout toolbar, String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(CopperSkin.CREAM);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setBackground(CopperSkin.touch(this, false));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(86), dp(42));
        params.leftMargin = dp(5);
        toolbar.addView(button, params);
    }

    private void appendPackage(StringBuilder b, String label, String pkg) {
        b.append("- ").append(label).append(": ").append(installed(pkg) ? "JA" : "nein").append(" [").append(pkg).append("]\n");
    }
    private void appendActiveMediaSessions(StringBuilder b) {
        b.append("Aktive Android-MediaSessions:\n");
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName listener = new ComponentName(this, DabNotificationListener.class);
            List<MediaController> sessions = manager.getActiveSessions(listener);
            if (sessions.isEmpty()) b.append("- keine gefunden\n");
            for (MediaController controller : sessions) {
                MediaMetadata metadata = controller.getMetadata();
                PlaybackState state = controller.getPlaybackState();
                b.append("- Paket: ").append(controller.getPackageName()).append("\n")
                        .append("  Titel: ").append(mediaText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE)).append("\n")
                        .append("  Interpret: ").append(mediaText(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).append("\n")
                        .append("  Display-Untertitel: ").append(mediaText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).append("\n")
                        .append("  Album/Senderhinweis: ").append(mediaText(metadata, MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)).append("\n")
                        .append("  Bilder: display=").append(hasBitmap(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON))
                        .append(" art=").append(hasBitmap(metadata, MediaMetadata.METADATA_KEY_ART))
                        .append(" album=").append(hasBitmap(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART)).append("\n")
                        .append("  Status: ").append(state == null ? "?" : state.getState())
                        .append("  Aktionen: ").append(state == null ? "?" : "0x" + Long.toHexString(state.getActions())).append("\n");
            }
        } catch (SecurityException e) {
            b.append("- Zugriff fehlt: DAB-Medienzugriff in den Einstellungen erlauben\n");
        } catch (Throwable t) {
            b.append("- Fehler: ").append(t.getClass().getSimpleName()).append("\n");
        }
        b.append("\n");
    }
    private static String mediaText(MediaMetadata metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            String value = metadata.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
    private static boolean hasBitmap(MediaMetadata metadata, String key) {
        try { return metadata != null && metadata.getBitmap(key) != null; }
        catch (Throwable ignored) { return false; }
    }
    private String defaultHomePackage() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null ? info.activityInfo.packageName : "nicht ermittelbar";
        } catch (Throwable t) { return "Fehler: " + t.getClass().getSimpleName(); }
    }
    private boolean installed(String pkg) { try { getPackageManager().getApplicationInfo(pkg, 0); return true; } catch (Throwable t) { return false; } }
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
    private static boolean containsAny(String value, String... needles) { for (String n : needles) if (value.contains(n)) return true; return false; }
    private static String num(double value, int decimals) { return Double.isNaN(value) ? "--" : String.format(Locale.GERMANY, "%." + decimals + "f", value); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
