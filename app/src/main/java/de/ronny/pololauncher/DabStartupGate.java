package de.ronny.pololauncher;

/** One automatic attempt per launcher process, not on each Home/resume event. */
final class DabStartupGate {
    private boolean attempted;
    synchronized void screenOff() { attempted = false; }
    synchronized boolean pending(boolean enabled) { return enabled && !attempted; }
    synchronized boolean claim(boolean enabled, boolean internetReady) {
        if (!enabled || !internetReady || attempted) return false;
        attempted = true;
        return true;
    }
}
