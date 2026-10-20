package de.ronny.pololauncher;

/** File names and identity rules of a USB stick prepared by the Windows downloader (pure logic). */
final class UsbImportLayout {
    /** Folder on the stick; the importer also accepts the files directly in the chosen folder. */
    static final String FOLDER = "PoloMaps";

    private UsbImportLayout() {}

    static String mapName(String region) { return region + "-shortbread-1.0.mbtiles"; }

    static String trafficName(String region) { return region + "-latest.osm.pbf"; }

    /** True for the file names (a map and a traffic source per region) the importer knows. */
    static boolean isExpected(String name, String[] regions) {
        if (name == null) return false;
        for (String region : regions) {
            if (name.equals(mapName(region)) || name.equals(trafficName(region))) return true;
        }
        return false;
    }

    static boolean isFolderName(String name) { return name != null && FOLDER.equalsIgnoreCase(name); }
}
