package de.ronny.pololauncher;

public final class UsbImportLayoutTest {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("UsbImportLayoutTest: " + msg);
    }

    private static final String[] REGIONS = {
            "baden-wuerttemberg", "bayern", "brandenburg", "hamburg",
            "hessen", "mecklenburg-vorpommern", "niedersachsen", "nordrhein-westfalen",
            "rheinland-pfalz", "saarland", "sachsen-anhalt", "sachsen",
            "schleswig-holstein", "thueringen"};

    private static boolean expected(String name) { return UsbImportLayout.isExpected(name, REGIONS); }

    public static void main(String[] args) {
        check("sachsen-shortbread-1.0.mbtiles".equals(UsbImportLayout.mapName("sachsen")), "map file name");
        check("sachsen-latest.osm.pbf".equals(UsbImportLayout.trafficName("sachsen")), "traffic file name");
        check(expected("bayern-shortbread-1.0.mbtiles"), "known map");
        check(expected("thueringen-latest.osm.pbf"), "known pbf");
        check(!expected("bayern-shortbread-1.0.mbtiles.part"), "part files are ignored");
        check(!expected("germany-latest.osm.pbf"), "unknown region is ignored");
        check(!expected(null), "null");
        int total = 0;
        for (String r : REGIONS) {
            if (expected(UsbImportLayout.mapName(r))) total++;
            if (expected(UsbImportLayout.trafficName(r))) total++;
        }
        check(total == 28, "28 expected names, got " + total);
        check(UsbImportLayout.isFolderName("PoloMaps") && UsbImportLayout.isFolderName("polomaps"), "folder name ignores case");
        check(!UsbImportLayout.isFolderName("Maps"), "other folder");
        System.out.println("UsbImportLayoutTest: all cases passed");
    }
}
