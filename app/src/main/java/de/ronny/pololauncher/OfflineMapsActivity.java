package de.ronny.pololauncher;

import android.app.Activity;
import android.app.DownloadManager;
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

import java.util.Locale;
import java.util.Arrays;

/** Download, replace, select and delete complete Mapsforge regions. */
public final class OfflineMapsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView storage;
    private TextView cancelDownload;
    private LinearLayout list;
    private String lastResult;
    private OfflineMapStore.UiState mapState;
    private boolean statusInFlight, actionPending, resumed;
    private long actionRevision;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            OfflineMapStore.finishIfCompleteAsync(OfflineMapsActivity.this, (owner, finished) ->
                    ((OfflineMapsActivity) owner).handleFinishedDownload(finished));
            requestStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    private void handleFinishedDownload(String finished) {
        lastResult = finished;
        actionRevision++;
        requestStatus();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(CopperSkin.background());
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(25), dp(17), dp(25), dp(22));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        root.addView(text("OFFLINE-KARTEN", 25, CopperSkin.CREAM, Typeface.BOLD));
        root.addView(hint("Komplette Bundesländer für Fahrten ohne Internet. Downloads sind über WLAN und mobile Daten erlaubt; Roaming bleibt gesperrt."));

        storage = text("", 15, CopperSkin.CREAM, Typeface.BOLD);
        storage.setPadding(0, dp(8), 0, dp(4));
        root.addView(storage);
        status = text("", 16, CopperSkin.GOLD, Typeface.BOLD);
        status.setPadding(0, dp(5), 0, dp(7));
        root.addView(status);
        cancelDownload = addButton(root, "Laufenden Download abbrechen", this::confirmCancel);
        cancelDownload.setTextColor(0xffffa06b);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        addButton(root, "Zurück", this::finish);
        setContentView(scroll);
        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void updateStatus() {
        if (mapState == null) {
            status.setText(lastResult == null ? "Kartenstatus wird geladen …" : lastResult);
            cancelDownload.setVisibility(View.GONE);
            return;
        }
        storage.setText("Kartenspeicher: " + size(mapState.used)
                + (mapState.free >= 0L ? " belegt  •  " + size(mapState.free) + " frei" : " belegt"));
        String key = mapState.downloading;
        int index = OfflineMapStore.indexOf(key);
        boolean active = index >= 0 && mapState.downloadId >= 0L;
        cancelDownload.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            long[] progress = mapState.progress;
            String amount = progress[1] > 0L
                    ? String.format(Locale.GERMANY, "%.1f / %.1f MB", progress[0] / 1048576.0, progress[1] / 1048576.0)
                    : String.format(Locale.GERMANY, "%.1f MB", progress[0] / 1048576.0);
            String state = progress[2] == DownloadManager.STATUS_PAUSED
                    ? "Pausiert – wartet auf Verbindung" : "Lädt";
            status.setText(state + ": " + OfflineMapStore.NAMES[index] + "  " + amount);
        } else if (lastResult != null) {
            status.setText(lastResult);
        } else {
            int selected = OfflineMapStore.indexOf(mapState.selected);
            status.setText(selected >= 0 && mapState.installed[selected]
                    ? "Aktive Karte: " + OfflineMapStore.NAMES[selected]
                    : "Keine Offline-Karte ausgewählt");
        }
    }

    private void requestStatus() {
        if (!resumed || statusInFlight || actionPending) return;
        statusInFlight = true;
        final long revision = actionRevision;
        OfflineMapStore.loadUiStateAsync(this, (owner, snapshot, error) ->
                ((OfflineMapsActivity) owner).acceptStatus(revision, snapshot, error));
    }

    private void acceptStatus(long revision, OfflineMapStore.UiState snapshot, String error) {
        statusInFlight = false;
        if (!resumed || actionPending) return;
        if (revision != actionRevision) { requestStatus(); return; }
        if (error != null) { lastResult = error; updateStatus(); return; }
        boolean changed = mapState == null || !mapState.selected.equals(snapshot.selected)
                || !Arrays.equals(mapState.installed, snapshot.installed);
        mapState = snapshot;
        if (changed) renderList();
        updateStatus();
    }

    private void runAction(OfflineMapStore.MapAction action) {
        if (actionPending) return;
        actionPending = true;
        actionRevision++;
        status.setText("Kartenverwaltung arbeitet …");
        OfflineMapStore.runActionAsync(this, action, (owner, message) -> {
            OfflineMapsActivity screen = (OfflineMapsActivity) owner;
            screen.actionPending = false;
            screen.lastResult = message;
            screen.requestStatus();
        });
    }

    private void renderList() {
        if (mapState == null) return;
        list.removeAllViews();
        String selected = mapState.selected;
        for (int i = 0; i < OfflineMapStore.FILES.length; i++) {
            final int index = i;
            String key = OfflineMapStore.FILES[index];
            boolean installed = mapState.installed[index];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(7), dp(14), dp(8));
            card.setBackground(CopperSkin.panel(this, installed && key.equals(selected)));

            String suffix = installed
                    ? key.equals(selected) ? "  ✓ AKTIV" : "  • installiert"
                    : "  • ca. " + OfflineMapStore.SIZE_MB[index] + " MB";
            TextView name = text(OfflineMapStore.NAMES[index] + suffix, 16,
                    installed && key.equals(selected) ? CopperSkin.GREEN : CopperSkin.CREAM, Typeface.BOLD);
            card.addView(name, new LinearLayout.LayoutParams(-1, dp(35)));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            if (installed) {
                addSmallButton(actions, key.equals(selected) ? "Aktiv" : "Auswählen",
                        () -> select(index), key.equals(selected));
                addSmallButton(actions, "Neu laden", () -> confirmDownload(index, true), false);
                addSmallButton(actions, "Löschen", () -> confirmDelete(index), false);
            } else {
                addSmallButton(actions, "Herunterladen", () -> confirmDownload(index, false), false);
            }
            card.addView(actions, new LinearLayout.LayoutParams(-1, dp(45)));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(94));
            cardParams.topMargin = dp(7);
            list.addView(card, cardParams);
        }
        updateStatus();
    }

    private void select(int index) {
        String key = OfflineMapStore.FILES[index];
        runAction(app -> { OfflineMapStore.select(app, key); return null; });
    }

    private void confirmDownload(int index, boolean replacement) {
        if (OfflineMapStore.activeId(this) >= 0L) {
            Toast.makeText(this, "Bitte laufenden Download zuerst beenden", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = replacement ? "Karte erneut herunterladen?" : "Karte herunterladen?";
        String message = OfflineMapStore.NAMES[index] + ", ca. " + OfflineMapStore.SIZE_MB[index]
                + " MB. Mobile Daten sind erlaubt und können Datenvolumen verbrauchen."
                + (replacement ? " Die vorhandene Karte bleibt nutzbar, bis die neue Datei vollständig geprüft wurde." : "");
        CopperSkin.dialog(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Herunterladen", (dialog, which) -> startDownload(index))
                .show();
    }

    private void startDownload(int index) {
        String key = OfflineMapStore.FILES[index];
        runAction(app -> { OfflineMapStore.start(app, key); return null; });
    }

    private void confirmDelete(int index) {
        CopperSkin.dialog(this)
                .setTitle("Offline-Karte löschen?")
                .setMessage(OfflineMapStore.NAMES[index] + " wird vom Radio entfernt und müsste später erneut heruntergeladen werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (dialog, which) -> {
                    String key = OfflineMapStore.FILES[index];
                    runAction(app -> OfflineMapStore.deleteInstalled(app, key));
                }).show();
    }

    private void confirmCancel() {
        CopperSkin.dialog(this)
                .setTitle("Download abbrechen?")
                .setMessage("Die unvollständige Datei wird entfernt. Eine bereits installierte Karte bleibt erhalten.")
                .setNegativeButton("Weiterladen", null)
                .setPositiveButton("Abbrechen", (dialog, which) -> {
                    runAction(OfflineMapStore::cancelActive);
                }).show();
    }

    private TextView addButton(LinearLayout target, String label, Runnable action) {
        TextView button = text(label, 16, CopperSkin.CREAM, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), 0, dp(12), 0);
        button.setBackground(CopperSkin.touch(this, false));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(51));
        params.setMargins(0, dp(7), 0, dp(4));
        target.addView(button, params);
        return button;
    }

    private void addSmallButton(LinearLayout target, String label, Runnable action, boolean selected) {
        TextView button = text(label, 14, selected ? CopperSkin.GREEN : CopperSkin.CREAM, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(CopperSkin.touch(this, selected));
        button.setOnClickListener(v -> action.run());
        button.setEnabled(!selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        target.addView(button, params);
    }

    private TextView hint(String value) {
        TextView view = text(value, 14, CopperSkin.MUTED, Typeface.NORMAL);
        view.setPadding(0, dp(6), 0, dp(5));
        return view;
    }

    private static String size(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L)
            return String.format(Locale.GERMANY, "%.1f GB", bytes / 1073741824.0);
        return String.format(Locale.GERMANY, "%.0f MB", bytes / 1048576.0);
    }

    private TextView text(String value, float size, int color, int weight) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", weight));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
