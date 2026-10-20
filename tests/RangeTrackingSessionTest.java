package de.ronny.pololauncher;

import android.content.Context;

public final class RangeTrackingSessionTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static VehicleState state(long km, double tank) {
        return state(km, tank, 6.8);
    }
    static VehicleState state(long km, double tank, double consumption) {
        VehicleState s = new VehicleState();
        s.odometerKm = km; s.fuelLiters = tank; s.rangeConsumption = consumption;
        return s;
    }
    public static void main(String[] args) {
        Context context = new Context();
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
        Context smoothingContext = new Context();
        RangeDisplayStore.record(smoothingContext, 30, 300);
        long[] smoothingTime = {1000};
        RangeTrackingSession smoothed = new RangeTrackingSession(smoothingContext, () -> smoothingTime[0],
                () -> refs[0]++, () -> refs[0]--);
        smoothed.start(); smoothed.update(state(100_000, 30, 15));
        check(smoothed.remainingKm() == 300, "Session holds cached range through startup dip");
        check(RangeStore.consumption(smoothingContext, state(100_000, 30, 15)) == 15,
                "Display never changes OBD calculation basis");
        smoothingTime[0] += 5500; smoothed.update(state(100_000, 30, 15));
        check(smoothed.remainingKm() == 296 && RangeDisplayStore.remainingKm(smoothingContext) == 296,
                "Session persists gradually adjusted display");
        smoothed.stop();
        check(refs[0] == 0, "Filtered session releases CAN");
        System.out.println("RangeTrackingSessionTest: all cases passed");
    }
}
