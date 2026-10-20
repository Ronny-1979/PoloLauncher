package de.ronny.pololauncher;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Size-capped UTF-8 body reader shared by the small JSON clients (weather, place name). */
final class HttpText {
    static final int MAX_RESPONSE_BYTES = 96 * 1024;

    private HttpText() {}

    static String readLimited(HttpURLConnection connection, String tooLargeMessage) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream(8_192)) {
            byte[] buffer = new byte[4_096];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException(tooLargeMessage);
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
