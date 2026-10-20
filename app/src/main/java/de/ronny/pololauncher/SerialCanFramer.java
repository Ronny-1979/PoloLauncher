package de.ronny.pololauncher;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/** Length/checksum based framing: 0x2E inside a payload is ordinary data. */
final class SerialCanFramer {
    private static final int MAX_RESYNC_MARKERS = 32;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Consumer<byte[]> consumer;

    SerialCanFramer(Consumer<byte[]> consumer) { this.consumer = consumer; }

    boolean pending() { return buffer.size() != 0; }

    void feed(byte[] bytes) {
        if (bytes == null) return;
        for (byte value : bytes) {
            if (buffer.size() == 0 && (value & 0xff) != 0x2e) continue;
            buffer.write(value);
            processBufferedFrames();
        }
    }

    private void processBufferedFrames() {
        while (true) {
            byte[] frame = buffer.toByteArray();
            if (frame.length < 3) return;

            int length = frame[2] & 0xff;
            if (length <= 250 && frame.length < length + 4) return;

            byte[] normalized = length <= 250 ? CanFrameNormalizer.normalize(frame) : null;
            if (normalized != null) {
                buffer.reset();
                consumer.accept(normalized);
                return;
            }

            // Structurally invalid/checksum-bad frame. Keep the next possible start marker,
            // but resynchronize iteratively so hostile/corrupt data cannot build recursion depth.
            int nextStart = -1;
            int markerCount = 0;
            for (int i = 1; i < frame.length; i++) {
                if ((frame[i] & 0xff) == 0x2e) {
                    markerCount++;
                    if (nextStart < 0) nextStart = i;
                }
            }
            buffer.reset();
            // A marker storm is not a plausible single damaged CAN frame. Dropping it avoids
            // quadratic resync work and guarantees the next clean frame starts from empty state.
            if (markerCount > MAX_RESYNC_MARKERS || nextStart < 0) return;
            buffer.write(frame, nextStart, frame.length - nextStart);
        }
    }
}
