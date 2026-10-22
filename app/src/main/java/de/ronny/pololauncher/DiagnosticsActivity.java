package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class DiagnosticsActivity extends Activity {
    private static final int MAX_VISIBLE_LOG_LINES = 600;
    private static final int REQUEST_EXPORT_DIAGNOSIS = 320;
    private TextView output;
    private boolean runtimeActive;
    private boolean exportInFlight;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService exportIo = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "diagnosis-export");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable delayedRefresh = this::refresh;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackground(GlassSkin.background(this));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Polo Launcher · Diagnose");
        title.setTextColor(GlassSkin.TEXT);
        title.setTextSize(23);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));
        addToolbarButton(toolbar, "NEU", this::refresh);
        addToolbarButton(toolbar, "KOPIEREN", this::copyDiagnosis);
        addToolbarButton(toolbar, "TEILEN", this::shareDiagnosis);
        addToolbarButton(toolbar, "DATEI", this::exportDiagnosis);
        addToolbarButton(toolbar, "ZURÜCK", this::finish);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(GlassSkin.TEXT);
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

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        exportIo.shutdown();
        super.onDestroy();
    }

    private int refreshSeq;

    /** Builds the report off the UI thread; the page opens at once and fills in a moment later. */
    private void refresh() {
        final int seq = ++refreshSeq;
        if (output.getText().length() == 0) output.setText("Diagnose wird erstellt …");
        try {
            exportIo.execute(() -> {
                String text;
                try { text = buildDiagnosis(); }
                catch (Throwable t) { text = "Diagnose fehlgeschlagen: " + t; }
                final String result = text;
                handler.post(() -> {
                    if (!isDestroyed() && seq == refreshSeq) output.setText(result);
                });
            });
        } catch (RejectedExecutionException ignored) {}
    }

    private String buildDiagnosis() {
        StringBuilder b = new StringBuilder();
        try { b.append("POLO LAUNCHER v").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append("\n"); }
        catch (PackageManager.NameNotFoundException ignored) { b.append("POLO LAUNCHER\n"); }
        b.append("Zeit: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(new Date())).append("\n\n");
        b.append("Letzter Java-Absturz: ").append(CrashReporter.summary(this)).append("\n");
        b.append("Letzte UI-Blockade: ").append(UiStallWatchdog.summary(this)).append("\n\n");
        b.append("Aktueller Standard-Launcher: ").append(defaultHomePackage()).append("\n\n");
        b.append(GermanyOfflineStore.diagnosticSummary(this)).append("\n");
        b.append("Ortsabhängiger Zoom: AN (fest)")
                .append("  Grundzoom: ").append(MapPreferences.zoom(this))
                .append("  Innerorts: ").append(MapPreferences.settlementZoom(this))
                .append("  Außerorts: ").append(MapPreferences.outsideZoom(this)).append("\n");
        b.append("Karte: MapLibre Deutschland offline")
                .append("  Neigung: ").append(MapPreferences.tilt(this)).append("°")
                .append("  Gebäudehöhe: ").append(MapPreferences.buildingHeight(this) == 0 ? "flach" : MapPreferences.buildingHeight(this) + " m").append("\n");
        String colorMode = MapPreferences.colorMode(this);
        b.append("Kartenmodus: ").append(MapPreferences.COLOR_DAY.equals(colorMode) ? "IMMER TAG"
                : MapPreferences.COLOR_NIGHT.equals(colorMode) ? "IMMER NACHT"
                : "AUTOMATISCH NACH SONNENSTAND/GPS").append("\n");
        b.append("GPS-Glättung: IMMER AN  Fahrtrichtung oben: ")
                .append(MapPreferences.headingUp(this) ? "AN" : "AUS").append("\n");
        b.append("Straßenkorrektur: fest an, Straßen aus den Offline-Kartenkacheln (sonst Verkehrsindex, sonst Roh-GPS)\n");
        b.append(SpeedLimitDiagnostics.summary()).append("\n");
        b.append(MapFollowDiagnostics.summary());
        b.append("Verbrauchs-Hintergrunddienst: ").append(RangeTrackingService.isRunning() ? "LÄUFT" : "NICHT AKTIV").append("\n");
        GermanyOfflineStore.Status germanyStatus = GermanyOfflineStore.status(this);
        b.append("Deutschland-Aufbau: ").append(germanyStatus.phase)
                .append(germanyStatus.steps > 0 ? " " + germanyStatus.step + "/" + germanyStatus.steps : "")
                .append(germanyStatus.detail == null || germanyStatus.detail.isEmpty() ? "" : " · " + germanyStatus.detail)
                .append("\n");
        b.append("Reichweitenberechnung: CAN + OBD2 (fest)\n");
        b.append("\n").append(Obd2Runtime.diagnostics(this));
        boolean bluetoothAllowed = android.os.Build.VERSION.SDK_INT < 31
                || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        b.append("OBD-Bluetooth-Berechtigung: ").append(bluetoothAllowed ? "ERLAUBT" : "FEHLT").append("\n");
        VehicleState obd = VehicleRepository.snapshot();
        b.append("OBD Momentanverbrauch: ").append(ConsumptionDisplay.instantaneous(obd)).append("\n");
        b.append("Aktuelle Reichweitenquelle: ").append(RangeStore.validConsumption(obd.rangeConsumption)
                ? "CAN + adaptives OBD-Fahrprofil"
                : RangeStore.validConsumption(Obd2Runtime.rangeAverage(this)) ? "CAN + gespeichertes OBD-Fahrprofil"
                : "CAN + OBD / wartet auf Fahrprofil").append("\n\n");
        double rangeConsumption = RangeStore.consumption(this, VehicleRepository.snapshot());
        b.append("Reichweitenverbrauch: ").append(num(rangeConsumption, 1))
                .append(" l/100 km\n");
        Intent appQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        b.append("Sichtbare startbare Apps: ").append(getPackageManager().queryIntentActivities(appQuery, 0).size()).append("\n\n");
        b.append("DABdream+ Paket: ").append(installed(DabNotificationListener.DAB_PACKAGE) ? "JA" : "NEIN").append("  [").append(DabNotificationListener.DAB_PACKAGE).append("]\n");
        b.append("Medienzugriff: ").append(DabNotificationListener.accessGranted(this) ? "ERLAUBT" : "NICHT ERLAUBT").append("\n");
        b.append("DAB Sender: ").append(MediaInfo.station()).append("\n");
        b.append("DAB Titel: ").append(MediaInfo.title()).append("\n");
        b.append("DAB Interpret: ").append(MediaInfo.artist()).append("\n");
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
        b.append("Datenalter: Drehzahl=").append(age(s.rpmUpdatedAtMs))
                .append(" Geschwindigkeit=").append(age(s.speedUpdatedAtMs))
                .append(" Tank=").append(age(s.fuelUpdatedAtMs))
                .append(" Spannung=").append(age(s.voltageUpdatedAtMs))
                .append(" Außen=").append(age(s.outsideUpdatedAtMs)).append("\n\n");

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
                if (AppLauncher.containsAny(hay, "dab", "radio", "zlink", "tlink", "autokit", "speedplay", "carplay", "carlink")) {
                    b.append("- ").append(label).append(" = ").append(app.packageName).append("\n");
                    found++;
                }
            }
            if (found == 0) b.append("- keine Treffer\n");
        } catch (Throwable t) { b.append("- Paketliste Fehler: ").append(t.getClass().getSimpleName()).append("\n"); }

        b.append("\nLetzte interne Meldungen (max. ")
                .append(MAX_VISIBLE_LOG_LINES).append("):\n")
                .append(DiagLog.text(MAX_VISIBLE_LOG_LINES));
        return b.toString();
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

    private void exportDiagnosis() {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "PoloLauncher_Diagnose_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(new Date()) + ".txt");
        try { startActivityForResult(create, REQUEST_EXPORT_DIAGNOSIS); }
        catch (Throwable error) {
            Toast.makeText(this, "Dateiauswahl nicht verfügbar", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_DIAGNOSIS || resultCode != RESULT_OK
                || data == null || data.getData() == null || exportInFlight) return;

        exportInFlight = true;
        final android.net.Uri targetUri = data.getData();
        final String visibleReport = output.getText().toString();
        final Context app = getApplicationContext();
        final WeakReference<DiagnosticsActivity> owner = new WeakReference<>(this);
        try {
            exportIo.execute(() -> {
                Throwable failure = null;
                try (OutputStream stream = app.getContentResolver().openOutputStream(targetUri, "w")) {
                    if (stream == null) throw new java.io.IOException("Kein Ausgabestrom");
                    String report = visibleReport
                            + "\n\nAlle internen Meldungen (max. 1200; die letzten 600 sind oben bereits enthalten):\n"
                            + DiagLog.text()
                            + "\nVollständiger letzter Java-Absturz:\n"
                            + CrashReporter.fullReport(app) + "\n";
                    stream.write(report.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                } catch (Throwable error) {
                    failure = error;
                }
                final Throwable result = failure;
                DiagnosticsActivity activity = owner.get();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    activity.exportInFlight = false;
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (result == null) {
                        Toast.makeText(activity, "Diagnosedatei gespeichert",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Speichern fehlgeschlagen: "
                                        + result.getClass().getSimpleName(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        } catch (RejectedExecutionException busy) {
            exportInFlight = false;
            Toast.makeText(this, "Diagnoseexport ist gerade nicht verfügbar",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void addToolbarButton(LinearLayout toolbar, String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(GlassSkin.TEXT);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setBackground(GlassSkin.touch(this, false));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(42));
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
    private static String num(double value, int decimals) { return Double.isNaN(value) ? "--" : String.format(Locale.GERMANY, "%." + decimals + "f", value); }
    private static String age(long timestamp) {
        if (timestamp <= 0L) return "nie";
        long value = VehicleRepository.now() - timestamp;
        if (value < 0L) return "Zeitfehler";
        if (value < 1_000L) return "<1 s";
        return value < 60_000L ? value / 1_000L + " s" : value / 60_000L + " min";
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
