package de.ronny.pololauncher;

public class FuelPriceLogicTest {
    static int fails;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } }
    public static void main(String[] a) {
        check(FuelPriceLogic.matches(0, "TS am Marktkauf", "", "Oschatz"), "marktkauf name");
        check(FuelPriceLogic.matches(0, "Tankstelle", "Marktkauf", "OSCHATZ"), "marktkauf brand");
        check(!FuelPriceLogic.matches(0, "TS am Marktkauf", "", "Wurzen"), "marktkauf other town");
        check(!FuelPriceLogic.matches(0, "ARAL", "ARAL", "Oschatz"), "aral not marktkauf");
        check(FuelPriceLogic.matches(1, "HEM Tankstelle", "HEM", "Dahlen"), "hem");
        check(FuelPriceLogic.matches(1, "HEM", "HEM", "Dahlen"), "hem plain");
        check(!FuelPriceLogic.matches(1, "Schlehmann Tank", "", "Dahlen"), "hem substring only");
        check(!FuelPriceLogic.matches(1, "HEM", "HEM", "Oschatz"), "hem wrong town");
        check(FuelPriceLogic.price(1.659d).equals("1,65 €"), "format " + FuelPriceLogic.price(1.659d));
        check(FuelPriceLogic.price(2.199d).equals("2,19 €"), "cut 2.199");
        check(FuelPriceLogic.price(1.700d).equals("1,70 €"), "exact 1.70");
        check(FuelPriceLogic.price(Double.NaN).equals("--"), "nan");
        check(FuelPriceLogic.price(0d).equals("--"), "zero");
        check(FuelPriceLogic.cheaper(1.659d, 1.699d) == 0, "a cheaper");
        check(FuelPriceLogic.cheaper(1.759d, 1.699d) == 1, "b cheaper");
        check(FuelPriceLogic.cheaper(1.699d, 1.699d) == -1, "equal");
        check(FuelPriceLogic.cheaper(Double.NaN, 1.699d) == -1, "nan");
        System.out.println(fails == 0 ? "FuelPriceLogicTest OK" : "FuelPriceLogicTest FAILED");
        if (fails != 0) System.exit(1);
    }
}
