package de.ronny.pololauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Compact map configuration. All values are persisted immediately. */
public final class MapSettingsActivity extends Activity {
    private TextView activeStyle;
    private TextView currentZoom;
    private TextView settlementZoomText;
    private TextView outsideZoomText;
    private TextView autoHint;
    private TextView offlineStatus;
    private SeekBar zoomBar;
    private SeekBar settlementZoomBar;
    private SeekBar outsideZoomBar;
    private Switch roadSnap;
    private TextView roadSnapHint;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(CopperSkin.background());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(25), dp(18), dp(25), dp(24));
        scroll.addView(root);

        root.addView(text("KARTE", 26, CopperSkin.CREAM, true), new LinearLayout.LayoutParams(-1, dp(48)));

        section(root, "ONLINE-KARTENSTIL");
        activeStyle = text("", 16, CopperSkin.GREEN, true);
        root.addView(activeStyle, new LinearLayout.LayoutParams(-1, dp(34)));
        RadioGroup styles = new RadioGroup(this);
        styles.setOrientation(RadioGroup.HORIZONTAL);
        addStyle(styles, "Liberty", "liberty");
        addStyle(styles, "Bright", "bright");
        addStyle(styles, "3D", "3d");
        root.addView(styles, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(hint("3D zeigt geneigte Vektorkarten und kartierte Gebäude. Bei Ruckeln Liberty oder Bright wählen."));
        refreshStyle();

        section(root, "FAHRTRICHTUNG");
        Switch heading = toggle("Fahrtrichtung oben", MapPreferences.headingUp(this));
        heading.setOnCheckedChangeListener((button, enabled) -> MapPreferences.setHeadingUp(this, enabled));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(hint("Wirkt auf Online- und Offline-Karte. Ohne brauchbaren GPS-Kurs bleibt Norden oben."));

        section(root, "GPS-POSITION – TEST");
        boolean roadDataAvailable = OfflineMapStore.selectedFile(this) != null;
        roadSnap = toggle("Punkt auf Straße einrasten", MapPreferences.roadSnap(this));
        roadSnap.setEnabled(roadDataAvailable);
        roadSnap.setAlpha(roadDataAvailable ? 1f : 0.45f);
        roadSnap.setOnCheckedChangeListener((button, enabled) -> MapPreferences.setRoadSnap(this, enabled));
        root.addView(roadSnap, new LinearLayout.LayoutParams(-1, dp(56)));
        roadSnapHint = hint(roadDataAvailable
                ? "Testmodus für Online- und Offline-Karte. Nutzt Abstand, Fahrtrichtung und Straßen der Offline-Region."
                : "Für den Testmodus muss zuerst eine Offline-Region heruntergeladen und ausgewählt werden.");
        root.addView(roadSnapHint);

        section(root, "ZOOM");
        currentZoom = text("", 20, CopperSkin.GOLD, true);
        root.addView(currentZoom, new LinearLayout.LayoutParams(-1, dp(38)));
        zoomBar = new SeekBar(this);
        zoomBar.setMax(MapPreferences.MAX_ZOOM - MapPreferences.MIN_ZOOM);
        zoomBar.setProgress(MapPreferences.zoom(this) - MapPreferences.MIN_ZOOM);
        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = MapPreferences.MIN_ZOOM + progress;
                currentZoom.setText("Grundzoom / noch nicht erkannt: " + value);
                if (fromUser) MapPreferences.setZoom(MapSettingsActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(zoomBar, new LinearLayout.LayoutParams(-1, dp(52)));
        currentZoom.setText("Grundzoom / noch nicht erkannt: " + MapPreferences.zoom(this));
        root.addView(hint("Solange noch kein Gebiet erkannt wurde, gilt bei GPS-Nachführung mindestens Zoom 12."));

        Switch automatic = toggle("Ortsabhängigen Zoom verwenden", MapPreferences.autoZoom(this));
        LinearLayout.LayoutParams automaticParams = new LinearLayout.LayoutParams(-1, dp(56));
        automaticParams.topMargin = dp(8);
        root.addView(automatic, automaticParams);
        autoHint = hint("");
        root.addView(autoHint);
        automatic.setOnCheckedChangeListener((button, enabled) -> {
            MapPreferences.setAutoZoom(this, enabled);
            refreshAutoHint(enabled);
            refreshZoomControls(enabled);
        });

        settlementZoomText = text("Innerorts: " + MapPreferences.settlementZoom(this), 18, CopperSkin.GOLD, true);
        root.addView(settlementZoomText, new LinearLayout.LayoutParams(-1, dp(34)));
        settlementZoomBar = new SeekBar(this);
        settlementZoomBar.setMax(MapPreferences.MAX_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
        settlementZoomBar.setProgress(MapPreferences.settlementZoom(this) - MapPreferences.FOLLOW_MIN_ZOOM);
        settlementZoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = MapPreferences.FOLLOW_MIN_ZOOM + progress;
                settlementZoomText.setText("Innerorts: " + value);
                if (fromUser) MapPreferences.setSettlementZoom(MapSettingsActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(settlementZoomBar, new LinearLayout.LayoutParams(-1, dp(45)));

        outsideZoomText = text("Außerorts: " + MapPreferences.outsideZoom(this), 18, CopperSkin.GOLD, true);
        root.addView(outsideZoomText, new LinearLayout.LayoutParams(-1, dp(34)));
        outsideZoomBar = new SeekBar(this);
        outsideZoomBar.setMax(MapPreferences.MAX_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
        outsideZoomBar.setProgress(MapPreferences.outsideZoom(this) - MapPreferences.FOLLOW_MIN_ZOOM);
        outsideZoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = MapPreferences.FOLLOW_MIN_ZOOM + progress;
                outsideZoomText.setText("Außerorts: " + value);
                if (fromUser) MapPreferences.setOutsideZoom(MapSettingsActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(outsideZoomBar, new LinearLayout.LayoutParams(-1, dp(45)));

        addButton(root, "Zoomwerte auf Standard zurücksetzen", () -> {
            MapPreferences.setZoom(this, 15);
            MapPreferences.setSettlementZoom(this, MapPreferences.DEFAULT_SETTLEMENT_ZOOM);
            MapPreferences.setOutsideZoom(this, MapPreferences.DEFAULT_OUTSIDE_ZOOM);
            zoomBar.setProgress(15 - MapPreferences.MIN_ZOOM);
            settlementZoomBar.setProgress(MapPreferences.DEFAULT_SETTLEMENT_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
            outsideZoomBar.setProgress(MapPreferences.DEFAULT_OUTSIDE_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
        });
        refreshAutoHint(automatic.isChecked());
        refreshZoomControls(automatic.isChecked());

        section(root, "OFFLINE-KARTE");
        offlineStatus = text(selectedRegionText(), 16,
                OfflineMapStore.selectedFile(this) == null ? 0xffffa06b : CopperSkin.GREEN, true);
        root.addView(offlineStatus, new LinearLayout.LayoutParams(-1, dp(38)));
        addButton(root, "Offline-Karten verwalten", () -> startActivity(new Intent(this, OfflineMapsActivity.class)));
        root.addView(hint("Online-Karten werden zwischengespeichert. Eine vollständige Region für Fahrten ohne Internet muss separat heruntergeladen werden."));

        addButton(root, "Zurück", this::finish);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAutoHint(MapPreferences.autoZoom(this));
        if (roadSnap != null) {
            boolean available = OfflineMapStore.selectedFile(this) != null;
            roadSnap.setEnabled(available);
            roadSnap.setAlpha(available ? 1f : 0.45f);
            roadSnapHint.setText(available
                    ? "Testmodus für Online- und Offline-Karte. Nutzt Abstand, Fahrtrichtung und Straßen der Offline-Region."
                    : "Für den Testmodus muss zuerst eine Offline-Region heruntergeladen und ausgewählt werden.");
        }
        if (offlineStatus != null) {
            offlineStatus.setText(selectedRegionText());
            offlineStatus.setTextColor(OfflineMapStore.selectedFile(this) == null ? 0xffffa06b : CopperSkin.GREEN);
        }
    }

    private void refreshStyle() {
        if (activeStyle == null) return;
        String key = MapPreferences.vectorStyle(this);
        String name = "3d".equals(key) ? "3D" : "bright".equals(key) ? "Bright" : "Liberty";
        activeStyle.setText("Aktiver Stil: " + name);
    }

    private void refreshAutoHint(boolean enabled) {
        if (autoHint == null) return;
        if (enabled && OfflineMapStore.selectedFile(this) == null) {
            autoHint.setText("⚠ Automatischer Zoom benötigt eine installierte und ausgewählte Offline-Region.");
            autoHint.setTextColor(0xffffa06b);
        } else {
            autoHint.setText(enabled
                    ? "Automatischer Zoom ist aktiv und nutzt die Ortsflächen der Offline-Region."
                    : "Ausgeschaltet: Die eingestellte Zoomstufe bleibt der feste Ausgangswert.");
            autoHint.setTextColor(CopperSkin.MUTED);
        }
    }

    private void refreshZoomControls(boolean enabled) {
        if (settlementZoomBar == null || outsideZoomBar == null) return;
        settlementZoomBar.setEnabled(enabled);
        outsideZoomBar.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.45f;
        settlementZoomBar.setAlpha(alpha);
        outsideZoomBar.setAlpha(alpha);
        settlementZoomText.setAlpha(alpha);
        outsideZoomText.setAlpha(alpha);
    }

    private String selectedRegionText() {
        int index = OfflineMapStore.indexOf(OfflineMapStore.selected(this));
        return index >= 0 && OfflineMapStore.selectedFile(this) != null
                ? "Aktive Region: " + OfflineMapStore.NAMES[index] : "Keine Offline-Region ausgewählt";
    }

    private void addStyle(RadioGroup group, String title, String key) {
        RadioButton option = new RadioButton(this);
        option.setText(title);
        option.setTextColor(CopperSkin.CREAM);
        option.setTextSize(16);
        option.setChecked(key.equals(MapPreferences.vectorStyle(this)));
        option.setPadding(0, 0, dp(14), 0);
        option.setOnClickListener(v -> {
            MapPreferences.setVectorStyle(this, key);
            refreshStyle();
        });
        group.addView(option);
    }

    private Switch toggle(String title, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(17);
        toggle.setTextColor(CopperSkin.CREAM);
        toggle.setChecked(checked);
        return toggle;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = text(title, 18, CopperSkin.GOLD, true);
        heading.setPadding(0, dp(15), 0, 0);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private TextView hint(String value) {
        TextView view = text(value, 14, CopperSkin.MUTED, false);
        view.setPadding(0, dp(3), 0, dp(6));
        return view;
    }

    private void addButton(LinearLayout root, String title, Runnable action) {
        TextView button = text(title, 16, CopperSkin.CREAM, true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(CopperSkin.touch(this, false));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(9);
        params.bottomMargin = dp(5);
        root.addView(button, params);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
