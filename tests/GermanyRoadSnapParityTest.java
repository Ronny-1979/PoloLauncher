package de.ronny.pololauncher;

public final class GermanyRoadSnapParityTest {
    public static void main(String[] args) {
        check(18d, GermanyRoadPositionMatcher.maximumSnapDistance(0f));
        check(18d, GermanyRoadPositionMatcher.maximumSnapDistance(5f));
        check(23d, GermanyRoadPositionMatcher.maximumSnapDistance(10f));
        check(35d, GermanyRoadPositionMatcher.maximumSnapDistance(18f));
        check(35d, GermanyRoadPositionMatcher.maximumSnapDistance(45f));
        System.out.println("GermanyRoadSnapParityTest: v0.31.49 snap envelope restored");
    }
    private static void check(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.001d)
            throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
}
