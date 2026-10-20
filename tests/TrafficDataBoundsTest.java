package de.ronny.pololauncher;

public final class TrafficDataBoundsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TrafficDataStore.Bounds sachsen = new TrafficDataStore.Bounds(50.17, 51.69, 11.87, 15.04);
        check(sachsen.mightContain(51.30, 13.30, 20_000), "inside region must match");
        check(sachsen.mightContain(51.70, 13.30, 20_000), "20 km border preload must overlap");
        check(!sachsen.mightContain(52.20, 13.30, 20_000), "far position must not activate region");
        TrafficDataStore.Bounds unknown = new TrafficDataStore.Bounds(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        check(unknown.mightContain(50, 10, 20_000), "legacy/unknown bounds remain fail-open");
        System.out.println("TrafficDataBoundsTest: regional preload bounds passed");
    }
}
