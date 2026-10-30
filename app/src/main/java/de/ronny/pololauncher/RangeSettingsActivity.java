package de.ronny.pololauncher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        root.setPadding(dp(24), dp(14), dp(24), dp(24));
        scroll.addView(root);
        root.addView(SettingsUi.title(this, "TANK UND REICHWEITE"), new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout reserve = SettingsUi.card(this, root, "RESERVE");
        SettingsUi.stepper(this, reserve, "Reserve (wird vom Tankinhalt abgezogen)",
                () -> (int) Math.round(RangeStore.reserve(this) * 10),
                v -> {
                    RangeStore.preferences(this).edit().putInt("reserve_tenths", v).apply();
                    refreshRangeInfo();
                }, 0, 100, 5, v -> String.format(Locale.GERMANY, "%.1f L", v / 10d));

        LinearLayout info = SettingsUi.card(this, root, "AKTUELLE BERECHNUNG");
        rangeInfo = SettingsUi.text(this, "", 15, GlassSkin.TEXT, false);
        info.addView(rangeInfo, new LinearLayout.LayoutParams(-1, -2));
        SettingsUi.hint(this, info, "Die Restkilometer nutzen Tankstand und Kilometerstand über CAN sowie das adaptive OBD2-Fahrprofil. "
                + "Fehlen kurz neue Daten, bleibt die letzte gültige Anzeige stehen. Nachtanken wird nach 30 Sekunden bestätigt.");

        SettingsUi.button(this, root, "Zurück", this::finish);
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
