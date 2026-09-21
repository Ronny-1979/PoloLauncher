package de.ronny.pololauncher;

public final class DiagLogTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static int lines(String text) {
        if (text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return count;
    }

    public static void main(String[] args) {
        DiagLog.clear();
        for (int i = 0; i < 1_500; i++) DiagLog.add("Meldung " + i);
        check(lines(DiagLog.text()) == 1_200, "Internal diagnostic ring remains bounded");
        check(lines(DiagLog.text(600)) == 600, "Visible diagnostic text is bounded independently");
        check(DiagLog.text(1).contains("Meldung 1499"), "Newest line remains first");

        DiagLog.clear();
        DiagLog.add("x".repeat(2_000));
        String bounded = DiagLog.text();
        check(bounded.contains("[gekürzt]") && bounded.length() < 1_300,
                "Single oversized diagnostic line is truncated");
        System.out.println("DiagLogTest: all cases passed");
    }
}
