package de.ronny.pololauncher;

public final class GermanyOfflineUpdatePolicyTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long localTime = 1_700_000_000_000L;
        long localSize = 500_000_000L;

        check(!GermanyOfflineUpdatePolicy.updateAvailable(
                        localSize, localTime, localSize, localTime),
                "identical object is current");
        check(GermanyOfflineUpdatePolicy.updateAvailable(
                        localSize, localTime, localSize, localTime + 60_000L),
                "clearly newer timestamp is an update even at same size");
        check(GermanyOfflineUpdatePolicy.updateAvailable(
                        localSize, localTime, localSize + 1234L, localTime),
                "same-date repack with changed size is an update");
        check(!GermanyOfflineUpdatePolicy.updateAvailable(
                        localSize, localTime, localSize + 1234L, localTime - 60_000L),
                "clearly older remote is not allowed to replace a working local file");
        check(!GermanyOfflineUpdatePolicy.updateAvailable(
                        localSize, localTime, -1L, -1L),
                "missing remote identity never forces an update");
        check(GermanyOfflineUpdatePolicy.updateAvailable(
                        -1L, -1L, localSize, localTime),
                "missing local artifact is replaceable when the remote exists");

        System.out.println("GermanyOfflineUpdatePolicyTest: explicit Germany update comparison passed");
    }
}
