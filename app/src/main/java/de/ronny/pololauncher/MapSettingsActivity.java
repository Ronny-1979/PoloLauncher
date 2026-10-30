package de.ronny.pololauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Map configuration. Every value is a large tap control (see SettingsUi): no sliders, no swipe switches,
 * so scrolling can never change a setting. All values are persisted immediately.
 */
public final class MapSettingsActivity extends Activity {
    private static final int TILT_STEP = 5;
    private TextView offlineStatus;
    private TextView speedCameraHint;
    private Runnable refreshColorMode;
    private Runnable refreshCameraToggle;
    private final java.util.List<Runnable> refreshers = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(GlassSkin.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(14), dp(24), dp(24));
        scroll.addView(root);
        root.addView(SettingsUi.title(this, "KARTE"), new LinearLayout.LayoutParams(-1, dp(46)));

        // --- Darstellung
        LinearLayout look = SettingsUi.card(this, root, "DARSTELLUNG");
        refreshColorMode = SettingsUi.choice(this, look, "Kartenmodus",
                new String[]{"Automatisch", "Tag", "Nacht"},
                new String[]{MapPreferences.COLOR_AUTO, MapPreferences.COLOR_DAY, MapPreferences.COLOR_NIGHT},
                () -> MapPreferences.colorMode(this), key -> MapPreferences.setColorMode(this, key));
        SettingsUi.hint(this, look, "Automatisch: Tag ab Sonnenaufgang, Nacht 30 Minuten vor Sonnenuntergang (nach GPS-Position).");
        SettingsUi.divider(this, look);
        String[] markerKeys = MarkerStyle.ids();
        String[] markerTitles = new String[markerKeys.length];
        for (int i = 0; i < markerKeys.length; i++) markerTitles[i] = MarkerStyle.label(markerKeys[i]);
        refreshers.add(SettingsUi.choice(this, look, "Positionsmarke", markerTitles, markerKeys,
                () -> MapPreferences.markerStyle(this), key -> MapPreferences.setMarkerStyle(this, key)));
        SettingsUi.divider(this, look);
        refreshers.add(SettingsUi.toggle(this, look, "Fahrtrichtung oben", "Ohne GPS-Kurs bleibt Norden oben.",
                () -> MapPreferences.headingUp(this), on -> MapPreferences.setHeadingUp(this, on)));

        // --- 3D-ANSICHT
        LinearLayout three = SettingsUi.card(this, root, "3D-ANSICHT");
        refreshers.add(SettingsUi.stepper(this, three, "Neigung",
                () -> MapPreferences.tilt(this), v -> MapPreferences.setTilt(this, v),
                0, MapPreferences.MAX_TILT, TILT_STEP, MapSettingsActivity::tiltLabel));
        SettingsUi.divider(this, three);
        refreshers.add(SettingsUi.stepper(this, three, "Gebäudehöhe",
                () -> MapPreferences.buildingHeight(this), v -> MapPreferences.setBuildingHeight(this, v),
                0, MapPreferences.MAX_BUILDING_HEIGHT, 3, MapSettingsActivity::heightLabel));
        SettingsUi.hint(this, three, "Gebäude erscheinen ab Zoom 14 und sind alle gleich hoch (0 = flach). Gilt nach Rückkehr zur Hauptseite.");
        SettingsUi.button(this, three, "3D auf Standard zurücksetzen", () -> {
            MapPreferences.setTilt(this, MapPreferences.DEFAULT_TILT);
            MapPreferences.setBuildingHeight(this, MapPreferences.DEFAULT_BUILDING_HEIGHT);
            refreshAll();
        });

        // --- Zoom
        LinearLayout zoom = SettingsUi.card(this, root, "ZOOM");
        SettingsUi.hint(this, zoom, "Der Zoom wechselt automatisch an der Ortsgrenze: beim Einfahren schnell auf Innerorts, beim Verlassen sanft auf Außerorts.");
        refreshers.add(SettingsUi.stepper(this, zoom, "Innerorts",
                () -> MapPreferences.settlementZoom(this), v -> MapPreferences.setSettlementZoom(this, v),
                MapPreferences.FOLLOW_MIN_ZOOM, MapPreferences.MAX_ZOOM, 1, v -> "Stufe " + v));
        SettingsUi.divider(this, zoom);
        refreshers.add(SettingsUi.stepper(this, zoom, "Außerorts",
                () -> MapPreferences.outsideZoom(this), v -> MapPreferences.setOutsideZoom(this, v),
                MapPreferences.FOLLOW_MIN_ZOOM, MapPreferences.MAX_ZOOM, 1, v -> "Stufe " + v));
        SettingsUi.button(this, zoom, "Zoom auf Standard zurücksetzen", () -> {
            MapPreferences.setZoom(this, MapPreferences.DEFAULT_ZOOM);
            MapPreferences.setSettlementZoom(this, MapPreferences.DEFAULT_SETTLEMENT_ZOOM);
            MapPreferences.setOutsideZoom(this, MapPreferences.DEFAULT_OUTSIDE_ZOOM);
            refreshAll();
        });

        // --- Warnungen
        LinearLayout warn = SettingsUi.card(this, root, "VERKEHRSWARNUNGEN");
        refreshCameraToggle = SettingsUi.toggle(this, warn, "Feste Blitzer aus Offline-Karte anzeigen", null,
                () -> MapPreferences.speedCameraAlerts(this), on -> MapPreferences.setSpeedCameraAlerts(this, on));
        speedCameraHint = SettingsUi.hint(this, warn, speedCameraHintText());

        // --- Offline-Karte
        LinearLayout offline = SettingsUi.card(this, root, "OFFLINE-KARTE");
        offlineStatus = SettingsUi.text(this, offlineStatusText(), 16,
                GermanyOfflineStore.ready(this) ? GlassSkin.GREEN : 0xffffa06b, true);
        offline.addView(offlineStatus, new LinearLayout.LayoutParams(-1, dp(36)));
        SettingsUi.button(this, offline, "Offline-Karte Deutschland verwalten",
                () -> startActivity(new Intent(this, OfflineMapsActivity.class)));

        SettingsUi.button(this, root, "Zurück", this::finish);
        setContentView(scroll);
    }

    private void refreshAll() {
        for (Runnable r : refreshers) r.run();
        if (refreshColorMode != null) refreshColorMode.run();
    }

    @Override protected void onResume() {
        super.onResume();
        if (refreshColorMode != null) refreshColorMode.run();
        if (refreshCameraToggle != null) {
            refreshCameraToggle.run();
            if (speedCameraHint != null) speedCameraHint.setText(speedCameraHintText());
        }
        if (offlineStatus != null) {
            offlineStatus.setText(offlineStatusText());
            offlineStatus.setTextColor(GermanyOfflineStore.ready(this) ? GlassSkin.GREEN : 0xffffa06b);
        }
    }

    private String speedCameraHintText() {
        if (!GermanyOfflineStore.ready(this))
            return "Benötigt die Offline-Karte Deutschland, sonst werden keine Blitzer angezeigt.";
        return "Feste Blitzer aus dem Deutschland-Paket, oben links mit Entfernung. Daten können fehlen oder veraltet sein.";
    }

    private String offlineStatusText() {
        if (GermanyOfflineStore.ready(this)) return "✓ Offline-Karte Deutschland bereit";
        GermanyOfflineStore.Status s = GermanyOfflineStore.status(this);
        if (s.active) return "Wird aufgebaut: " + s.detail;
        return "Offline-Karte Deutschland noch nicht installiert";
    }

    private static String tiltLabel(int degrees) {
        return degrees + "°" + (degrees == 0 ? "  (von oben)" : degrees == MapPreferences.DEFAULT_TILT ? "  (Standard)" : "");
    }

    private static String heightLabel(int metres) {
        return (metres == 0 ? "flach" : metres + " m") + (metres == MapPreferences.DEFAULT_BUILDING_HEIGHT ? "  (Standard)" : "");
    }

    private int dp(int value) { return SettingsUi.dp(this, value); }
}
