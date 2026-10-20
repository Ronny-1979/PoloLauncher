package de.ronny.pololauncher;

import android.os.SystemClock;

/**
 * Cheap live counters for the GPS -> filter -> router -> map camera chain.
 * Shown on the diagnostics page so a drive test tells exactly which stage stops
 * (raw GPS keeps arriving while the camera stops moving).
 */
final class MapFollowDiagnostics {
    private static long noConsumer, restarts;
    private static long rawFixes, cameraFixes, viaMatcher, matcherSnapped, matcherRaw, matcherTileRoad, aimHeld, offlineCreatedMs, offlineStyleMs, offlineRenderedMs, toGermany, toNobody;
    private static long germanyMoved, germanyHidden, germanyNotFollowing, germanyNotReady;
    private static long lastRawMs, lastCameraFixMs, lastMoveMs;
    // Smoothness: spacing of GPS fixes and of camera frames while the map glides.
    private static long prevRawMs, gpsLt07, gps07to13, gps13to20, gpsGt20, gpsMaxGap;
    private static long prevFrameMs, frames, slowFrames, holdGaps, maxHoldGap, glideMs;
    // A new fix arrived while driving and the camera had already stopped moving: "waiting for the fix".
    private static long movingFixes, movingHolds, movingHoldMax, lastHoldEventMs;
    private static String lastMover = "";
    private static double lastMoveLat, lastMoveLon;

    private static final java.util.ArrayDeque<String> EVENTS = new java.util.ArrayDeque<>();
    private static final java.text.SimpleDateFormat TS =
            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT);

    private MapFollowDiagnostics() {}

    /** Short ring buffer of map-relevant events; the main DiagLog is flooded by CAN lines. */
    static synchronized void event(String text) {
        EVENTS.addFirst(TS.format(new java.util.Date()) + " " + text);
        while (EVENTS.size() > 40) EVENTS.removeLast();
    }

    static synchronized void rawFix() {
        rawFixes++;
        long now = SystemClock.elapsedRealtime();
        if (prevRawMs != 0L) {
            long gap = now - prevRawMs;
            if (gap < 700L) gpsLt07++; else if (gap < 1_300L) gps07to13++;
            else if (gap < 2_000L) gps13to20++; else gpsGt20++;
            if (gap > gpsMaxGap) gpsMaxGap = gap;
        }
        prevRawMs = now;
        lastRawMs = now;
    }

    /**
     * Called when a fix reaches the follow camera. If the car is moving and the camera has
     * already been idle for a while, the previous glide ended before this fix arrived.
     */
    static synchronized void fixArrived(float speedMps, long fixIntervalMs) {
        if (!(speedMps >= 3f)) return;
        movingFixes++;
        long now = SystemClock.elapsedRealtime();
        if (prevFrameMs == 0L) return;
        long idle = now - prevFrameMs;
        if (idle <= 600L) return;
        movingHolds++;
        if (idle > movingHoldMax) movingHoldMax = idle;
        if (now - lastHoldEventMs >= 5_000L) {
            lastHoldEventMs = now;
            event(String.format(java.util.Locale.ROOT,
                    "Kamera stand %d ms still bei %.0f km/h (Fix-Abstand %d ms)",
                    idle, speedMps * 3.6f, fixIntervalMs));
        }
    }

    /** One camera-move callback = one map frame while the camera glides. */
    static synchronized void cameraFrame() {
        long now = SystemClock.elapsedRealtime();
        if (prevFrameMs != 0L) {
            long dt = now - prevFrameMs;
            if (dt <= 600L) {
                frames++;
                glideMs += dt;
                if (dt > 120L) slowFrames++;
            } else if (dt <= 5_000L) {
                holdGaps++;                       // camera stood still between two glides
                if (dt > maxHoldGap) maxHoldGap = dt;
            }
        }
        prevFrameMs = now;
    }
    static synchronized void cameraFix() { cameraFixes++; lastCameraFixMs = SystemClock.elapsedRealtime(); }
    static synchronized void noConsumer() { noConsumer++; }
    static synchronized void feedRestart() { restarts++; }
    static synchronized void matcherQueued() { viaMatcher++; }
    static synchronized void offlineCreated() { offlineCreatedMs = SystemClock.elapsedRealtime(); offlineStyleMs = 0L; offlineRenderedMs = 0L; }
    static synchronized void offlineStyle() { offlineStyleMs = SystemClock.elapsedRealtime(); }
    static synchronized void offlineRendered() { offlineRenderedMs = SystemClock.elapsedRealtime(); }
    static synchronized void aimHeld() { aimHeld++; }
    static synchronized void matcherTileRoad() { matcherTileRoad++; }
    static synchronized void matcherResult(boolean snapped) { if (snapped) matcherSnapped++; else matcherRaw++; }
    static synchronized void routedGermany() { toGermany++; }
    static synchronized void routedNobody() { toNobody++; }
    static synchronized void germanyDropped(boolean visible, boolean following) {
        if (!visible) germanyHidden++; else if (!following) germanyNotFollowing++; else germanyNotReady++;
    }
    static synchronized void moved(String who, double lat, double lon) {
        germanyMoved++;
        lastMover = who;
        lastMoveMs = SystemClock.elapsedRealtime();
        lastMoveLat = lat;
        lastMoveLon = lon;
    }

    static synchronized String summary() {
        long now = SystemClock.elapsedRealtime();
        StringBuilder b = new StringBuilder("Kartennachführung (Zähler seit App-Start):\n");
        b.append("  GPS roh: ").append(rawFixes).append(" · letzter vor ").append(age(now, lastRawMs)).append("\n");
        b.append("  nach Filter an Router: ").append(cameraFixes).append(" · letzter vor ")
                .append(age(now, lastCameraFixMs)).append("\n");
        b.append("  Ohne Empfänger verworfen: ").append(noConsumer).append(" · GPS-Neustarts: ").append(restarts).append("\n");
        b.append("  Weiterleitung: Deutschland ").append(toGermany)
                .append(" · Straßenabgleich ").append(viaMatcher).append(" · niemand ").append(toNobody).append("\n");
        b.append("  Straßenkorrektur: eingerastet ").append(matcherSnapped)
                .append(" · Roh-GPS ").append(matcherRaw)
                .append(" · davon aus Kartenkacheln ").append(matcherTileRoad).append("\n");
        b.append("  GPS-Abstand: unter 0,7 s ").append(gpsLt07).append(" · 0,7–1,3 s ").append(gps07to13)
                .append(" · 1,3–2 s ").append(gps13to20).append(" · über 2 s ").append(gpsGt20)
                .append(" · größter ").append(gpsMaxGap).append(" ms\n");
        b.append("  Karten-Bilder beim Gleiten: ").append(frames).append(" · Ø ")
                .append(glideMs > 0L ? String.format(java.util.Locale.ROOT, "%.1f", frames * 1000.0 / glideMs) : "--")
                .append(" Bilder/s · langsam (>120 ms) ").append(slowFrames)
                .append(" · Stillstand zwischen Gleitbewegungen (>0,6 s) ").append(holdGaps)
                .append(" · längster ").append(maxHoldGap).append(" ms\n");
        b.append("  In Fahrt (>=11 km/h): Fixes ").append(movingFixes)
                .append(" · Kamera stand beim Eintreffen schon still (>0,6 s) ").append(movingHolds)
                .append(" · längster ").append(movingHoldMax).append(" ms\n");
        b.append("  Offline-Karte: ");
        if (offlineCreatedMs == 0L) b.append("nicht erstellt");
        else {
            b.append("Stil ").append(offlineStyleMs == 0L ? "noch nicht angewendet (seit " + (now - offlineCreatedMs) / 1000L + " s)"
                    : "nach " + (offlineStyleMs - offlineCreatedMs) + " ms");
            b.append(" · erster vollständiger Frame ").append(offlineRenderedMs == 0L ? "noch nicht"
                    : "nach " + (offlineRenderedMs - offlineCreatedMs) + " ms");
        }
        b.append("\n");
        b.append("  Kamera: Rückwärtssprünge verhindert ").append(aimHeld).append("\n");
        b.append("  Deutschland-Karte: bewegt ").append(germanyMoved).append(" · verworfen (versteckt ")
                .append(germanyHidden).append(", folgt nicht ").append(germanyNotFollowing)
                .append(", nicht bereit ").append(germanyNotReady).append(")\n");
        b.append("  Letzte Kamerabewegung: ");
        if (lastMoveMs == 0L) b.append("noch nie");
        else b.append(lastMover).append(" vor ").append(age(now, lastMoveMs)).append(String.format(
                java.util.Locale.ROOT, " bei %.5f, %.5f", lastMoveLat, lastMoveLon));
        b.append("  Kartenereignisse (neueste zuerst):\n");
        for (String e : EVENTS) b.append("   ").append(e).append("\n");
        return b.append("\n").toString();
    }

    private static String age(long now, long then) {
        if (then == 0L) return "nie";
        long seconds = Math.max(0L, (now - then) / 1000L);
        return seconds + " s";
    }
}
