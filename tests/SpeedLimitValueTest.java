package de.ronny.pololauncher;

public final class SpeedLimitValueTest {
    private static void eq(int expected, int actual, String message) {
        if (expected != actual)
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        eq(50, SpeedLimitValue.parse("50"), "plain km/h");
        eq(30, SpeedLimitValue.parse("30 km/h"), "km/h suffix");
        eq(80, SpeedLimitValue.parse("50 mph"),
                "settlement logic must not mistake 50 mph for an urban 50 km/h road");
        eq(50, SpeedLimitValue.parse("DE:urban"), "German urban default");
        eq(100, SpeedLimitValue.parse("DE:rural"), "German rural default");
        eq(30, SpeedLimitValue.parse("DE:zone:30"), "German zone notation");
        eq(30, SpeedLimitValue.parse("DE:30"), "zone:maxspeed German notation");
        eq(30, SpeedLimitValue.parse("DE:zone30"), "German compact zone notation");
        eq(30, SpeedLimitValue.resolve("", "", "", "", "", "DE:zone30", false, true),
                "maxspeed:type German zone fallback");
        eq(SpeedLimitValue.UNRESTRICTED, SpeedLimitValue.parse("none"),
                "explicit unrestricted");
        eq(SpeedLimitValue.UNKNOWN, SpeedLimitValue.parse("50;70"),
                "ambiguous compound value is not guessed");
        eq(SpeedLimitValue.UNKNOWN, SpeedLimitValue.parse("signals"),
                "variable/signal limit is not guessed");

        eq(70, SpeedLimitValue.resolve("50", "70", "30", "", "", "", true, true),
                "forward directional limit wins");
        eq(30, SpeedLimitValue.resolve("50", "70", "30", "", "", "", true, false),
                "backward directional limit wins");
        eq(50, SpeedLimitValue.resolve("50", "70", "30", "", "", "", false, true),
                "without known direction base maxspeed is used");
        eq(50, SpeedLimitValue.resolve("", "", "", "", "DE:urban", "", false, true),
                "standard source:maxspeed can provide safe fallback");

        System.out.println("SpeedLimitValueTest: OSM maxspeed parsing and direction passed");
    }
}
