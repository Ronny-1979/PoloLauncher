package de.ronny.pololauncher;

/** Setzt aus der Ebenen-Vorlage (assets/offline_style) den fertigen MapLibre-Stil zusammen. Ohne Android. */
final class OfflineStyleComposer {
    static final String BACKGROUND = "#f8f4f0";
    /** Background of the night theme (VersaTiles colorful-dark). */
    static final String BACKGROUND_DARK = "#282725";

    private OfflineStyleComposer() {}

    /** Reiner Zusammenbau ohne Android: Quellen je Paket, Ebenen je Paket aus der Vorlage. */
    static String compose(String glyphUrl, String spriteUrlOrNull, String[] keys, String[] mbtilesUrls,
                          String layersJsonl, String background) {
        StringBuilder out = new StringBuilder(layersJsonl.length() * Math.max(1, keys.length) + 2048);
        out.append("{\"version\":8,\"name\":\"Polo Deutschland Offline\"");
        if (glyphUrl != null) out.append(",\"glyphs\":").append(q(glyphUrl));
        if (spriteUrlOrNull != null) out.append(",\"sprite\":").append(q(spriteUrlOrNull));
        out.append(",\"sources\":{");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) out.append(',');
            // http(s) address = tile template of the local tile server, otherwise an mbtiles:// source
            boolean template = mbtilesUrls[i].startsWith("http");
            out.append(q(sourceId(keys[i]))).append(":{\"type\":\"vector\",")
                    .append(template ? "\"tiles\":[" : "\"url\":").append(q(mbtilesUrls[i]))
                    .append(template ? "]" : "").append(",\"minzoom\":0,\"maxzoom\":14}");
        }
        out.append("},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":")
                .append(q(background)).append("}}");
        // Flaechen aller Pakete zuerst waere schoener bei Ueberlappung, ist aber bei sauber getrennten
        // Geofabrik-Auszuegen nicht noetig; einfache Reihenfolge haelt den Stil klein und schnell.
        for (String key : keys) {
            String src = sourceId(key);
            for (String line : layersJsonl.split("\n")) {
                if (line.isEmpty()) continue;
                out.append(',').append(line.replace("@SRC@", src));
            }
        }
        return out.append("]}").toString();
    }

    /** Stil ohne jede Quelle: prueft nur, ob MapLibre ueberhaupt Stile laden kann. */
    static final String BACKGROUND_ONLY_STYLE = "{\"version\":8,\"name\":\"Polo Probe\",\"sources\":{},"
            + "\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\""
            + BACKGROUND + "\"}}]}";

    /** Entfernt alle Symbolebenen (Schrift/Icons), uebrig bleiben Flaechen und Linien. */
    static String withoutSymbols(String layersJsonl) {
        StringBuilder out = new StringBuilder(layersJsonl.length());
        for (String line : layersJsonl.split("\n")) {
            if (line.isEmpty() || line.contains("\"type\":\"symbol\"")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    static String sourceId(String regionKey) { return "de_" + regionKey.replace('-', '_'); }

    static String q(String value) {
        StringBuilder s = new StringBuilder(value.length() + 8).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') s.append('\\').append(c);
            else if (c == '\n') s.append("\\n");
            else if (c == '\r') s.append("\\r");
            else if (c == '\t') s.append("\\t");
            else s.append(c);
        }
        return s.append('"').toString();
    }

    /** The 3D template stores the default building height (9 m) twice; this sets it to the chosen value. */
    static final String DEFAULT_HEIGHT_FRAGMENT = "[\"to-number\",[\"get\",\"height\"],9],9]";

    static String withBuildingHeight(String template, int metres) {
        int h = Math.max(1, Math.min(60, metres));
        return template.replace(DEFAULT_HEIGHT_FRAGMENT,
                "[\"to-number\",[\"get\",\"height\"]," + h + "]," + h + "]");
    }
}
