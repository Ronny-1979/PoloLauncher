package de.ronny.pololauncher;

public final class MapUpdateScheduleTest {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("MapUpdateScheduleTest: " + msg);
    }

    public static void main(String[] args) {
        long day = 24L * 60L * 60L * 1000L;
        long now = 100L * day;
        check(MapUpdateSchedule.checkDue(now, 0L), "never checked: due");
        check(!MapUpdateSchedule.checkDue(now, now - 6 * day), "6 days: not due");
        check(MapUpdateSchedule.checkDue(now, now - 7 * day), "7 days: due");
        check(MapUpdateSchedule.checkDue(now, now + day), "check in the future (clock jump): due");

        check(!MapUpdateSchedule.promptDue(now, 0, 0L), "nothing pending: no popup");
        check(MapUpdateSchedule.promptDue(now, 3, 0L), "pending and no pause: popup");
        check(!MapUpdateSchedule.promptDue(now, 3, now + day / 2), "paused after Später: no popup");
        check(MapUpdateSchedule.promptDue(now, 3, now), "pause over: popup");
        check(MapUpdateSchedule.promptDue(now, 3, now + 10 * day), "pause absurdly far in the future (clock jump): popup");
        System.out.println("MapUpdateScheduleTest: all cases passed");
    }
}
