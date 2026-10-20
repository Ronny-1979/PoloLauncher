package de.ronny.pololauncher;

/** Driveable road classes of the Shortbread "streets" layer (property "kind"). */
final class RoadKinds {
    private RoadKinds() {}

    /** Paths, rails, tracks and service roads are deliberately out. */
    static boolean ok(String kind) {
        if (kind == null) return false;
        switch (kind) {
            case "motorway": case "motorway_link":
            case "trunk": case "trunk_link":
            case "primary": case "primary_link":
            case "secondary": case "secondary_link":
            case "tertiary": case "tertiary_link":
            case "unclassified": case "residential": case "living_street": case "minor":
                return true;
            default:
                return false;
        }
    }
}
