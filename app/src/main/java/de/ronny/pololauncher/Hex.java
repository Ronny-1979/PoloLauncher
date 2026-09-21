package de.ronny.pololauncher;

public final class Hex {
    private static final int MAX_BYTES = 512;
    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();
    private Hex() {}
    public static String of(byte[] b) {
        if (b == null) return "null";
        int count = Math.min(b.length, MAX_BYTES);
        StringBuilder s = new StringBuilder(count * 3 + 24);
        for (int i=0;i<count;i++) {
            if (i>0) s.append(' ');
            int value = b[i] & 0xFF;
            s.append(DIGITS[value >>> 4]).append(DIGITS[value & 0x0F]);
        }
        if (b.length > count) s.append(" ... [").append(b.length - count).append(" Bytes mehr]");
        return s.toString();
    }
}
