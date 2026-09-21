package de.ronny.pololauncher;

import android.content.Context;

public final class RangeTrackingSessionTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static VehicleState state(long km, double tank) {
        VehicleState s = new VehicleState(); s.odometerKm = km; s.fuelLiters = tank; return s;
    }
    public static void main(String[] args) {
        Context context = new Context();
        RangeStore.setIncludeObd2InRange(context, false);
        long[] monotonic = {1_000};
        int[] refs = {0};
        RangeTrackingSession session = new RangeTrackingSession(context, () -> monotonic[0],
                () -> refs[0]++, () -> refs[0]--);
        check(session.remainingKm() == -1, "No stale value before service start");
        check(Double.isNaN(RangeDisplayStore.tankLitres(context)) && RangeDisplayStore.remainingKm(context) == -1,
                "New installation has no invented display values");
        session.start(); session.start();
        check(refs[0] == 1, "Repeated service starts acquire CAN once");
        session.update(state(100_000, 30));
        int initial = session.remainingKm();
        check(initial > 0, "Service publishes live range");
        // No dashboard involved: continued service sampling reduces the range.
        monotonic[0] += 500;
        session.update(state(100_001, 30));
        check(session.remainingKm() < initial, "Background kilometres reduce range without dashboard");
        int cachedRange = RangeDisplayStore.remainingKm(context);
        check(cachedRange == session.remainingKm() && RangeDisplayStore.tankLitres(context) == 30,
                "Live values saved as display-only snapshot");
        monotonic[0] += 2_001;
        check(session.remainingKm() == -1, "Stalled service result is not shown as live");
        session.update(state(-1, Double.NaN));
        check(session.remainingKm() == -1, "Missing CAN data does not invent consumption");
        check(RangeDisplayStore.remainingKm(context) == cachedRange && RangeDisplayStore.tankLitres(context) == 30,
                "CAN loss preserves both display values");
        RangeDisplayStore.record(context, Double.POSITIVE_INFINITY, -1);
        RangeDisplayStore.record(context, -1, 99_999);
        check(RangeDisplayStore.remainingKm(context) == cachedRange && RangeDisplayStore.tankLitres(context) == 30,
                "Invalid input cannot overwrite saved display values");
        session.update(state(100_002, 30));
        int beforeStop = session.remainingKm();
        session.stop(); session.stop();
        check(refs[0] == 0 && !session.isActive(), "Shutdown releases CAN once");
        session.update(state(100_003, 30));
        check(session.remainingKm() == -1, "Stopped session cannot publish");
        RangeTrackingSession restarted = new RangeTrackingSession(context, () -> monotonic[0],
                () -> refs[0]++, () -> refs[0]--);
        check(RangeDisplayStore.remainingKm(context) == beforeStop && RangeDisplayStore.tankLitres(context) == 30,
                "Last display immediately available before restarted service has CAN");
        restarted.start(); restarted.update(state(100_002, 30));
        check(restarted.remainingKm() == beforeStop, "Persisted range survives service restart");
        restarted.update(state(100_002, 29));
        check(RangeDisplayStore.tankLitres(context) == 29, "New CAN tank replaces saved tank");
        restarted.stop();
        check(refs[0] == 0, "Restart also releases CAN");
        // Real session -> RangeStore -> learner, without any Activity callbacks.
        Context learningContext = new Context();
        RangeStore.setIncludeObd2InRange(learningContext, false);
        long[] wall = {1_800_000_000_000L};
        RangeTrackingSession learning = new RangeTrackingSession(learningContext, () -> monotonic[0],
                () -> wall[0], () -> refs[0]++, () -> refs[0]--);
        learning.start();
        for (int i = 0; i <= 3; i++) {
            learning.update(state(100_000, 30)); wall[0] += 10_000;
        }
        wall[0] += 3_600_000;
        for (int i = 0; i <= 3; i++) {
            learning.update(state(100_100, 23)); wall[0] += 10_000;
        }
        check(RangeStore.learnedWindows(learningContext) == 1, "Service session learns without dashboard");
        check(RangeStore.consumption(learningContext) > 6.8, "Background learning changes effective rate");
        double rate = RangeStore.consumption(learningContext);
        int windows = (int) RangeStore.learnedWindows(learningContext);
        int savedRange = RangeDisplayStore.remainingKm(learningContext);
        for (int i = 0; i < 20; i++) {
            wall[0] += 10_000;
            learning.update(state(-1, Double.NaN));
        }
        check(RangeStore.learnedWindows(learningContext) == windows && RangeStore.consumption(learningContext) == rate,
                "Saved display cannot produce additional learning during CAN outage");
        check(RangeDisplayStore.remainingKm(learningContext) == savedRange && RangeDisplayStore.tankLitres(learningContext) == 23,
                "Long CAN outage keeps last-known display frozen");
        learning.stop();
        RangeTrackingSession learningRestart = new RangeTrackingSession(learningContext, () -> monotonic[0],
                () -> wall[0], () -> refs[0]++, () -> refs[0]--);
        learningRestart.start();
        learningRestart.update(state(100_100, 23));
        check(RangeStore.consumption(learningContext) == rate, "Learned rate retained on restart");
        RangeStore.resetLearning(learningContext);
        learningRestart.update(state(100_100, 23));
        check(RangeStore.learnedWindows(learningContext) == 0 && RangeStore.consumption(learningContext) == 6.8,
                "Settings reset not overwritten by running service");
        learningRestart.stop();
        check(refs[0] == 0, "Learning sessions release CAN references");
        Context smoothingContext = new Context();
        RangeStore.setIncludeObd2InRange(smoothingContext, false);
        RangeStore.preferences(smoothingContext).edit().putInt("consumption_tenths", 150).apply();
        RangeDisplayStore.record(smoothingContext, 30, 300);
        long[] smoothingTime = {1000};
        RangeTrackingSession smoothed = new RangeTrackingSession(smoothingContext, () -> smoothingTime[0],
                () -> refs[0]++, () -> refs[0]--);
        smoothed.start(); smoothed.update(state(100_000, 30));
        check(smoothed.remainingKm() == 300, "Session holds cached range through startup dip");
        check(RangeStore.consumption(smoothingContext) == 15, "Display never changes calculation basis");
        smoothingTime[0] += 5500; smoothed.update(state(100_000, 30));
        check(smoothed.remainingKm() == 296 && RangeDisplayStore.remainingKm(smoothingContext) == 296,
                "Session persists gradually adjusted display");
        smoothed.stop();
        check(refs[0] == 0, "Filtered session releases CAN");
        System.out.println("RangeTrackingSessionTest: all cases passed");
    }
}
