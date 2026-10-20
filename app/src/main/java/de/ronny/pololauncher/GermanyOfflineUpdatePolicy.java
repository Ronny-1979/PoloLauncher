package de.ronny.pololauncher;

/** Pure comparison rules for explicit Germany offline update checks. */
final class GermanyOfflineUpdatePolicy {
    private static final long TIME_TOLERANCE_MS = 2_000L;

    private GermanyOfflineUpdatePolicy() {}

    /**
     * Returns true only when the remote object can be identified as different/newer.
     * A missing remote identity never forces a replacement of a working local file.
     */
    static boolean updateAvailable(long localSize, long localModified,
                                   long remoteSize, long remoteModified) {
        if (localSize <= 0L) return remoteSize > 0L || remoteModified > 0L;

        // A clearly newer server timestamp is the strongest signal.
        if (remoteModified > 0L && localModified > 0L
                && remoteModified > localModified + TIME_TOLERANCE_MS) return true;

        // Geofabrik may repack an object while keeping the same/nearby timestamp.
        // A changed byte size therefore also counts, unless the remote is clearly older.
        if (remoteSize > 0L && remoteSize != localSize) {
            return remoteModified <= 0L || localModified <= 0L
                    || remoteModified + TIME_TOLERANCE_MS >= localModified;
        }

        // If only a timestamp is known, still detect a clearly newer object.
        return remoteSize <= 0L && remoteModified > 0L && localModified > 0L
                && remoteModified > localModified + TIME_TOLERANCE_MS;
    }
}
