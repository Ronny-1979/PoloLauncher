package de.ronny.pololauncher;

public final class MarkerStyleTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        check(MarkerStyle.STANDARD.equals(MarkerStyle.normalize(null)), "null -> standard");
        check(MarkerStyle.STANDARD.equals(MarkerStyle.normalize("")), "empty -> standard");
        check(MarkerStyle.STANDARD.equals(MarkerStyle.normalize("quatsch")), "unknown -> standard");
        check(MarkerStyle.PUMPKIN.equals(MarkerStyle.normalize("kuerbis")), "pumpkin kept");
        check(MarkerStyle.SLEIGH.equals(MarkerStyle.normalize("schlitten")), "sleigh kept");
        check(MarkerStyle.ids().length == 3, "three styles");
        for (String id : MarkerStyle.ids()) {
            int[] s = MarkerStyle.sizeDp(id);
            check(s[0] >= 16 && s[1] >= 16 && s[0] <= 80 && s[1] <= 80, "size sane for " + id);
            check(MarkerStyle.label(id).length() > 3, "label for " + id);
        }
        check(MarkerStyle.rotates(MarkerStyle.SLEIGH), "sleigh rotates");
        check(!MarkerStyle.rotates(MarkerStyle.PUMPKIN) && !MarkerStyle.rotates(MarkerStyle.STANDARD), "others do not rotate");
        System.out.println("MarkerStyleTest: all cases passed");
    }
}
