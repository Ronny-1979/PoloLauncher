package de.ronny.pololauncher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Structural sanity check for MapLibre glyph-range protobuf files.
 *
 * Important: an empty-but-valid glyph range can be only about 32 bytes. Therefore size alone
 * must never be used to reject a glyph PBF. We verify the protobuf envelope and its declared
 * fontstack range instead.
 */
final class OfflineGlyphValidator {
    private static final long MIN_BYTES = 8L;
    private static final long MAX_BYTES = 8L * 1024L * 1024L;

    private OfflineGlyphValidator() {}

    static boolean isUsable(File file, int expectedStart) {
        if (file == null || !file.isFile()) return false;
        long length = file.length();
        if (length < MIN_BYTES || length > MAX_BYTES || length > Integer.MAX_VALUE) return false;

        byte[] data = new byte[(int) length];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) return false;
                if (read == 0) continue;
                offset += read;
            }
        } catch (IOException ignored) {
            return false;
        }

        String expectedRange = expectedStart + "-" + (expectedStart + 255);
        Cursor top = new Cursor(data, 0, data.length);
        try {
            while (top.pos < top.end) {
                long key = top.varint();
                int field = (int) (key >>> 3);
                int wire = (int) (key & 7L);
                if (field == 1 && wire == 2) {
                    int len = top.length();
                    int end = checkedEnd(top.pos, len, top.end);
                    if (validFontStack(data, top.pos, end, expectedRange)) return true;
                    top.pos = end;
                } else {
                    top.skip(wire);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private static boolean validFontStack(byte[] data, int start, int end, String expectedRange) {
        Cursor stack = new Cursor(data, start, end);
        String name = null;
        String range = null;
        try {
            while (stack.pos < stack.end) {
                long key = stack.varint();
                int field = (int) (key >>> 3);
                int wire = (int) (key & 7L);
                if ((field == 1 || field == 2) && wire == 2) {
                    int len = stack.length();
                    int valueEnd = checkedEnd(stack.pos, len, stack.end);
                    String value = new String(data, stack.pos, len, StandardCharsets.UTF_8);
                    stack.pos = valueEnd;
                    if (field == 1) name = value;
                    else range = value;
                } else {
                    stack.skip(wire);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return name != null && !name.isEmpty() && expectedRange.equals(range);
    }

    private static int checkedEnd(int start, int length, int limit) {
        if (length < 0 || start < 0 || start > limit || length > limit - start)
            throw new IllegalArgumentException("protobuf length out of bounds");
        return start + length;
    }

    private static final class Cursor {
        final byte[] data;
        final int end;
        int pos;

        Cursor(byte[] data, int start, int end) {
            this.data = data;
            this.pos = start;
            this.end = end;
        }

        long varint() {
            long value = 0L;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IllegalArgumentException("truncated varint");
                int b = data[pos++] & 0xff;
                value |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) return value;
            }
            throw new IllegalArgumentException("varint too long");
        }

        int length() {
            long value = varint();
            if (value < 0L || value > Integer.MAX_VALUE) throw new IllegalArgumentException("bad length");
            return (int) value;
        }

        void skip(int wire) {
            switch (wire) {
                case 0:
                    varint();
                    return;
                case 1:
                    advance(8);
                    return;
                case 2:
                    advance(length());
                    return;
                case 5:
                    advance(4);
                    return;
                default:
                    throw new IllegalArgumentException("unsupported wire type " + wire);
            }
        }

        void advance(int count) {
            int next = checkedEnd(pos, count, end);
            pos = next;
        }
    }
}
