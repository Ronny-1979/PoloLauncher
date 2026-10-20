package de.ronny.pololauncher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class OfflineGlyphValidatorTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("glyph-test").toFile();
        try {
            // This reproduces the real v0.31.55 bug: an empty but valid fontstack is ~32 bytes.
            File validEmpty = file(dir, "1536-1791.pbf", glyphPbf("Open Sans Regular", "1536-1791"));
            if (validEmpty.length() >= 100L) throw new AssertionError("fixture should stay below 100 B");
            check(validEmpty, 1536, true, "valid empty <100B range");

            check(file(dir, "wrong-range.pbf", glyphPbf("Open Sans Regular", "1792-2047")),
                    1536, false, "wrong range");
            check(file(dir, "html.pbf", "<!doctype html><html>Not Found</html>".getBytes(StandardCharsets.UTF_8)),
                    1536, false, "html error body");
            check(file(dir, "json.pbf", "{\"error\":\"rate limit\"}".getBytes(StandardCharsets.UTF_8)),
                    1536, false, "json error body");
            check(file(dir, "junk.pbf", new byte[]{8, 1, 8, 2, 8, 3, 8, 4, 8, 5}),
                    1536, false, "protobuf-looking junk without fontstack");
            System.out.println("OfflineGlyphValidatorTest: valid ~32B empty range accepted; wrong/text/junk rejected");
        } finally { delete(dir); }
    }

    private static byte[] glyphPbf(String name, String range) throws Exception {
        ByteArrayOutputStream stack = new ByteArrayOutputStream();
        fieldString(stack, 1, name);
        fieldString(stack, 2, range);
        byte[] stackBytes = stack.toByteArray();

        ByteArrayOutputStream top = new ByteArrayOutputStream();
        top.write(0x0A); // Glyphs.stacks, field 1, length-delimited
        writeVarint(top, stackBytes.length);
        top.write(stackBytes);
        return top.toByteArray();
    }

    private static void fieldString(ByteArrayOutputStream out, int field, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write((field << 3) | 2);
        writeVarint(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7f) != 0) {
            out.write((v & 0x7f) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static File file(File dir, String name, byte[] data) throws Exception {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
        return f;
    }

    private static void check(File file, int start, boolean expected, String label) {
        boolean actual = OfflineGlyphValidator.isUsable(file, start);
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void delete(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) delete(x); }
        f.delete();
    }
}
