package de.ronny.pololauncher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Minimal Mapbox Vector Tile reader for one purpose: the driveable road lines of the Shortbread
 * "streets" layer, returned as flat segments {lat1, lon1, lat2, lon2, ...} in WGS84.
 *
 * Pure Java without Android or MapLibre, so it is unit tested on the JVM against tiles that were
 * written by an independent encoder.
 */
final class MvtRoadDecoder {
    static final String LAYER = "streets";
    private static final int DEFAULT_EXTENT = 4096;

    private MvtRoadDecoder() {}

    /** Decodes a (gzip or raw) tile. @return segments, length is a multiple of 4; never null. */
    static double[] decode(byte[] data, int zoom, int tileX, int tileY) {
        if (data == null || data.length == 0) return new double[0];
        try {
            byte[] raw = isGzip(data) ? gunzip(data) : data;
            return new Decoder(raw, zoom, tileX, tileY).run();
        } catch (IOException | RuntimeException e) {
            return new double[0];
        }
    }

    private static boolean isGzip(byte[] d) {
        return d.length > 2 && (d[0] & 0xff) == 0x1f && (d[1] & 0xff) == 0x8b;
    }

    private static byte[] gunzip(byte[] d) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(d));
             ByteArrayOutputStream out = new ByteArrayOutputStream(d.length * 4)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** Tile index of a coordinate (XYZ scheme, y grows southwards). */
    static int tileX(double lon, int zoom) {
        double n = 1 << zoom;
        return clamp((int) Math.floor((lon + 180d) / 360d * n), zoom);
    }

    static int tileY(double lat, int zoom) {
        double n = 1 << zoom;
        double rad = Math.toRadians(Math.max(-85.05112878d, Math.min(85.05112878d, lat)));
        double merc = Math.log(Math.tan(rad) + 1d / Math.cos(rad));
        return clamp((int) Math.floor((1d - merc / Math.PI) / 2d * n), zoom);
    }

    private static int clamp(int v, int zoom) { return Math.max(0, Math.min((1 << zoom) - 1, v)); }

    private static final class Decoder {
        private final byte[] b;
        private final int zoom, tileX, tileY;
        private double[] out = new double[256];
        private int outLen;

        Decoder(byte[] b, int zoom, int tileX, int tileY) {
            this.b = b; this.zoom = zoom; this.tileX = tileX; this.tileY = tileY;
        }

        double[] run() {
            int[] pos = {0};
            while (pos[0] < b.length) {
                long tag = varint(pos);
                int field = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (field == 3 && wire == 2) {
                    int len = (int) varint(pos);
                    int end = pos[0] + len;
                    layer(pos[0], end);
                    pos[0] = end;
                } else skip(pos, wire);
            }
            double[] result = new double[outLen];
            System.arraycopy(out, 0, result, 0, outLen);
            return result;
        }

        private void layer(int start, int end) {
            int[] pos = {start};
            String name = null;
            int extent = DEFAULT_EXTENT;
            ArrayList<String> keys = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            ArrayList<int[]> features = new ArrayList<>();
            while (pos[0] < end) {
                long tag = varint(pos);
                int field = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (wire == 2) {
                    int len = (int) varint(pos);
                    int s = pos[0], e = s + len;
                    if (field == 1) name = new String(b, s, len, java.nio.charset.StandardCharsets.UTF_8);
                    else if (field == 2) features.add(new int[] {s, e});
                    else if (field == 3) keys.add(new String(b, s, len, java.nio.charset.StandardCharsets.UTF_8));
                    else if (field == 4) values.add(stringValue(s, e));
                    pos[0] = e;
                } else if (wire == 0) {
                    long v = varint(pos);
                    if (field == 5 && v > 0) extent = (int) v;
                } else skip(pos, wire);
            }
            if (!LAYER.equals(name)) return;
            int kindKey = keys.indexOf("kind");
            if (kindKey < 0) return;
            for (int[] f : features) feature(f[0], f[1], kindKey, values, extent);
        }

        private String stringValue(int start, int end) {
            int[] pos = {start};
            while (pos[0] < end) {
                long tag = varint(pos);
                int field = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (wire == 2) {
                    int len = (int) varint(pos);
                    String s = field == 1
                            ? new String(b, pos[0], len, java.nio.charset.StandardCharsets.UTF_8) : null;
                    pos[0] += len;
                    if (s != null) return s;
                } else skip(pos, wire);
            }
            return "";
        }

        private void feature(int start, int end, int kindKey, ArrayList<String> values, int extent) {
            int[] pos = {start};
            int type = 0;
            int tagStart = -1, tagEnd = -1, geoStart = -1, geoEnd = -1;
            while (pos[0] < end) {
                long tag = varint(pos);
                int field = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (wire == 2) {
                    int len = (int) varint(pos);
                    if (field == 2) { tagStart = pos[0]; tagEnd = pos[0] + len; }
                    else if (field == 4) { geoStart = pos[0]; geoEnd = pos[0] + len; }
                    pos[0] += len;
                } else if (wire == 0) {
                    long v = varint(pos);
                    if (field == 3) type = (int) v;
                } else skip(pos, wire);
            }
            if (type != 2 || tagStart < 0 || geoStart < 0) return;

            String kind = null;
            int[] t = {tagStart};
            while (t[0] < tagEnd) {
                int k = (int) varint(t);
                if (t[0] >= tagEnd) break;
                int v = (int) varint(t);
                if (k == kindKey && v >= 0 && v < values.size()) { kind = values.get(v); break; }
            }
            if (!RoadKinds.ok(kind)) return;
            lines(geoStart, geoEnd, extent);
        }

        private void lines(int start, int end, int extent) {
            int[] pos = {start};
            long x = 0, y = 0;
            boolean havePrev = false;
            double prevLat = 0, prevLon = 0;
            while (pos[0] < end) {
                int command = (int) varint(pos);
                int id = command & 7, count = command >>> 3;
                if (id == 7) { havePrev = false; continue; }
                for (int i = 0; i < count && pos[0] < end; i++) {
                    x += unzigzag(varint(pos));
                    y += unzigzag(varint(pos));
                    double lon = lon(x, extent), lat = lat(y, extent);
                    if (id == 1) havePrev = false;
                    if (id == 2 && havePrev) add(prevLat, prevLon, lat, lon);
                    prevLat = lat; prevLon = lon;
                    havePrev = true;
                }
            }
        }

        private double lon(long px, int extent) {
            double n = 1 << zoom;
            return (tileX + (double) px / extent) / n * 360d - 180d;
        }

        private double lat(long py, int extent) {
            double n = 1 << zoom;
            double merc = Math.PI * (1d - 2d * (tileY + (double) py / extent) / n);
            return Math.toDegrees(Math.atan(Math.sinh(merc)));
        }

        private void add(double lat1, double lon1, double lat2, double lon2) {
            if (lat1 == lat2 && lon1 == lon2) return;
            if (outLen + 4 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
            out[outLen++] = lat1; out[outLen++] = lon1; out[outLen++] = lat2; out[outLen++] = lon2;
        }

        private static long unzigzag(long v) { return (v >>> 1) ^ -(v & 1); }

        private long varint(int[] pos) {
            long result = 0;
            int shift = 0;
            while (true) {
                if (pos[0] >= b.length || shift > 63) throw new IllegalStateException("truncated");
                int c = b[pos[0]++] & 0xff;
                result |= (long) (c & 0x7f) << shift;
                if ((c & 0x80) == 0) return result;
                shift += 7;
            }
        }

        private void skip(int[] pos, int wire) {
            switch (wire) {
                case 0: varint(pos); break;
                case 1: pos[0] += 8; break;
                case 2: pos[0] += (int) varint(pos); break;
                case 5: pos[0] += 4; break;
                default: throw new IllegalStateException("bad wire type " + wire);
            }
        }
    }
}
