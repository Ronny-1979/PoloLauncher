package de.ronny.pololauncher;

public final class AverageDisplayFilterTest {
    public static void main(String[] args) {
        Obd2ConsumptionAverager a = new Obd2ConsumptionAverager();
        a.restore(0.06, 0.999);
        check(Double.isNaN(a.reliableValue()), "Short distance not mature");
        a.restore(0.06, 1);
        check(Math.abs(a.reliableValue() - 6) < 1e-8, "One km ready");
        a.add(0.8, 0, 5);
        check(a.reliableValue() > 6 && a.weightedKm() == 1, "Idle fuel remains counted");
        Obd2ConsumptionAverager restored = new Obd2ConsumptionAverager();
        restored.restore(a.weightedLiters(), a.weightedKm());
        check(restored.reliableValue() == a.reliableValue(), "Restart retains mature totals");
        AverageDisplayFilter f = new AverageDisplayFilter();
        check(f.update(7, 0) == 7, "Initial saved value direct");
        double next = f.update(9, 1000);
        check(next > 7 && next < 7.2, "Gradual change");
        for (int i = 2; i <= 120; i++) next = f.update(9, i * 1000L);
        check(next > 8.99 && next < 9, "Converges, no clipping of idle increase");
        check(Double.isNaN(f.update(Double.NaN, 121000)), "Reset clears display");
        check(f.update(6, 122000) == 6, "New baseline after reset");
        check(f.update(9, 900000) < 6.5, "Pause cannot cause huge step");
        System.out.println("AverageDisplayFilterTest: all cases passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
