package de.ronny.pololauncher;

import android.content.Context;
import java.nio.file.*;

/** The free-space check uses the size the server reports now, not the possibly stale table. */
public final class OfflineMapSizeProbeTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("polo-map-size-");
        try {
            Context app = new Context();
            app.externalFilesDir = root.toFile();
            app.services.put(Context.DOWNLOAD_SERVICE, new android.app.DownloadManager());
            String[] asked = {null};
            long usable = root.toFile().getUsableSpace();
            // Server says the map is far larger than the free space: refuse before downloading.
            OfflineMapStore.sizeProbe = url -> { asked[0] = url; return usable * 2; };
            boolean refused = false;
            try { OfflineMapStore.start(app, "sachsen"); }
            catch (IllegalStateException e) { refused = e.getMessage().contains("Zu wenig freier Speicher"); }
            check(refused, "Live size larger than free space is refused");
            check(asked[0] != null && asked[0].endsWith("sachsen.map"), "The map URL is probed");
            // Probe unavailable: fall back to the (small) table entry and start.
            OfflineMapStore.sizeProbe = url -> -1L;
            check(OfflineMapStore.start(app, "sachsen") == 1L, "Fallback to the table size when the probe fails");
            OfflineMapStore.cancelActive(app);
            System.out.println("OfflineMapSizeProbeTest: all cases passed");
        } finally {
            try (var files = Files.walk(root)) { for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
}
