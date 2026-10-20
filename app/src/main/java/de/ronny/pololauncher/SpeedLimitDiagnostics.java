package de.ronny.pololauncher;


/** Cross-activity snapshot explaining why the offline speed-limit sign is or is not visible. */
final class SpeedLimitDiagnostics {
    private static volatile Snapshot state = Snapshot.initial();

    private SpeedLimitDiagnostics() {}

    static void capability(boolean supported, String source) {
        String label = source == null || source.isEmpty() ? "unbekannt" : source;
        state = supported
                ? new Snapshot(true, true, label, "wartet auf GPS/Positionsprüfung",
                    Double.NaN, Double.NaN, "", "", SpeedLimitValue.UNKNOWN)
                : new Snapshot(true, false, label,
                    "keine Tempolimitdaten installiert/verfügbar",
                    Double.NaN, Double.NaN, "", "", SpeedLimitValue.UNKNOWN);
    }

    static void lookup(double nearestRoadMetres, double maximumDistance,
                       String roadType, String rawSpeedTag, int resolvedSpeed,
                       boolean matchedRoad) {
        Snapshot old = state;
        String status;
        if (!matchedRoad) {
            status = Double.isFinite(nearestRoadMetres)
                    ? "keine passende Straße im Suchradius"
                    : "keine befahrbare Straße in den gelesenen Tiles";
        } else if (resolvedSpeed == SpeedLimitValue.UNKNOWN) {
            status = "Straße gefunden, aber kein nutzbares maxspeed";
        } else if (resolvedSpeed == SpeedLimitValue.UNRESTRICTED) {
            status = "Straße gefunden, unbeschränkt";
        } else {
            status = "Tempolimit erkannt";
        }
        state = new Snapshot(old.supportKnown, old.supported, old.source, status,
                nearestRoadMetres, maximumDistance,
                roadType == null ? "" : roadType,
                rawSpeedTag == null ? "" : rawSpeedTag,
                resolvedSpeed);
    }

    static void error(String type) {
        Snapshot old = state;
        state = new Snapshot(old.supportKnown, old.supported, old.source,
                "Lesefehler" + (type == null || type.isEmpty() ? "" : " (" + type + ")"),
                Double.NaN, Double.NaN, "", "", SpeedLimitValue.UNKNOWN);
    }

    static String summary() {
        Snapshot s = state;
        StringBuilder out = new StringBuilder("Tempolimit: Daten verfügbar ");
        out.append(!s.supportKnown ? "UNBEKANNT" : s.supported ? "JA" : "NEIN");
        out.append(" | Quelle=").append(s.source);
        out.append(" | ").append(s.status);
        if (Double.isFinite(s.nearestRoadMetres))
            out.append(" | nächste Straße=").append(Math.round(s.nearestRoadMetres)).append(" m");
        if (Double.isFinite(s.maximumDistance))
            out.append(" | Suchradius=").append(Math.round(s.maximumDistance)).append(" m");
        if (!s.roadType.isEmpty()) out.append(" | Straße=").append(s.roadType);
        if (!s.rawSpeedTag.isEmpty()) out.append(" | OSM ").append(s.rawSpeedTag);
        out.append(" | erkannt=").append(formatSpeed(s.resolved));
        return out.toString();
    }

    private static String formatSpeed(int value) {
        if (value == SpeedLimitValue.UNKNOWN) return "--";
        if (value == SpeedLimitValue.UNRESTRICTED) return "unbeschränkt";
        return value + " km/h";
    }

    private static final class Snapshot {
        final boolean supportKnown, supported;
        final String source, status, roadType, rawSpeedTag;
        final double nearestRoadMetres, maximumDistance;
        final int resolved;

        Snapshot(boolean supportKnown, boolean supported, String source, String status,
                 double nearestRoadMetres, double maximumDistance,
                 String roadType, String rawSpeedTag, int resolved) {
            this.supportKnown = supportKnown;
            this.supported = supported;
            this.source = source;
            this.status = status;
            this.nearestRoadMetres = nearestRoadMetres;
            this.maximumDistance = maximumDistance;
            this.roadType = roadType;
            this.rawSpeedTag = rawSpeedTag;
            this.resolved = resolved;
        }

        static Snapshot initial() {
            return new Snapshot(false, false, "--", "Analyzer noch nicht gestartet",
                    Double.NaN, Double.NaN, "", "", SpeedLimitValue.UNKNOWN);
        }
    }
}
