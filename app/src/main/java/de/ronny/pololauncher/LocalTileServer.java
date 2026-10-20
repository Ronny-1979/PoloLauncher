package de.ronny.pololauncher;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Winziger HTTP-Server nur auf 127.0.0.1. Er liefert MapLibre die Kacheln aus den MBTiles-Dateien
 * sowie Schrift und Sprite, falls MapLibre die Dateien nicht direkt ueber mbtiles:// oder file://
 * oeffnen kann. Routen: /t/{paket}/{z}/{x}/{y}.pbf und /f/{relativer Pfad}.
 */
final class LocalTileServer {
    interface Tiles { byte[] tile(String region, int z, int x, int y); }
    interface Files { byte[] read(String relativePath); }

    private final Tiles tiles;
    private final Files files;
    private final ServerSocket socket;
    private final ExecutorService pool;
    private volatile boolean closed;

    LocalTileServer(Tiles tiles, Files files) throws IOException {
        this.tiles = tiles;
        this.files = files;
        this.socket = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        this.pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "tile-server-worker");
            t.setDaemon(true);
            return t;
        });
        Thread acceptor = new Thread(this::acceptLoop, "tile-server-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() { return socket.getLocalPort(); }
    String base() { return "http://127.0.0.1:" + port(); }

    void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                final Socket s = socket.accept();
                try { pool.execute(() -> handle(s)); }
                catch (RuntimeException rejected) { try { s.close(); } catch (IOException ignored) {} }
            } catch (IOException e) {
                if (closed) return;
            }
        }
    }

    private void handle(Socket s) {
        try (Socket socket = s) {
            socket.setSoTimeout(5_000);
            String line = readRequestLine(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) { respond(out, 405, "text/plain", new byte[0]); return; }
            String target = parts[1];
            int q = target.indexOf('?');
            if (q >= 0) target = target.substring(0, q);
            String path = URLDecoder.decode(target.replace("+", "%2B"), "UTF-8");
            byte[] body = null;
            String type = "application/octet-stream";
            if (path.startsWith("/t/")) {
                body = tile(path.substring(3));
                type = "application/x-protobuf";
            } else if (path.startsWith("/f/")) {
                String rel = path.substring(3);
                if (!rel.contains("..") && !rel.startsWith("/")) body = files.read(rel);
                type = rel.endsWith(".json") ? "application/json" : rel.endsWith(".png") ? "image/png"
                        : "application/x-protobuf";
            }
            if (body == null) respond(out, 404, "text/plain", new byte[0]);
            else respond(out, 200, type, body);
        } catch (IOException | RuntimeException ignored) {
            // a broken connection of one tile request must never matter
        }
    }

    private byte[] tile(String rest) {
        // {region}/{z}/{x}/{y}.pbf
        String[] p = rest.split("/");
        if (p.length != 4 || !p[3].endsWith(".pbf") || !p[0].matches("[a-z-]+")) return null;
        try {
            int z = Integer.parseInt(p[1]), x = Integer.parseInt(p[2]);
            int y = Integer.parseInt(p[3].substring(0, p[3].length() - 4));
            return gunzip(tiles.tile(p[0], z, x, y));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Shortbread tiles are gzip-compressed. They are served uncompressed and without a Content-Encoding
     * header, so it does not matter whether MapLibre's HTTP stack would have decompressed them itself.
     */
    static byte[] gunzip(byte[] data) {
        if (data == null || data.length < 3 || (data[0] & 0xff) != 0x1f || (data[1] & 0xff) != 0x8b) return data;
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readRequestLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        String first = null;
        int c, total = 0;
        while ((c = in.read()) >= 0 && total++ < 8192) {
            if (c == '\n') {
                String text = line.toString("ISO-8859-1").trim();
                line.reset();
                if (first == null) first = text;
                if (text.isEmpty()) break;         // end of headers
            } else line.write(c);
        }
        return first;
    }

    private static void respond(OutputStream out, int status, String type, byte[] body) throws IOException {
        String text = status == 200 ? "OK" : status == 404 ? "Not Found" : "Method Not Allowed";
        String head = "HTTP/1.1 " + status + " " + text + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    /** Ruft den eigenen Server ueber das normale HTTP-Netz des Systems auf (prueft auch die Klartext-Erlaubnis). */
    String selfCheck(String path) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base() + path).openConnection();
            c.setConnectTimeout(3_000);
            c.setReadTimeout(3_000);
            int code = c.getResponseCode();
            int n = 0;
            if (code == 200) try (InputStream in = c.getInputStream()) { while (in.read() >= 0) n++; }
            c.disconnect();
            return "HTTP " + code + (code == 200 ? ", " + n + " B" : "");
        } catch (IOException | RuntimeException e) {
            return "Fehler " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
