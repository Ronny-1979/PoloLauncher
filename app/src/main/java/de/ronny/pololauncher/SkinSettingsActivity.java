package de.ronny.pololauncher;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Cockpit themes. Copper remains selected on fresh installation. */
public final class SkinSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CopperSkin.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(CopperSkin.background());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(20));
        scroll.addView(root);
        root.addView(text("SKINS / FARBEN", 26, CopperSkin.CREAM, true));
        root.addView(text("Auswahl antippen. Der Kupfer-Skin ist bei der ersten Installation Standard.",
                15, CopperSkin.MUTED, false));
        for (int i = 0; i < CopperSkin.NAMES.length; i++) {
            final int theme = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), 0, dp(18), 0);
            row.setBackground(CopperSkin.touch(this, theme == CopperSkin.selection(this)));
            TextView swatch = text("●", 25, new int[]{0xfff0be78, 0xff76bfff, 0xff84d9b6, 0xffefa1ba}[i], true);
            row.addView(swatch, new LinearLayout.LayoutParams(dp(42), -2));
            TextView name = text(CopperSkin.NAMES[i], 19, CopperSkin.CREAM, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView check = text(theme == CopperSkin.selection(this) ? "✓ AKTIV" : "AUSWÄHLEN",
                    13, CopperSkin.GOLD, true);
            row.addView(check);
            row.setOnClickListener(v -> {
                CopperSkin.select(this, theme);
                recreate();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(60));
            params.topMargin = dp(9);
            root.addView(row, params);
        }
        TextView back = text("ZURÜCK", 17, CopperSkin.CREAM, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(CopperSkin.touch(this, false));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bottom = new LinearLayout.LayoutParams(-1, dp(52));
        bottom.topMargin = dp(16);
        root.addView(back, bottom);
        setContentView(scroll);
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
