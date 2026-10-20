package de.ronny.pololauncher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** Prueft Regionenauswahl und den zusammengebauten Offline-Stil gegen die echten Asset-Vorlagen. */
public final class OfflineStyleTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }

    // ---- minimaler JSON-Parser (nur fuer den Test) ----
    static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        Object parse() { Object v = value(); ws(); check(i == s.length(), "trailing JSON at " + i); return v; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws(); char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = str(); ws(); check(s.charAt(i++) == ':', "colon"); m.put(k, value()); ws();
                    char d = s.charAt(i++); if (d == '}') return m; check(d == ',', "comma in object at " + i); }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(value()); ws(); char d = s.charAt(i++); if (d == ']') return l; check(d == ',', "comma in array at " + i); }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            check(i > st, "bad JSON value at " + st); return Double.valueOf(s.substring(st, i));
        }
        String str() {
            check(s.charAt(i++) == '"', "string start"); StringBuilder b = new StringBuilder();
            while (true) { char c = s.charAt(i++); if (c == '"') return b.toString();
                if (c == '\\') { char e = s.charAt(i++); if (e == 'u') { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    else b.append(e == 'n' ? '\n' : e == 't' ? '\t' : e == 'r' ? '\r' : e); }
                else b.append(c); }
        }
    }

    @SuppressWarnings("unchecked") static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> list(Object o) { return (List<Object>) o; }

    static void strings(Object o, List<String> out) {
        if (o instanceof String) out.add((String) o);
        else if (o instanceof List) for (Object x : list(o)) strings(x, out);
        else if (o instanceof Map) for (Object x : map(o).values()) strings(x, out);
    }

    static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    public static void main(String[] a) throws Exception {
        // --- Regionen ---
        String storeSrc = read(Paths.get("app/src/main/java/de/ronny/pololauncher/GermanyOfflineStore.java"));
        int from = storeSrc.indexOf("REGION_KEYS = {"), to = storeSrc.indexOf("};", from);
        List<String> storeKeys = new ArrayList<>();
        java.util.regex.Matcher km = java.util.regex.Pattern.compile("\"([a-z-]+)\"").matcher(storeSrc.substring(from, to));
        while (km.find()) storeKeys.add(km.group(1));
        check(storeKeys.equals(Arrays.asList(GermanyRegionIndex.KEYS)), "Region keys match the real store: " + storeKeys);
        check(GermanyRegionIndex.select(48.14, 11.58, 0.12, 3).get(0).equals("bayern"), "Muenchen -> Bayern");
        check(GermanyRegionIndex.select(52.52, 13.40, 0.12, 3).get(0).equals("brandenburg"), "Berlin -> Brandenburg");
        List<String> hh = GermanyRegionIndex.select(53.55, 10.00, 0.12, 3);
        check(hh.get(0).equals("hamburg") && hh.contains("niedersachsen") && hh.size() <= 3, "Hamburg first, max 3: " + hh);
        check(GermanyRegionIndex.select(48.85, 2.35, 0.12, 3).isEmpty(), "Paris: no package");
        check(!GermanyRegionIndex.insideGermanyPackages(48.85, 2.35), "Paris outside");
        check(GermanyRegionIndex.insideGermanyPackages(50.0, 8.0), "Rhein-Main inside");
        // Prefix property: need-set (small margin) is always a subset of the load-set (big margin) -> no reload loops.
        for (double lat = 47.3; lat < 55.0; lat += 0.07)
            for (double lon = 5.9; lon < 15.0; lon += 0.09) {
                List<String> load = GermanyRegionIndex.select(lat, lon, 0.12, 3);
                check(GermanyRegionIndex.covered(load, lat, lon, 0.04, 3), "covered right after load at " + lat + "," + lon);
            }

        // --- Vorlagen + Sprite ---
        Path dir = Paths.get("app/src/main/assets/offline_style");
        Map<String, Object> sprite = map(new P(read(dir.resolve("sprite.json"))).parse());
        check(sprite.size() > 100, "sprite has icons: " + sprite.size());
        check(Files.size(dir.resolve("sprite.png")) > 1000 && Files.size(dir.resolve("sprite@2x.png")) > 1000, "sprite pngs present");
        String full = read(dir.resolve("layers_full.jsonl"));
        String full3d = read(dir.resolve("layers_full_3d.jsonl"));
        String lite = read(dir.resolve("layers_lite.jsonl"));
        String fullDark = read(dir.resolve("layers_full_dark.jsonl"));
        String full3dDark = read(dir.resolve("layers_full_3d_dark.jsonl"));
        String liteDark = read(dir.resolve("layers_lite_dark.jsonl"));
        check(fullDark.split("\n").length == full.split("\n").length && full3dDark.split("\n").length == full3d.split("\n").length
                && liteDark.split("\n").length == lite.split("\n").length, "night templates have the same layers as the day templates");
        check(!fullDark.contains("fill-extrusion") && full3dDark.contains(OfflineStyleComposer.DEFAULT_HEIGHT_FRAGMENT), "night 3D variant has the replaceable extrusion");
        check(!fullDark.contains("rgb(248,244,240)") && fullDark.contains("rgb("), "night template carries its own colours");
        check(OfflineStyleComposer.compose(null, null, new String[]{"hamburg"}, new String[]{"mbtiles:///d/h.mbtiles"}, fullDark,
                OfflineStyleComposer.BACKGROUND_DARK).contains("\"background-color\":\"#282725\""), "night background colour");
        check(!full.contains("fill-extrusion") && full3d.contains("fill-extrusion"), "3D variant has the extrusion, flat has none");
        check(full3d.contains(OfflineStyleComposer.DEFAULT_HEIGHT_FRAGMENT), "3D template carries the replaceable default height");
        String tall = OfflineStyleComposer.withBuildingHeight(full3d, 14);
        check(tall.contains("[\"get\",\"height\"],14],14]") && !tall.contains(OfflineStyleComposer.DEFAULT_HEIGHT_FRAGMENT), "building height is set to 14 m");
        map(new P(OfflineStyleComposer.compose("file:///g/{fontstack}/{range}.pbf", "file:///s/sprite", new String[]{"hamburg"},
                new String[]{"mbtiles:///d/hamburg.mbtiles"}, tall, OfflineStyleComposer.BACKGROUND)).parse());
        check(!full.contains("base:") && !full.contains("noto_sans"), "template has no VersaTiles-only references left");
        int fullLayers = full.split("\n").length;
        check(fullLayers > 150 && fullLayers < 260, "full layer count " + fullLayers);
        check(lite.split("\n").length < 30, "lite layer count");

        String[] keys = {"hamburg", "niedersachsen"};
        String[] urls = {"mbtiles:///d/hamburg.mbtiles", "mbtiles:///d/niedersachsen.mbtiles"};
        for (String template : new String[]{full, full3d, lite, fullDark, full3dDark, liteDark}) {
            String json = OfflineStyleComposer.compose("file:///g/{fontstack}/{range}.pbf", "file:///s/sprite", keys, urls, template, OfflineStyleComposer.BACKGROUND);
            check(!json.contains("@SRC@"), "no placeholder left");
            Map<String, Object> style = map(new P(json).parse());
            check(((Double) style.get("version")) == 8d, "style version 8");
            Map<String, Object> sources = map(style.get("sources"));
            check(sources.keySet().equals(new LinkedHashSet<>(Arrays.asList("de_hamburg", "de_niedersachsen"))), "sources " + sources.keySet());
            Set<String> ids = new HashSet<>();
            List<Object> layers = list(style.get("layers"));
            int perRegion = template.split("\n").length;
            check(layers.size() == 1 + 2 * perRegion, "layer count " + layers.size());
            List<String> icons = new ArrayList<>();
            for (Object o : layers) {
                Map<String, Object> l = map(o);
                check(ids.add((String) l.get("id")), "unique layer id " + l.get("id"));
                if ("background".equals(l.get("type"))) continue;
                check(sources.containsKey(l.get("source")), "layer source declared: " + l.get("id"));
                check(((String) l.get("id")).startsWith((String) l.get("source") + ":"), "id prefixed with source");
                Map<String, Object> layout = l.get("layout") == null ? null : map(l.get("layout"));
                if (layout != null && layout.containsKey("text-font"))
                    check(list(layout.get("text-font")).equals(Arrays.asList("Open Sans Regular", "Arial Unicode MS Regular")),
                            "font stack matches the downloaded glyphs: " + l.get("id"));
                if (layout != null && layout.containsKey("icon-image")) strings(layout.get("icon-image"), icons);
            }
            // Jedes Symbol, das der Stil nennt, muss im Sprite existieren.
            for (String name : icons) {
                if (name.isEmpty() || name.equals("match") || name.equals("get") || name.equals("case") || name.equals("coalesce")) continue;
                if (name.matches("[a-z_]+") && !name.contains("-")) continue;       // property names (amenity, shop ...)
                if (name.matches("[a-z_]+")) continue;
                check(sprite.containsKey(name), "sprite has icon '" + name + "'");
            }
        }
        // Glyph-/Sprite-Url landen im JSON, ohne Sprite fehlt der Schluessel.
        String noSprite = OfflineStyleComposer.compose("file:///g/{fontstack}/{range}.pbf", null, keys, urls, lite, OfflineStyleComposer.BACKGROUND);
        check(!noSprite.contains("\"sprite\""), "sprite key omitted when unavailable");
        // Rueckfallstufen: ohne Symbole kein symbol-Layer, ohne Glyphen kein "glyphs"-Schluessel, Probe ist gueltig.
        String flat = OfflineStyleComposer.withoutSymbols(full);
        check(!flat.contains("\"type\":\"symbol\"") && flat.split("\n").length > 100, "fallback template has no symbol layers");
        String minimal = OfflineStyleComposer.compose(null, null, new String[]{"hamburg"}, new String[]{"mbtiles:///d/h.mbtiles"}, flat, OfflineStyleComposer.BACKGROUND);
        Map<String, Object> ms = map(new P(minimal).parse());
        check(!ms.containsKey("glyphs") && !ms.containsKey("sprite"), "minimal style needs neither glyphs nor sprite");
        for (Object o : list(ms.get("layers"))) check(!"symbol".equals(map(o).get("type")), "no symbol in minimal style");
        Map<String, Object> probe = map(new P(OfflineStyleComposer.BACKGROUND_ONLY_STYLE).parse());
        check(map(probe.get("sources")).isEmpty() && list(probe.get("layers")).size() == 1, "probe style is background only");
        check(minimal.contains("\"url\":\"mbtiles:///d/h.mbtiles\""), "mbtiles url keeps the three-slash form");
        // Kachelserver-Variante: Quelle mit tiles-Vorlage statt url
        String http = OfflineStyleComposer.compose("http://127.0.0.1:1/f/glyphs/{fontstack}/{range}.pbf", "http://127.0.0.1:1/f/sprites/sprite",
                new String[]{"hamburg"}, new String[]{"http://127.0.0.1:1/t/hamburg/{z}/{x}/{y}.pbf"}, full, OfflineStyleComposer.BACKGROUND);
        Map<String, Object> hs = map(new P(http).parse());
        Map<String, Object> src = map(map(hs.get("sources")).get("de_hamburg"));
        check(list(src.get("tiles")).equals(Arrays.asList("http://127.0.0.1:1/t/hamburg/{z}/{x}/{y}.pbf")) && !src.containsKey("url"),
                "local server source uses a tiles template");
        System.out.println("OfflineStyleTest: OK (" + fullLayers + " layers per package, " + sprite.size() + " sprite icons)");
    }
}
