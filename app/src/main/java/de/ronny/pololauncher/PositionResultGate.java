package de.ronny.pololauncher;

/** Main-thread gate: each GPS fix is displayed at most once; late matches lose. */
final class PositionResultGate {
    private long latest;
    private boolean waiting;

    long request() { waiting = true; return ++latest; }
    boolean complete(long request) {
        if (!waiting || request != latest) return false;
        waiting = false;
        return true;
    }
    boolean fallback() { return complete(latest); }
    void cancel() { ++latest; waiting = false; }
}
