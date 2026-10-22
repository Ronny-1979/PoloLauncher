package de.ronny.pololauncher;

public final class ObdHistoryTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        ObdHistory h = new ObdHistory(3);
        check(h.dump().contains("noch keine"), "empty text");
        h.add(0L, "a"); h.add(1000L, "b"); h.add(2000L, "c"); h.add(3000L, "d");
        check(h.size() == 3, "ring keeps max entries");
        check(!h.dump().contains("  a\n") && h.dump().contains("  d\n"), "oldest dropped");
        ObdHistory r = new ObdHistory(5);
        r.add(0L, "hängt"); r.add(1000L, "hängt"); r.add(2000L, "hängt");
        check(r.size() == 1, "repeats are merged");
        check(r.dump().contains("(x3)"), "repeat counter shown: " + r.dump());
        r.add(3000L, "verbunden");
        check(r.size() == 2, "new text starts a new line");
        r.add(4000L, "hängt");
        check(r.size() == 3 && !r.dump().contains("(x4)"), "counter restarts after other text");
        r.add(5000L, null);
        check(r.size() == 3, "null ignored");
        System.out.println("ObdHistoryTest: all cases passed");
    }
}
