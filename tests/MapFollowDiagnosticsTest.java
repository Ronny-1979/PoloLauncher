package de.ronny.pololauncher;

public final class MapFollowDiagnosticsTest {
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    public static void main(String[] a) {
        String empty = MapFollowDiagnostics.summary();
        check(empty.contains("Letzte Kamerabewegung: noch nie"), "fresh state reports no camera move");
        MapFollowDiagnostics.rawFix(); MapFollowDiagnostics.cameraFix();
        MapFollowDiagnostics.routedGermany(); MapFollowDiagnostics.germanyDropped(false, true);
        MapFollowDiagnostics.moved("germany", 51.05, 13.74);
        String s = MapFollowDiagnostics.summary();
        check(s.contains("GPS roh: 1") && s.contains("Deutschland 1")
                && s.contains("versteckt 1") && s.contains("germany vor") && s.contains("51.05000"), "counters visible: " + s);
        System.out.println("MapFollowDiagnosticsTest: follow-chain counters and last-move report passed");
    }
}
