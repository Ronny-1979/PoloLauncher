package de.ronny.pololauncher;

import java.io.IOException;
import java.net.SocketException;

public class MapResumePolicyTest {
    static int fails;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } }
    public static void main(String[] a) throws Exception {
        check(MapResumePolicy.isTransient(new SocketException("Software caused connection abort")), "SocketException transient");
        check(MapResumePolicy.isTransient(new IOException("x")), "IOException transient");
        check(MapResumePolicy.isTransient(new RuntimeException("w", new IOException("c"))), "cause IOException transient");
        check(MapResumePolicy.isTransient(new IllegalStateException("Download unvollständig (1/2)")), "incomplete transient");
        check(MapResumePolicy.isTransient(new IllegalStateException("HTTP 503")), "503 transient");
        check(!MapResumePolicy.isTransient(new IllegalStateException("HTTP 404")), "404 fatal");
        check(!MapResumePolicy.isTransient(new IllegalStateException("Zu wenig Speicher: frei 1")), "space fatal");
        check(!MapResumePolicy.isTransient(new IllegalStateException("MBTiles ungültig: Süd")), "corrupt fatal");
        check(MapResumePolicy.backoffMs(1) == 15000L && MapResumePolicy.backoffMs(99) == 600000L && MapResumePolicy.backoffMs(0) == 15000L, "backoff");
        check(MapResumePolicy.resumeOffset(500L, 1000L, 5L) == 500L, "resume");
        check(MapResumePolicy.resumeOffset(0L, 1000L, 5L) == 0L, "no part");
        check(MapResumePolicy.resumeOffset(1000L, 1000L, 5L) == 0L, "full part restarts");
        check(MapResumePolicy.resumeOffset(500L, 1000L, 0L) == 0L, "no validator");
        check(MapResumePolicy.resumeOffset(500L, 0L, 5L) == 0L, "no size");
        System.out.println(fails == 0 ? "MapResumePolicyTest OK" : "MapResumePolicyTest FAILED");
        if (fails != 0) System.exit(1);
    }
}
