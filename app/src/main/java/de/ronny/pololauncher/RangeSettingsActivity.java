package de.ronny.pololauncher;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Switch;
import java.util.Locale;

public final class RangeSettingsActivity extends Activity {
    private TextView learningInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshLearningInfo(); handler.postDelayed(this, 1000L); }
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
        root.addView(text("TANK UND REICHWEITE", 25, true));
        Switch automatic = new Switch(this);
        automatic.setText("Verbrauch automatisch lernen");
        automatic.setTextSize(18);
        automatic.setTextColor(CopperSkin.CREAM);
        automatic.setChecked(RangeStore.automaticLearning(this));
        automatic.setOnCheckedChangeListener((button, enabled) -> {
            RangeStore.setAutomaticLearning(this, enabled);
            refreshLearningInfo();
        });
        root.addView(automatic, new LinearLayout.LayoutParams(-1, dp(56)));
        Switch includeObd2 = new Switch(this);
        includeObd2.setText("Reichweite: CAN + OBD2 (statt nur CAN)");
        includeObd2.setTextSize(18);
        includeObd2.setTextColor(CopperSkin.CREAM);
        includeObd2.setChecked(RangeStore.includeObd2InRange(this));
        includeObd2.setOnCheckedChangeListener((button, enabled) -> {
            RangeStore.setIncludeObd2InRange(this, enabled);
            refreshLearningInfo();
        });
        root.addView(includeObd2, new LinearLayout.LayoutParams(-1, dp(56)));
        learningInfo = text("", 16, false);
        root.addView(learningInfo);
        addSlider(root, "Grundverbrauch (Start / manuell)", "consumption_tenths", 30, 200,
                (int) Math.round(RangeStore.manualConsumption(this) * 10), "l/100 km");
        addSlider(root, "Reserve", "reserve_tenths", 0, 100,
                (int) Math.round(RangeStore.reserve(this) * 10), "L");
        root.addView(text("Standard: 6,8 l/100 km und 2,0 L Reserve. Die Reserve wird von der geschätzten Reichweite abgezogen.\n\n"
                + "Der Tankwert kommt über CAN (Ganzliter-Wert), da dieses Fahrzeug keine OBD2-Tankstandsanzeige unterstützt. Der Kilometerstand zählt die Reichweite zwischen den Tankmeldungen herunter; kleine Schwankungen werden abgefedert, Nachtanken wird nach 30 Sekunden bestätigt.\n\n"
                + "Mit „CAN + OBD2“ wird der kumulative OBD2 Ø-Verbrauch für die Reichweite verwendet. Auch ohne neue Adapterdaten und nach einem Neustart bleibt der gespeicherte OBD-Durchschnitt die Grundlage. Ohne brauchbaren gespeicherten Durchschnitt wartet die Berechnung; sie wechselt nicht auf den CAN-Grundverbrauch. Bei ausgeschaltetem Schalter wird der CAN-Lernwert beziehungsweise der eingestellte Grundverbrauch verwendet.\n\n"
                + "Die CAN-Lernautomatik läuft dabei unverändert im Hintergrund weiter, auch wenn OBD2 gerade die Reichweite bestimmt – sie bleibt so als Rückfallwert bereit, sobald OBD2 mal nicht verfügbar ist. Ein CAN-Tankwert muss 30 Sekunden stabil bleiben; erst ab mindestens 100 km und 5 L Tankabnahme wird der CAN-Lernwert vorsichtig angepasst. Größere Schwankungen, unplausible Daten und Nachtanken werden nicht als Verbrauch gelernt.\n\n"
                + "CAN-Lernwert und Fahrabschnitt bleiben über Neustarts erhalten; der OBD2-Ø-Verbrauch ebenso (Einstellungen → OBD-Verbindung, dort separat zurücksetzbar). Ganze Liter und kleine unerkannte Nachfüllmengen begrenzen die Genauigkeit der Reichweite. Ohne frische CAN-Daten bleiben die zuletzt gespeicherten Tankliter und Restkilometer stehen; die CAN-Lernfunktion pausiert.", 16, false));
        TextView reset = text("Verbrauchs-Lernwerte zurücksetzen", 19, true);
        reset.setPadding(dp(16), dp(14), dp(16), dp(14));
        reset.setBackground(CopperSkin.touch(this, false));
        reset.setOnClickListener(v -> CopperSkin.dialog(this)
                .setTitle("Lernwerte zurücksetzen?")
                .setMessage("Der gelernte Verbrauch und der aktuelle Lernabschnitt werden gelöscht. Der eingestellte Grundverbrauch und die Reserve bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Zurücksetzen", (dialog, which) -> {
                    RangeStore.resetLearning(this);
                    refreshLearningInfo();
                }).show());
        root.addView(reset);
        TextView back = text("Zurück", 20, true);
        back.setPadding(dp(16), dp(14), dp(16), dp(14));
        back.setBackground(CopperSkin.touch(this, false));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(scroll);
        refreshLearningInfo();
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
    private void refreshLearningInfo() {
        if (learningInfo == null) return;
        boolean automatic = RangeStore.automaticLearning(this);
        boolean includeObd2 = RangeStore.includeObd2InRange(this);
        VehicleState live = VehicleRepository.snapshot();
        boolean usingObd2 = includeObd2 && ConsumptionLearner.validRate(live.avgConsumption);
        String source = includeObd2 ? (usingObd2 ? "OBD2 Ø-Verbrauch"
                : ConsumptionLearner.validRate(Obd2Runtime.rangeAverage(this))
                ? "Gespeicherter OBD2 Ø-Verbrauch" : "Wartet auf OBD2 Ø-Verbrauch")
                : automatic ? "Automatik (CAN gelernt)" : "Manueller Verbrauch";
        if (!includeObd2) source += " – OBD2 in Einstellungen abgewählt";
        learningInfo.setText(String.format(Locale.GERMANY,
                "Für die Reichweite: %s l/100 km (%s)\nBestätigte CAN-Lernabschnitte: %d\n%s",
                ConsumptionLearner.validRate(RangeStore.consumption(this, live))
                ? String.format(Locale.GERMANY, "%.1f", RangeStore.consumption(this, live)) : "--", source,
                RangeStore.learnedWindows(this), RangeStore.learningStatus(this)));
    }

    private void addSlider(LinearLayout root, String title, String key, int min, int max, int value, String unit) {
        TextView label = text("", 20, true);
        root.addView(label);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        label.setText(String.format(Locale.GERMANY, "%s: %.1f %s", title, value / 10d, unit));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean user) {
                int current = min + progress;
                label.setText(String.format(Locale.GERMANY, "%s: %.1f %s", title, current / 10d, unit));
                if (user) {
                    RangeStore.preferences(RangeSettingsActivity.this).edit().putInt(key, current).apply();
                    refreshLearningInfo();
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(bold ? CopperSkin.GOLD : CopperSkin.CREAM);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setPadding(0, dp(10), 0, dp(10));
        return text;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
