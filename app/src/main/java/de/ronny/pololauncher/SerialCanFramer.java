package de.ronny.pololauncher;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/** Length/checksum based framing: 0x2E inside a payload is ordinary data. */
final class SerialCanFramer {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Consumer<byte[]> consumer;
    SerialCanFramer(Consumer<byte[]> consumer) { this.consumer = consumer; }
    boolean pending() { return buffer.size() != 0; }
    void feed(byte[] bytes) {
        if (bytes != null) for (byte value : bytes) feedByte(value);
    }
    private void feedByte(byte value) {
        if (buffer.size() == 0 && (value & 0xff) != 0x2e) return;
        buffer.write(value);
        byte[] frame = buffer.toByteArray();
        if (frame.length < 3) return;
        int length = frame[2] & 0xff;
        if (length <= 250 && frame.length < length + 4) return;
        byte[] normalized = length <= 250 ? CanFrameNormalizer.normalize(frame) : null;
        buffer.reset();
        if (normalized != null) {
            consumer.accept(normalized);
            return;
        }
        // Resynchronize only after a structurally invalid/failed-checksum frame.
        for (int i = 1; i < frame.length; i++) {
            if ((frame[i] & 0xff) == 0x2e) {
                feed(Arrays.copyOfRange(frame, i, frame.length));
                break;
            }
        }
    }
}
