package de.ronny.pololauncher;

/** Test helper: empties the diagnostic ring buffer between test cases. */
final class DiagLogReset {
    private DiagLogReset() {}

    static void clear() {
        try {
            java.lang.reflect.Field f = DiagLog.class.getDeclaredField("LINES");
            f.setAccessible(true);
            synchronized (DiagLog.class) { ((java.util.Collection<?>) f.get(null)).clear(); }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
