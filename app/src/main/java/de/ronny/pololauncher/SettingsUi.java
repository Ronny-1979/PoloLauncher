package de.ronny.pololauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Shared, deliberately simple controls for all settings screens.
 * Nothing here reacts to dragging: values change only by a deliberate tap on a large button,
 * so scrolling through a menu on the radio can never move a value by accident
 * (no sliders, no swipe switches).
 */
final class SettingsUi {
    private SettingsUi() {}

    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    /** Screen title. */
    static TextView title(Context c, String value) {
        TextView v = text(c, value, 24, GlassSkin.TEXT, true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(c, 2), 0, 0, 0);
        return v;
    }

    /** A grouped panel with a small heading; add the controls to the returned layout. */
    static LinearLayout card(Context c, LinearLayout parent, String heading) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 10));
        card.setBackground(GlassSkin.panel(c, false));
        if (heading != null && !heading.isEmpty()) {
            TextView h = text(c, heading, 13, GlassSkin.ACCENT, true);
            h.setPadding(0, dp(c, 2), 0, dp(c, 4));
            card.addView(h, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(c, 10);
        parent.addView(card, p);
        return card;
    }

    /** Small explanation under a control. */
    static TextView hint(Context c, LinearLayout parent, String value) {
        TextView v = text(c, value, 12, GlassSkin.MUTED, false);
        v.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 4));
        parent.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    private static TextView stepButton(Context c, String sign) {
        TextView b = text(c, sign, 28, GlassSkin.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(GlassSkin.touch(c, false));
        b.setContentDescription(sign.equals("+") ? "Mehr" : "Weniger");
        return b;
    }

    /**
     * Value with large minus/plus buttons. The shown value is read back after every tap, so it always
     * reflects what is really stored. Returns a refresh action for programmatic changes (reset buttons).
     */
    static Runnable stepper(Context c, LinearLayout parent, String label, IntSupplier get, IntConsumer set,
                            int min, int max, int step, IntFunction<String> format) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(c, label, 13, GlassSkin.MUTED, true);
        TextView value = text(c, "", 19, GlassSkin.TEXT, true);
        left.addView(name);
        left.addView(value);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView minus = stepButton(c, "−");
        TextView plus = stepButton(c, "+");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(c, 72), dp(c, 52));
        bp.leftMargin = dp(c, 10);
        row.addView(minus, bp);
        row.addView(plus, new LinearLayout.LayoutParams(bp));
        Runnable refresh = () -> {
            int v = get.getAsInt();
            value.setText(format.apply(v));
            minus.setAlpha(v <= min ? 0.35f : 1f);
            plus.setAlpha(v >= max ? 0.35f : 1f);
        };
        minus.setOnClickListener(v -> {
            int next = Math.max(min, get.getAsInt() - step);
            set.accept(next);
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            int next = Math.min(max, get.getAsInt() + step);
            set.accept(next);
            refresh.run();
        });
        refresh.run();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(c, 64));
        parent.addView(row, rp);
        return refresh;
    }

    /** One tap on the whole row switches it. Shows AN/AUS; no dragging involved. */
    static Runnable toggle(Context c, LinearLayout parent, String label, String sub,
                           java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 4), 0, dp(c, 4));
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(c, label, 16, GlassSkin.TEXT, true);
        left.addView(name);
        if (sub != null && !sub.isEmpty()) left.addView(text(c, sub, 12, GlassSkin.MUTED, false));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView state = text(c, "", 16, GlassSkin.TEXT, true);
        state.setGravity(Gravity.CENTER);
        row.addView(state, new LinearLayout.LayoutParams(dp(c, 84), dp(c, 46)));
        Runnable refresh = () -> {
            boolean on = get.getAsBoolean();
            state.setText(on ? "AN" : "AUS");
            state.setTextColor(on ? GlassSkin.GREEN : GlassSkin.MUTED);
            state.setBackground(GlassSkin.touch(c, on));
        };
        row.setOnClickListener(v -> {
            set.accept(!get.getAsBoolean());
            refresh.run();
        });
        refresh.run();
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return refresh;
    }

    /** Row of equal buttons; exactly one is selected. Tap only. */
    static Runnable choice(Context c, LinearLayout parent, String label, String[] titles, String[] keys,
                           java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        if (label != null && !label.isEmpty()) {
            TextView name = text(c, label, 13, GlassSkin.MUTED, true);
            name.setPadding(0, dp(c, 4), 0, dp(c, 4));
            parent.addView(name);
        }
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] buttons = new TextView[keys.length];
        Runnable refresh = () -> {
            String current = get.get();
            for (int i = 0; i < buttons.length; i++) {
                boolean on = keys[i].equals(current);
                buttons[i].setBackground(GlassSkin.touch(c, on));
                buttons[i].setTextColor(on ? GlassSkin.GREEN : GlassSkin.TEXT);
            }
        };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            TextView b = text(c, titles[i], 16, GlassSkin.TEXT, true);
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> { set.accept(key); refresh.run(); });
            buttons[i] = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, 52), 1f);
            if (i > 0) p.leftMargin = dp(c, 8);
            row.addView(b, p);
        }
        refresh.run();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(c, 4);
        parent.addView(row, rp);
        return refresh;
    }

    /** Full-width tap button. */
    static TextView button(Context c, LinearLayout parent, String label, Runnable action) {
        TextView b = text(c, label, 16, GlassSkin.TEXT, true);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setBackground(GlassSkin.touch(c, false));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(c, 54));
        p.topMargin = dp(c, 8);
        parent.addView(b, p);
        return b;
    }

    static View divider(Context c, LinearLayout parent) {
        View v = new View(c);
        v.setBackgroundColor(0x22ffffff);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 1);
        p.topMargin = dp(c, 4);
        p.bottomMargin = dp(c, 4);
        parent.addView(v, p);
        return v;
    }
}
