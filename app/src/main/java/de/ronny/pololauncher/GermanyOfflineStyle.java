package de.ronny.pololauncher;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline-Kartenstil fuer die Geofabrik-Shortbread-Pakete.
 *
 * Die Ebenen stammen aus dem VersaTiles-Stil "freedom" (MIT-Lizenz, OSM-Liberty-Optik), gekuerzt und
 * von tools/offline-style/build.mjs als Vorlage nach assets/offline_style erzeugt. Die Vorlage enthaelt
 * den Platzhalter @SRC@ fuer die Quelle; hier wird sie pro eingebundenem Bundesland eingesetzt.
 * Icons (Maki/Temaki, CC0) kommen aus einem lokalen Sprite, Schrift aus den lokalen Glyphen.
 */
final class GermanyOfflineStyle {
        /** Hoechstens so viele Bundeslaender gleichzeitig (jedes bringt ~200 Ebenen mit). */
    static final int MAX_REGIONS = 3;
    /** Puffer, mit dem eingebunden wird, und kleinerer Puffer, ab dem nachgeladen werden muss. */
    static final double LOAD_MARGIN_DEG = 0.12d;
    static final double NEED_MARGIN_DEG = 0.04d;

    private static final String ASSET_DIR = "offline_style/";
    private static final String[] SPRITE_FILES = {"sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png"};
    private static final java.util.Map<String, String> templates = new java.util.HashMap<>();

    /** Ergebnis: fertiger Stil-JSON plus die tatsaechlich eingebundenen Pakete. */
    static final class Built {
        final String json;
        final List<String> regions;
        final boolean lite;
        final int layerCount;
        Built(String json, List<String> regions, boolean lite, int layerCount) {
            this.json = json; this.regions = regions; this.lite = lite; this.layerCount = layerCount;
        }
    }

    private GermanyOfflineStyle() {}

    /**
     * @param level 0 = voller Stil, 1 = ohne Sprite (Icons), 2 = nur Flaechen und Linien, ohne Schrift und
     *              Icons, nur das naechste Paket. Stufen 1 und 2 sind Rueckfallebenen, falls Sprite oder
     *              Schrift das Laden verhindern.
     * @param serverBase null = MapLibre opens the MBTiles files itself (mbtiles://), otherwise the base address
     *                   of the {@link LocalTileServer} (http://127.0.0.1:port), which then also serves glyphs and sprite
     */
    static Built build(Context context, boolean hasPosition, double lat, double lon, int buildingHeightM, boolean night, int level,
                       String serverBase) throws IOException {
        List<String> keys = new ArrayList<>();
        boolean lite = !hasPosition || !GermanyRegionIndex.insideGermanyPackages(lat, lon);
        if (lite) {
            for (String key : GermanyOfflineStore.REGION_KEYS) keys.add(key);
        } else {
            keys.addAll(GermanyRegionIndex.select(lat, lon, LOAD_MARGIN_DEG, MAX_REGIONS));
        }
        List<String> keep = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (String key : keys) {
            File file = GermanyOfflineStore.mapFile(context, key);
            if (file == null || !file.isFile() || file.length() < 1024) continue;
            keep.add(key);
            // MapLibre expects the scheme followed by the absolute path (three slashes in total).
            // A file:// URI after the scheme cannot be opened: the style then never finishes loading.
            urls.add(serverBase == null ? "mbtiles://" + file.getAbsolutePath()
                    : serverBase + "/t/" + key + "/{z}/{x}/{y}.pbf");
        }
        if (keep.isEmpty()) throw new IOException("Keine Kartendatei vorhanden");
        String template = template(context, lite, buildingHeightM > 0, night);
        if (!lite && buildingHeightM > 0) template = OfflineStyleComposer.withBuildingHeight(template, buildingHeightM);
        String glyphs = serverBase == null
                ? Uri.fromFile(GermanyOfflineStore.glyphRoot(context)) + "/{fontstack}/{range}.pbf"
                : serverBase + "/f/glyphs/{fontstack}/{range}.pbf";
        String sprite = level >= 1 ? null : ensureSprites(context);
        if (sprite != null && serverBase != null) sprite = serverBase + "/f/sprites/sprite";
        if (level >= 2) {
            template = OfflineStyleComposer.withoutSymbols(template);
            glyphs = null;
            keep = keep.subList(0, 1);
            urls = urls.subList(0, 1);
        }
        String json = OfflineStyleComposer.compose(glyphs, sprite, keep.toArray(new String[0]), urls.toArray(new String[0]), template,
                night ? OfflineStyleComposer.BACKGROUND_DARK : OfflineStyleComposer.BACKGROUND);
        return new Built(json, keep, lite, countLines(template) * keep.size() + 1);
    }

    private static int countLines(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    private static synchronized String template(Context c, boolean lite, boolean threeD, boolean night) throws IOException {
        String name = (lite ? "layers_lite" : threeD ? "layers_full_3d" : "layers_full") + (night ? "_dark" : "") + ".jsonl";
        String cached = templates.get(name);
        if (cached == null) { cached = asset(c, name); templates.put(name, cached); }
        return cached;
    }

    private static String asset(Context c, String name) throws IOException {
        AssetManager am = c.getAssets();
        try (InputStream in = am.open(ASSET_DIR + name)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    static File spriteDir(Context c) {
        File glyphs = GermanyOfflineStore.glyphRoot(c);
        return glyphs == null || glyphs.getParentFile() == null ? null : new File(glyphs.getParentFile(), "sprites");
    }

    /** Kopiert das Sprite einmalig in den privaten Speicher (MapLibre liest es per file://). */
    private static String ensureSprites(Context c) {
        try {
            File glyphs = GermanyOfflineStore.glyphRoot(c);
            if (glyphs == null || glyphs.getParentFile() == null) return null;
            File dir = new File(glyphs.getParentFile(), "sprites");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            for (String name : SPRITE_FILES) {
                File target = new File(dir, name);
                byte[] data;
                try (InputStream in = c.getAssets().open(ASSET_DIR + name)) { data = readAll(in); }
                if (target.isFile() && target.length() == data.length) continue;
                File tmp = new File(dir, name + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
                if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) return null; }
            }
            return Uri.fromFile(new File(dir, "sprite")).toString();
        } catch (IOException | RuntimeException e) {
            DiagLog.add("Offline-Stil: Sprite nicht bereit (" + e.getClass().getSimpleName() + ") – Karte ohne Symbole");
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
