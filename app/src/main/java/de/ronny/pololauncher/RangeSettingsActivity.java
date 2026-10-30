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
import java.util.Locale;

public final class RangeSettingsActivity extends Activity {
    private TextView rangeInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshRangeInfo(); handler.postDelayed(this, 1000L); }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(GlassSkin.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(24));
        scroll.addView(root);
        root.addView(text("TANK UND REICHWEITE", 25, true));
        rangeInfo = text("", 17, false);
        root.addView(rangeInfo);
        addSlider(root, "Reserve", "reserve_tenths", 0, 100,
                (int) Math.round(RangeStore.reserve(this) * 10), "L");
        root.addView(text("Die Restkilometer verwenden Tankstand und Kilometerstand über CAN sowie ein eigenes adaptives OBD2-Fahrprofil. Die Reserve wird vorher vom Tankinhalt abgezogen.\n\n"
                + "Fehlen kurzzeitig neue Daten, bleibt die letzte gültige Anzeige stehen. Nachtanken wird nach 30 Sekunden bestätigt.", 15, false));
        TextView back = text("Zurück", 20, true);
        back.setPadding(dp(16), dp(14), dp(16), dp(14));
        back.setBackground(GlassSkin.touch(this, false));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(scroll);
        refreshRangeInfo();
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
    private void refreshRangeInfo() {
        if (rangeInfo == null) return;
        VehicleState live = VehicleRepository.snapshot();
        boolean usingObd2 = RangeStore.validConsumption(live.rangeConsumption);
        String source = usingObd2 ? "Aktuelles adaptives OBD2-Fahrprofil"
                : RangeStore.validConsumption(Obd2Runtime.rangeAverage(this))
                ? "Gespeichertes adaptives OBD2-Fahrprofil" : "Wartet auf OBD2-Fahrprofil";
        double consumption = RangeStore.consumption(this, live);
        rangeInfo.setText(String.format(Locale.GERMANY,
                "Berechnung: CAN + OBD2\nVerbrauch: %s l/100 km\nQuelle: %s",
                RangeStore.validConsumption(consumption)
                        ? String.format(Locale.GERMANY, "%.1f", consumption) : "--", source));
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
                    refreshRangeInfo();
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(bar, barParams(56));
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(bold ? GlassSkin.ACCENT : GlassSkin.TEXT);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setPadding(0, dp(10), 0, dp(10));
        return text;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    /** Regler nur ca. 62 % breit, rechts bleibt Platz zum Scrollen. */
    private LinearLayout.LayoutParams barParams(int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.55f), dp(heightDp));
        return p;
    }
}
