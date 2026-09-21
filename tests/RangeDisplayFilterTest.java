package de.ronny.pololauncher;

public final class RangeDisplayFilterTest {
    public static void main(String[] args) {
        RangeDisplayFilter f = new RangeDisplayFilter(300);
        check(f.update(180, 1000) == 300, "Restore display, not startup dip");
        check(f.update(190, 5000) == 300, "Brief dip held");
        check(f.update(300, 5500) == 300, "Recovery has no visible bounce");
        check(f.update(299, 6000) == 299, "Ordinary countdown direct");
        f.update(180, 6500);
        check(f.update(180, 11000) == 299, "Five second confirmation");
        check(f.update(180, 11500) == 298, "Limited downward rate");
        check(f.update(180, 3600000) == 294, "Long gap cannot produce jump");
        check(f.update(-1, 3600500) == -1, "Invalid data not live");
        check(f.update(180, 3601000) == 294, "Outage restarts confirmation");
        check(f.update(19, 3601500) == 19, "Low range never hidden");
        RangeDisplayFilter refill = new RangeDisplayFilter(100);
        refill.update(500, 0);
        int value = 100;
        for (int i = 1; i <= 220; i++) value = refill.update(500, i * 1000L);
        check(value == 500, "Persistent refill eventually displayed");
        RangeDisplayFilter first = new RangeDisplayFilter(-1);
        check(first.update(280, 100) == 280, "No cache: first valid value direct");
        System.out.println("RangeDisplayFilterTest: all cases passed");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
