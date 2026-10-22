package de.ronny.pololauncher;

import java.io.IOException;

/** Pure rules for automatic continuation of the Germany map build/update. */
final class MapResumePolicy {
    private static final long[] BACKOFF_S = {15, 30, 60, 120, 300, 600};

    private MapResumePolicy() {}

    /** Network-type problems are retried forever; real errors (space, corrupt data, HTTP 4xx) stop the run. */
    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof IOException) return true;
            String m = t.getMessage();
            if (t instanceof IllegalStateException && m != null) {
                if (m.startsWith("Download unvollständig")) return true;
                if (m.equals("HTTP 408") || m.equals("HTTP 429")) return true;
                if (m.startsWith("HTTP 5")) return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    static long backoffMs(int failures) {
        int i = Math.max(0, Math.min(BACKOFF_S.length - 1, failures - 1));
        return BACKOFF_S[i] * 1000L;
    }

    /**
     * Byte offset to continue a partial download from, or 0 to start over. Resuming needs a trustworthy
     * remote identity (size + Last-Modified, verified server-side with If-Range) and a strictly shorter part.
     */
    static long resumeOffset(long partLength, long remoteSize, long remoteModified) {
        if (partLength <= 0L || remoteSize <= 0L || remoteModified <= 0L) return 0L;
        if (partLength >= remoteSize) return 0L;
        return partLength;
    }
}
