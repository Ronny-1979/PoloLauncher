package de.ronny.pololauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One user-facing offline map: Germany base map plus speed limits and fixed cameras. */
public final class OfflineMapsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView progress;
    private TextView storage;
    private TextView updateInfo;
    private TextView details;
    private TextView install;
    private TextView importButton;
    private static final int REQUEST_PICK_USB_FOLDER = 4711;
    private TextView cancel;
    private TextView delete;
    private boolean resumed;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            render();
            GermanyOfflineStore.Status s = GermanyOfflineStore.status(OfflineMapsActivity.this);
            handler.postDelayed(this, s.active ? 1_000L : 5_000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(GlassSkin.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(25), dp(18), dp(25), dp(24));
        scroll.addView(root);

        root.addView(text("OFFLINE-KARTE DEUTSCHLAND", 25, GlassSkin.TEXT, Typeface.BOLD));
        root.addView(hint("Eine einzige Offline-Karte im Launcher. Ortssymbole (POIs) erscheinen ab Zoom 14, ausgewertet werden sie nicht; "
                + "Tempolimits und feste Blitzer werden beim Aufbau aus den passenden OSM-Daten erzeugt. "
                + "Der erste Aufbau ist groß und kann auf dem Autoradio mehrere Stunden dauern. "
                + "Er läuft als Hintergrunddienst weiter und wird nach Zündungs-/Prozessunterbrechungen automatisch fortgesetzt."));

        status = text("", 19, GlassSkin.ACCENT, Typeface.BOLD);
        status.setPadding(0, dp(12), 0, dp(4));
        root.addView(status);
        progress = text("", 15, GlassSkin.TEXT, Typeface.BOLD);
        root.addView(progress);
        details = text("", 14, GlassSkin.MUTED, Typeface.NORMAL);
        details.setPadding(0, dp(4), 0, dp(8));
        root.addView(details);
        storage = text("", 14, GlassSkin.TEXT, Typeface.NORMAL);
        storage.setPadding(0, 0, 0, dp(6));
        root.addView(storage);
        updateInfo = text("", 14, GlassSkin.MUTED, Typeface.NORMAL);
        updateInfo.setPadding(0, 0, 0, dp(10));
        root.addView(updateInfo);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(GlassSkin.panel(this, true));
        card.addView(text("Enthalten", 17, GlassSkin.TEXT, Typeface.BOLD));
        card.addView(check("Deutschlandkarte · offline"));
        card.addView(check("Tempolimits · auch im Stand erkennbar"));
        card.addView(check("Feste Blitzer · mit Diagnose-Logging"));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        install = addButton(root, "Deutschland installieren", this::primaryAction);
        importButton = addButton(root, "Von USB-Stick importieren", this::importFromUsb);
        cancel = addButton(root, "Aufbau abbrechen", () -> {
            GermanyOfflineStore.cancel(this);
            Toast.makeText(this, "Abbruch angefordert", Toast.LENGTH_SHORT).show();
            render();
        });
        cancel.setTextColor(0xffffa06b);
        delete = addButton(root, "Offline-Karte Deutschland löschen", this::confirmDelete);
        delete.setTextColor(0xffff8c8c);

        addButton(root, "Zurück", this::finish);
        setContentView(scroll);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        GermanyOfflineStore.autoResumePending(this);
        resumed = true;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void primaryAction() {
        if (GermanyOfflineStore.ready(this)) checkForUpdates();
        else startInstall();
    }

    private void startInstall() {
        GermanyOfflineStore.start(this);
        Toast.makeText(this, "Deutschland-Aufbau gestartet", Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 350L);
    }

    private void checkForUpdates() {
        if (GermanyOfflineUpdateChecker.running()) {
            Toast.makeText(this, "Updateprüfung läuft bereits", Toast.LENGTH_SHORT).show();
            return;
        }
        updateInfo.setText("Prüfe 14 Regionen für Karte und Verkehr …");
        GermanyOfflineUpdateChecker.checkAsync(this, (owner, result) -> {
            render();
            if (result.busy) {
                Toast.makeText(owner, result.error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!result.complete()) {
                String message = result.error != null ? result.error
                        : result.summary() + " · " + result.failedChecks + " Prüfungen nicht erreichbar";
                Toast.makeText(owner, message + " – bitte erneut prüfen", Toast.LENGTH_LONG).show();
                return;
            }
            if (!result.hasUpdates()) {
                Toast.makeText(owner, "Deutschland-Karte und Verkehrsdaten sind aktuell", Toast.LENGTH_LONG).show();
                return;
            }
            GlassSkin.dialog(owner)
                    .setTitle("Deutschland-Updates gefunden")
                    .setMessage(result.detail() + "\n\nNur geänderte Regionen werden geladen und neu aufgebaut. Jetzt aktualisieren?")
                    .setNegativeButton("Später", null)
                    .setPositiveButton("Aktualisieren", (dialog, which) -> {
                        GermanyOfflineStore.startUpdate(owner);
                        Toast.makeText(owner, "Deutschland-Aktualisierung gestartet", Toast.LENGTH_SHORT).show();
                        handler.removeCallbacks(refresh);
                        handler.postDelayed(refresh, 350L);
                    }).show();
        });
        render();
    }

    /** Looks for map files on a USB stick; falls back to the system folder picker. */
    private void importFromUsb() {
        if (GermanyOfflineStore.active(this)) {
            Toast.makeText(this, "Es läuft bereits ein Aufbau", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Suche USB-Stick …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            java.util.List<File> folders = UsbImportSource.discoverFolders(getApplicationContext());
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (folders.isEmpty()) {
                    DiagLog.add("USB-Import: kein Stick automatisch gefunden");
                    askForFolder();
                } else {
                    DiagLog.add("USB-Import: Stick gefunden: " + folders.get(0).getAbsolutePath());
                    confirmImport(UsbImportSource.fileSpec(folders.get(0)), folders.get(0).getAbsolutePath());
                }
            });
        }, "usb-import-discover").start();
    }

    private void askForFolder() {
        GlassSkin.dialog(this)
                .setTitle("Kein USB-Stick gefunden")
                .setMessage("Es wurden keine Kartendateien auf einem eingesteckten Stick gefunden. "
                        + "Stecke den Stick ein (Ordner „PoloMaps“ aus dem Windows-Programm) "
                        + "oder wähle den Ordner selbst aus.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ordner wählen", (dialog, which) -> {
                    try {
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                                REQUEST_PICK_USB_FOLDER);
                    } catch (ActivityNotFoundException missing) {
                        DiagLog.add("USB-Import: keine Dateiauswahl auf diesem Gerät");
                        Toast.makeText(this, "Dieses Radio hat keine Dateiauswahl – Stick ggf. neu einstecken",
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_USB_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // the permission still holds for this session
        }
        DiagLog.add("USB-Import: Ordner gewählt");
        confirmImport(UsbImportSource.treeSpec(tree), "gewählter Ordner");
    }

    private void confirmImport(String spec, String label) {
        new Thread(() -> {
            UsbImportSource source = UsbImportSource.open(getApplicationContext(), spec);
            int count = source == null ? 0 : source.count();
            long bytes = source == null ? 0L : source.totalBytes();
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (count == 0) {
                    DiagLog.add("USB-Import: keine Kartendateien in " + label);
                    Toast.makeText(this, "Keine Kartendateien gefunden (erwartet: Ordner PoloMaps)",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                GlassSkin.dialog(this)
                        .setTitle("Karten vom Stick importieren?")
                        .setMessage(count + " Dateien (" + GermanyOfflineStore.humanBytes(bytes) + ") in " + label
                                + ".\n\nDer Launcher übernimmt die Karten und baut Tempolimits und Blitzer selbst auf. "
                                + "Das kann mehrere Stunden dauern und läuft im Hintergrund. Die alte Karte bleibt aktiv, "
                                + "bis die neue fertig ist. Den Stick bitte stecken lassen, bis das Kopieren vorbei ist.")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Importieren", (dialog, which) -> {
                            GermanyOfflineStore.startImport(this, spec);
                            DiagLog.add("USB-Import: gestartet (" + count + " Dateien)");
                            Toast.makeText(this, "USB-Import gestartet", Toast.LENGTH_SHORT).show();
                            handler.removeCallbacks(refresh);
                            handler.postDelayed(refresh, 350L);
                        }).show();
            });
        }, "usb-import-open").start();
    }

    private void confirmDelete() {
        if (GermanyOfflineStore.active(this)) {
            Toast.makeText(this, "Bitte zuerst den laufenden Aufbau abbrechen", Toast.LENGTH_LONG).show();
            return;
        }
        GlassSkin.dialog(this)
                .setTitle("Offline-Karte Deutschland löschen?")
                .setMessage("Karte, Tempolimits und Blitzer-Daten werden gelöscht. Die Online-Karte bleibt erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (dialog, which) -> {
                    String message = GermanyOfflineStore.deleteAll(this);
                    DiagLog.add(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    render();
                }).show();
    }

    private void render() {
        GermanyOfflineStore.Status s = GermanyOfflineStore.status(this);
        boolean checkingUpdates = GermanyOfflineUpdateChecker.running();
        if (s.ready) {
            status.setText("✓ Offline-Karte Deutschland bereit");
            status.setTextColor(GlassSkin.GREEN);
            install.setText(checkingUpdates ? "Prüfe Updates …" : "Auf Updates prüfen");
        } else if (s.active) {
            status.setText("Deutschland wird aufgebaut …");
            status.setTextColor(GlassSkin.ACCENT);
            install.setText("Aufbau läuft");
        } else if (GermanyOfflineStore.PHASE_ERROR.equals(s.phase)) {
            status.setText("Aufbau unterbrochen / fehlgeschlagen");
            status.setTextColor(0xffffa06b);
            install.setText("Aufbau fortsetzen");
        } else if (GermanyOfflineStore.PHASE_CANCELLED.equals(s.phase)) {
            status.setText("Aufbau abgebrochen – fertige Teildaten bleiben erhalten");
            status.setTextColor(0xffffa06b);
            install.setText("Aufbau fortsetzen");
        } else {
            status.setText("Noch nicht installiert");
            status.setTextColor(GlassSkin.MUTED);
            install.setText("Deutschland installieren");
        }
        importButton.setEnabled(!s.active && !checkingUpdates);
        importButton.setAlpha((s.active || checkingUpdates) ? 0.45f : 1f);
        install.setEnabled(!s.active && !checkingUpdates);
        install.setAlpha((s.active || checkingUpdates) ? 0.45f : 1f);
        cancel.setVisibility(s.active ? View.VISIBLE : View.GONE);
        delete.setVisibility(s.bytes > 0L || s.ready ? View.VISIBLE : View.GONE);

        String phase = s.phase == null ? "" : s.phase;
        String step = s.steps > 0 ? "Schritt " + Math.max(0, s.step) + "/" + s.steps : "";
        String amount = s.total > 0L
                ? GermanyOfflineStore.humanBytes(s.done) + " / " + GermanyOfflineStore.humanBytes(s.total)
                : s.done > 0L ? GermanyOfflineStore.humanBytes(s.done) : "";
        progress.setText(join(" · ", phase, step, amount));
        details.setText(s.detail == null || s.detail.isEmpty()
                ? (s.ready && s.builtAt > 0L ? "Fertiggestellt: " + date(s.builtAt) : "")
                : s.detail);
        storage.setText("Offline-Paket: " + GermanyOfflineStore.humanBytes(s.bytes)
                + (s.free >= 0L ? "   •   frei: " + GermanyOfflineStore.humanBytes(s.free) : ""));
        GermanyOfflineStore.UpdateStatus updates = GermanyOfflineStore.updateStatus(this);
        if (!s.ready) {
            updateInfo.setText("");
        } else if (checkingUpdates) {
            updateInfo.setText("Updateprüfung: Karte + Verkehr für 14 Regionen …");
        } else if (updates.checked()) {
            String checked = "Letzte Prüfung " + date(updates.checkedAt) + " · ";
            String summary = updates.detail.isEmpty()
                    ? "Karten: " + updates.maps + " Regionen neuer · Verkehr: " + updates.traffic + " Regionen neuer"
                    : updates.detail;
            if (updates.failed > 0) summary += " · " + updates.failed + " nicht erreichbar";
            updateInfo.setText(checked + summary);
        } else {
            updateInfo.setText("Updates werden nur auf Knopfdruck geprüft.");
        }
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(part);
        }
        return b.toString();
    }

    private static String date(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date(millis));
    }

    private TextView check(String value) {
        TextView v = text("✓  " + value, 15, GlassSkin.GREEN, Typeface.BOLD);
        v.setPadding(0, dp(5), 0, dp(3));
        return v;
    }

    private TextView addButton(LinearLayout root, String title, Runnable action) {
        TextView button = text(title, 17, GlassSkin.TEXT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(GlassSkin.touch(this, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(9);
        root.addView(button, lp);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView hint(String value) {
        TextView v = text(value, 14, GlassSkin.MUTED, Typeface.NORMAL);
        v.setPadding(0, dp(4), 0, dp(8));
        return v;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", style));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
