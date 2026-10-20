package de.ronny.pololauncher;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.TextView;

/**
 * Zahlenwert mit kleiner Einheit: "1850 U/min" erscheint als große Zahl mit verkleinerter
 * Einheit. Der Text selbst bleibt unverändert ("1850 U/min"), nur die Darstellung ändert sich;
 * dadurch funktionieren Vergleiche, Barrierefreiheitstexte und Tests wie bei einem normalen TextView.
 */
final class UnitValueView extends TextView {
    private final float unitScale;

    UnitValueView(Context context, float unitScale) {
        super(context);
        this.unitScale = unitScale;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (text == null) {
            super.setText(null, type);
            return;
        }
        int split = indexOfUnit(text);
        if (split < 0) {
            super.setText(text, type);
            return;
        }
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new RelativeSizeSpan(unitScale), split, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        super.setText(styled, BufferType.SPANNABLE);
    }

    /** Index des ersten Leerzeichens nach der Zahl, oder -1 wenn keine Einheit folgt. */
    static int indexOfUnit(CharSequence text) {
        for (int i = 1; i < text.length() - 1; i++) {
            if (text.charAt(i) == ' ') return i;
        }
        return -1;
    }
}
