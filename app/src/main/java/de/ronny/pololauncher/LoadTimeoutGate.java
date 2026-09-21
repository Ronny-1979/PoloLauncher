package de.ronny.pololauncher;

/** Repeated foreground updates must not postpone an already armed timeout. */
final class LoadTimeoutGate {
    private boolean armed;
    boolean arm() {
        if (armed) return false;
        armed = true;
        return true;
    }
    void clear() { armed = false; }
}
