package de.ronny.pololauncher;

import java.util.Base64;

/** Decodes tiles written by an independent MVT encoder (python mapbox-vector-tile). */
public final class MvtRoadDecoderTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }

    private static final String GZIP_TILE = "H4sIANKjzGoC/y3OPQrCMBjG8bepmuatooQOUpBKJicXd/EmEjBgsF+kwerWI7i5OnoKcXR0ELyCo0cwFNcfPA//+OwhraxRylY84gRgTESfPbyTz59DgGvER049pwGD7xybFfChE+KEsijCFwUeO/AdcHZJLgl+KLAPfU8Q7v24s9P5RgwwNKrSG5VbLVPBkJZGZ9IcRQ87pbRbgRhkhS1MLY+zZnog8QLDWlpl1qnOVdVmQJtBCN6Wt+X/mWLX6L0y7egHp3HJ6s8AAAA=";
    private static final String RAW_TILE = "GpcBCgdzdHJlZXRzEhQSAgAAGAIiDAnIAZADEtAPAACoFBIQEgIAARgCIggJAPAuCoBAABIPEgIAAhgCIgcJFBQK1AcAEhoSAgADGAIiEgmgH6AfCugHAAnoB9cdCgDADBoEa2luZCINCgtyZXNpZGVudGlhbCIJCgdwcmltYXJ5IgYKBHBhdGgiCgoIbW90b3J3YXkogCB4AhozCgt3YXRlcl9saW5lcxIQEgIAABgCIggJAgIKvj6+PhoEa2luZCIHCgVyaXZlciiAIHgC";
    private static final int Z = 14, X = 8800, Y = 5500;
    private static final double[][] EXPECTED = {
            {50.79136885020993, 13.359911441802979, 50.79136885020993, 13.365275859832764},
            {50.79136885020993, 13.365275859832764, 50.78696021806557, 13.365275859832764},
            {50.78187281817864, 13.359375, 50.78187281817864, 13.38134765625},
            {50.78526447960933, 13.37010383605957, 50.78526447960933, 13.372786045074463},
            {50.791707958538446, 13.375468254089355, 50.78899502302643, 13.375468254089355}
    };

    private static void verify(double[] got, String label) {
        check(got.length == EXPECTED.length * 4, label + ": 5 road segments (path and water ignored), got " + got.length / 4);
        for (int i = 0; i < EXPECTED.length; i++)
            for (int k = 0; k < 4; k++)
                check(Math.abs(got[i * 4 + k] - EXPECTED[i][k]) < 1e-9, label + ": coordinate " + i + "/" + k);
    }

    public static void main(String[] a) {
        verify(MvtRoadDecoder.decode(Base64.getDecoder().decode(GZIP_TILE), Z, X, Y), "gzip");
        verify(MvtRoadDecoder.decode(Base64.getDecoder().decode(RAW_TILE), Z, X, Y), "raw");
        check(MvtRoadDecoder.decode(null, Z, X, Y).length == 0, "null tile");
        check(MvtRoadDecoder.decode(new byte[] {1, 2, 3, 4}, Z, X, Y).length == 0, "garbage tile does not throw");
        byte[] truncated = Base64.getDecoder().decode(RAW_TILE);
        check(MvtRoadDecoder.decode(java.util.Arrays.copyOf(truncated, truncated.length / 2), Z, X, Y).length % 4 == 0,
                "truncated tile stays well-formed");

        // Tile index maths: a point inside the fixture tile maps back to it.
        double lat = EXPECTED[0][0], lon = EXPECTED[0][1];
        check(MvtRoadDecoder.tileX(lon, Z) == X && MvtRoadDecoder.tileY(lat, Z) == Y, "tile index of a coordinate");
        check(MvtRoadDecoder.tileX(-181d, Z) == 0 && MvtRoadDecoder.tileX(181d, Z) == (1 << Z) - 1, "x clamped");
        double[] bounds = GermanyRoadTiles.parseBounds("5.86,47.27,15.04,55.06");
        check(bounds != null && bounds[0] == 5.86 && bounds[3] == 55.06, "bounds parsed");
        check(GermanyRoadTiles.parseBounds(null) == null && GermanyRoadTiles.parseBounds("1,2,3") == null
                && GermanyRoadTiles.parseBounds("a,b,c,d") == null && GermanyRoadTiles.parseBounds("9,9,1,1") == null,
                "bad bounds rejected");
        System.out.println("MvtRoadDecoderTest: MVT road lines decoded identically to an independent encoder");
    }
}
