package de.ronny.pololauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Compact map configuration. All values are persisted immediately. */
public final class MapSettingsActivity extends Activity {
    private TextView activeColorMode;
    private TextView settlementZoomText;
    private TextView outsideZoomText;
    private TextView offlineStatus;
    private SeekBar tiltBar;
    private SeekBar heightBar;
    private TextView tiltText;
    private TextView heightText;
    private static final int TILT_STEP = 5;
    private SeekBar settlementZoomBar;
    private SeekBar outsideZoomBar;
    private Switch directBoundaryZoom;
    private Switch speedCameraAlerts;
    private TextView speedCameraHint;
    private RadioGroup colorModes;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(GlassSkin.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(25), dp(18), dp(25), dp(24));
        scroll.addView(root);

        root.addView(text("KARTE", 26, GlassSkin.TEXT, true), new LinearLayout.LayoutParams(-1, dp(48)));

        section(root, "3D-ANSICHT");
        tiltText = text("", 18, GlassSkin.ACCENT, true);
        root.addView(tiltText, new LinearLayout.LayoutParams(-1, dp(34)));
        tiltBar = new SeekBar(this);
        tiltBar.setMax(MapPreferences.MAX_TILT / TILT_STEP);
        tiltBar.setProgress(MapPreferences.tilt(this) / TILT_STEP);
        tiltBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = progress * TILT_STEP;
                tiltText.setText(tiltLabel(value));
                if (fromUser) MapPreferences.setTilt(MapSettingsActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(tiltBar, new LinearLayout.LayoutParams(-1, dp(45)));
        tiltText.setText(tiltLabel(MapPreferences.tilt(this)));
        heightText = text("", 18, GlassSkin.ACCENT, true);
        root.addView(heightText, new LinearLayout.LayoutParams(-1, dp(34)));
        heightBar = new SeekBar(this);
        heightBar.setMax(MapPreferences.MAX_BUILDING_HEIGHT);
        heightBar.setProgress(MapPreferences.buildingHeight(this));
        heightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                heightText.setText(heightLabel(progress));
                if (fromUser) MapPreferences.setBuildingHeight(MapSettingsActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(heightBar, new LinearLayout.LayoutParams(-1, dp(45)));
        heightText.setText(heightLabel(MapPreferences.buildingHeight(this)));
        root.addView(hint("Neigung: 0° = Karte von oben, 45° ist der Standard. Gebäudehöhe: Die Offline-Daten kennen keine echten Höhen, alle Gebäude werden gleich hoch gezeichnet (0 = flach). Gebäude erscheinen ab Zoom 14. Änderungen gelten, sobald du zur Hauptseite zurückkehrst."));
        addButton(root, "3D-Werte auf Standard zurücksetzen", () -> {
            MapPreferences.setTilt(this, MapPreferences.DEFAULT_TILT);
            MapPreferences.setBuildingHeight(this, MapPreferences.DEFAULT_BUILDING_HEIGHT);
            tiltBar.setProgress(MapPreferences.DEFAULT_TILT / TILT_STEP);
            heightBar.setProgress(MapPreferences.DEFAULT_BUILDING_HEIGHT);
        });

        section(root, "POSITIONSMARKE");
        RadioGroup markers = new RadioGroup(this);
        markers.setOrientation(RadioGroup.VERTICAL);
        String current = MapPreferences.markerStyle(this);
        for (String id : MarkerStyle.ids()) {
            RadioButton button = new RadioButton(this);
            button.setText(MarkerStyle.label(id));
            button.setTextColor(GlassSkin.TEXT);
            button.setTextSize(17);
            button.setTag(id);
            button.setId(View.generateViewId());
            markers.addView(button, new RadioGroup.LayoutParams(-1, dp(48)));
            if (id.equals(current)) markers.check(button.getId());
        }
        markers.setOnCheckedChangeListener((group, checkedId) -> {
            View checked = group.findViewById(checkedId);
            if (checked != null) MapPreferences.setMarkerStyle(this, String.valueOf(checked.getTag()));
        });
        root.addView(markers, new LinearLayout.LayoutParams(-1, -2));
        root.addView(hint("Wie dein Auto auf der Karte markiert wird: der blaue Standard-Punkt, ein Halloween-Kürbis oder der Schlitten vom Weihnachtsmann (dreht sich mit der Fahrtrichtung). Wirkt, sobald du zur Hauptseite zurückkehrst."));

        section(root, "KARTENMODUS");
        activeColorMode = text("", 16, GlassSkin.GREEN, true);
        root.addView(activeColorMode, new LinearLayout.LayoutParams(-1, dp(34)));
        colorModes = new RadioGroup(this);
        colorModes.setOrientation(RadioGroup.HORIZONTAL);
        addColorMode(colorModes, "Automatisch", MapPreferences.COLOR_AUTO);
        addColorMode(colorModes, "Tag", MapPreferences.COLOR_DAY);
        addColorMode(colorModes, "Nacht", MapPreferences.COLOR_NIGHT);
        root.addView(colorModes, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(hint("Automatisch schaltet mit Sonnenaufgang und Sonnenuntergang an der GPS-Position. Wirkt auf die Karte."));
        refreshColorMode();

        section(root, "FAHRTRICHTUNG");
        Switch heading = toggle("Fahrtrichtung oben", MapPreferences.headingUp(this));
        heading.setOnCheckedChangeListener((button, enabled) -> MapPreferences.setHeadingUp(this, enabled));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(hint("Wirkt auf die Karte. Ohne brauchbaren GPS-Kurs bleibt Norden oben."));

        section(root, "VERKEHRSWARNUNGEN");
        speedCameraAlerts = toggle("Feste Blitzer aus Offline-Karte anzeigen",
                MapPreferences.speedCameraAlerts(this));
        speedCameraAlerts.setOnCheckedChangeListener((button, enabled) ->
                MapPreferences.setSpeedCameraAlerts(this, enabled));
        root.addView(speedCameraAlerts, new LinearLayout.LayoutParams(-1, dp(56)));
        speedCameraHint = hint(speedCameraHintText());
        root.addView(speedCameraHint);

        section(root, "ZOOM");
        root.addView(hint("Der Zoom richtet sich automatisch nach dem Ort: Innerorts und Außerorts werden an der GPS-Position erkannt (mit Vorausschau, schnellem Ortseingang und geglättetem Herauszoomen). Bis das Gebiet erkannt ist, gilt Zoom 16."));

        directBoundaryZoom = toggle("Direkt an Ortsgrenze umschalten",
                MapPreferences.directBoundaryZoom(this));
        directBoundaryZoom.setOnCheckedChangeListener((button, enabled) ->
                MapPreferences.setDirectBoundaryZoom(this, enabled));
        root.addView(directBoundaryZoom, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(hint("Beide Modi schauen in Fahrtrichtung voraus und schalten beim Einfahren schnell auf Innerorts. "
                + "Stabil hält Innerorts bei OSM-Lücken besonders konsequent und zoomt erst nach mehreren klaren Außerorts-Treffern wieder heraus. "
                + "Direkt reagiert beim tatsächlichen Verlassen etwas früher."));

        settlementZoomText = text("Innerorts: " + MapPreferences.settlementZoom(this), 18, GlassSkin.ACCENT, true);
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

        outsideZoomText = text("Außerorts: " + MapPreferences.outsideZoom(this), 18, GlassSkin.ACCENT, true);
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
            MapPreferences.setZoom(this, MapPreferences.DEFAULT_ZOOM);
            MapPreferences.setSettlementZoom(this, MapPreferences.DEFAULT_SETTLEMENT_ZOOM);
            MapPreferences.setOutsideZoom(this, MapPreferences.DEFAULT_OUTSIDE_ZOOM);
            settlementZoomBar.setProgress(MapPreferences.DEFAULT_SETTLEMENT_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
            outsideZoomBar.setProgress(MapPreferences.DEFAULT_OUTSIDE_ZOOM - MapPreferences.FOLLOW_MIN_ZOOM);
        });

        section(root, "OFFLINE-KARTE");
        offlineStatus = text(offlineStatusText(), 16,
                GermanyOfflineStore.ready(this) ? GlassSkin.GREEN : 0xffffa06b, true);
        root.addView(offlineStatus, new LinearLayout.LayoutParams(-1, dp(38)));
        addButton(root, "Offline-Karte Deutschland verwalten", () -> startActivity(new Intent(this, OfflineMapsActivity.class)));
        root.addView(hint("Die Offline-Karte Deutschland enthält die Basiskarte, alle Straßen zum Einrasten des Standortpunkts sowie Tempolimits und feste Blitzer. Symbole für Orte (Gaststätten, Tankstellen …) erscheinen ab Zoom 14."));

        addButton(root, "Zurück", this::finish);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshColorMode();
        if (directBoundaryZoom != null) {
            directBoundaryZoom.setChecked(MapPreferences.directBoundaryZoom(this));
        }
        if (speedCameraAlerts != null) {
            speedCameraAlerts.setChecked(MapPreferences.speedCameraAlerts(this));
            if (speedCameraHint != null) speedCameraHint.setText(speedCameraHintText());
        }
        if (offlineStatus != null) {
            offlineStatus.setText(offlineStatusText());
            offlineStatus.setTextColor(GermanyOfflineStore.ready(this) ? GlassSkin.GREEN : 0xffffa06b);
        }
    }

    private void refreshColorMode() {
        if (activeColorMode == null) return;
        String mode = MapPreferences.colorMode(this);
        String name = MapPreferences.COLOR_DAY.equals(mode) ? "Immer Tag"
                : MapPreferences.COLOR_NIGHT.equals(mode) ? "Immer Nacht"
                : "Automatisch nach Sonnenstand";
        activeColorMode.setText("Aktiver Modus: " + name);
        syncRadioSelection(colorModes, mode);
    }

    private String speedCameraHintText() {
        if (!GermanyOfflineStore.ready(this))
            return "Standardmäßig aus. Benötigt die Offline-Karte Deutschland; ohne Offline-Daten werden keine Blitzer angezeigt.";
        return "Nutzt feste OSM-Blitzer aus dem Deutschland-Paket. Ein bestätigter Treffer erscheint oben links mit Entfernung und wird im Diagnose-Log protokolliert. Daten können fehlen oder veraltet sein; Rechtslage beachten.";
    }

    private String offlineStatusText() {
        if (GermanyOfflineStore.ready(this)) return "✓ Offline-Karte Deutschland bereit";
        GermanyOfflineStore.Status s = GermanyOfflineStore.status(this);
        if (s.active) return "Deutschland wird aufgebaut: " + s.detail;
        return "Offline-Karte Deutschland noch nicht installiert";
    }

    private void addColorMode(RadioGroup group, String title, String key) {
        RadioButton option = new RadioButton(this);
        option.setId(android.view.View.generateViewId());
        option.setTag(key);
        option.setText(title);
        option.setTextColor(GlassSkin.TEXT);
        option.setTextSize(16);
        option.setPadding(0, 0, dp(14), 0);
        option.setOnClickListener(v -> {
            MapPreferences.setColorMode(this, key);
            refreshColorMode();
        });
        group.addView(option);
    }

    /** Keeps every RadioGroup in sync with its persisted value immediately. */
    private static void syncRadioSelection(RadioGroup group, String selectedKey) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof RadioButton && selectedKey.equals(child.getTag())) {
                group.check(child.getId());
                return;
            }
        }
        group.clearCheck();
    }

    private Switch toggle(String title, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(17);
        toggle.setTextColor(GlassSkin.TEXT);
        toggle.setChecked(checked);
        return toggle;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = text(title, 18, GlassSkin.ACCENT, true);
        heading.setPadding(0, dp(15), 0, 0);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private TextView hint(String value) {
        TextView view = text(value, 14, GlassSkin.MUTED, false);
        view.setPadding(0, dp(3), 0, dp(6));
        return view;
    }

    private void addButton(LinearLayout root, String title, Runnable action) {
        TextView button = text(title, 16, GlassSkin.TEXT, true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(GlassSkin.touch(this, false));
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

    private static String tiltLabel(int degrees) {
        return "Neigung: " + degrees + "°" + (degrees == 0 ? " (von oben)" : degrees == MapPreferences.DEFAULT_TILT ? " (Standard)" : "");
    }

    private static String heightLabel(int metres) {
        return "Gebäudehöhe: " + (metres == 0 ? "flach" : metres + " m") + (metres == MapPreferences.DEFAULT_BUILDING_HEIGHT ? " (Standard)" : "");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
