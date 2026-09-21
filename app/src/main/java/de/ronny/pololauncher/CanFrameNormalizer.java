package de.ronny.pololauncher;

import java.util.Arrays;

/** The validated VW/HCT decoder consumes [function, length, payload]. */
final class CanFrameNormalizer {
    private CanFrameNormalizer() {}
    static byte[] normalize(byte[] raw) {
        if (raw == null || raw.length < 2) return null;
        if ((raw[0] & 0xff) != 0x2e) {
            int length = raw[1] & 0xff;
            return length <= 250 && raw.length >= length + 2
                    ? Arrays.copyOf(raw, length + 2) : null;
        }
        if (raw.length < 4) return null;
        int length = raw[2] & 0xff;
        if (length > 250 || raw.length < length + 4) return null;
        int sum = 0;
        for (int i = 1; i < length + 3; i++) sum += raw[i] & 0xff;
        if (((sum & 0xff) ^ 0xff) != (raw[length + 3] & 0xff)) return null;
        return Arrays.copyOfRange(raw, 1, length + 3);
    }
}
