package de.ronny.pololauncher;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** The local tile server answers tiles, files and errors exactly like MapLibre expects. */
public final class LocalTileServerTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }

    static int[] status = new int[1];

    static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(3000);
        status[0] = c.getResponseCode();
        if (status[0] != 200) { c.disconnect(); return null; }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    public static void main(String[] a) throws Exception {
        final byte[] raw = "vector tile payload ".repeat(20).getBytes();
        final java.io.ByteArrayOutputStream gz = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream o = new java.util.zip.GZIPOutputStream(gz)) { o.write(raw); }
        final byte[] tile = gz.toByteArray();
        final byte[] glyph = new byte[3000];
        Arrays.fill(glyph, (byte) 7);
        LocalTileServer server = new LocalTileServer(
                (region, z, x, y) -> "sachsen".equals(region) && z == 14 && x == 8800 && y == 5500 ? tile : null,
                rel -> rel.equals("glyphs/Open Sans Regular,Arial Unicode MS Regular/0-255.pbf") ? glyph
                        : rel.equals("sprites/sprite.json") ? "{}".getBytes() : null);
        try {
            String base = server.base();
            check(base.startsWith("http://127.0.0.1:") && server.port() > 0, "binds to loopback only: " + base);
            check(Arrays.equals(get(base + "/t/sachsen/14/8800/5500.pbf"), raw) && status[0] == 200, "gzip tile is served decompressed");
            check(get(base + "/t/sachsen/14/8800/5501.pbf") == null && status[0] == 404, "missing tile is 404");
            check(get(base + "/t/Sachsen/14/8800/5500.pbf") == null && status[0] == 404, "bad region name rejected");
            check(get(base + "/t/sachsen/14/8800/abc.pbf") == null && status[0] == 404, "bad number rejected");
            check(Arrays.equals(get(base + "/f/glyphs/Open%20Sans%20Regular,Arial%20Unicode%20MS%20Regular/0-255.pbf"), glyph),
                    "glyph with spaces and comma in the font stack");
            check(new String(get(base + "/f/sprites/sprite.json")).equals("{}"), "sprite json");
            check(get(base + "/f/../etc/passwd") == null && status[0] == 404, "path traversal rejected");
            check(get(base + "/other") == null && status[0] == 404, "unknown route");
            check(server.selfCheck("/t/sachsen/14/8800/5500.pbf").equals("HTTP 200, " + raw.length + " B"), "selfCheck: " + server.selfCheck("/t/sachsen/14/8800/5500.pbf"));
            // many parallel requests, as MapLibre does
            Thread[] ts = new Thread[8];
            final boolean[] ok = {true};
            for (int i = 0; i < ts.length; i++) {
                ts[i] = new Thread(() -> {
                    try { for (int k = 0; k < 25; k++)
                        if (!Arrays.equals(get(base + "/t/sachsen/14/8800/5500.pbf"), raw)) ok[0] = false; }
                    catch (Exception e) { ok[0] = false; }
                });
                ts[i].start();
            }
            for (Thread t : ts) t.join();
            check(ok[0], "200 parallel requests answered correctly");
        } finally {
            server.close();
        }
        System.out.println("LocalTileServerTest: tiles, files, errors and parallel requests OK");
    }
}
