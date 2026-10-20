package de.ronny.pololauncher;

import android.content.Context;

public final class CombinedRangeModelTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static VehicleState state() {
        VehicleState s = new VehicleState();
        s.odometerKm = 100_000;
        s.fuelLiters = 30;
        return s;
    }

    static void savedAverage(Context context, double litresPer100Km) {
        Obd2Client.preferences(context).edit()
                .putLong("avg_weighted_liters_bits", Double.doubleToLongBits(litresPer100Km / 100d))
                .putLong("avg_weighted_km_bits", Double.doubleToLongBits(1d)).apply();
    }

    public static void main(String[] args) throws Exception {
        android.os.SystemClock.now = 1_000_000L;
        Context context = new Context();
        RangeStore.preferences(context).edit()
                .putBoolean("include_obd2_in_range", false)
                .putBoolean("automatic_learning", true)
                .putInt("consumption_tenths", 68)
                .putLong("learn_windows", 3L).apply();
        VehicleState s = state();
        s.rangeConsumption = 10;
        check(RangeStore.consumption(context, s) == 10,
                "Legacy CAN-only preference cannot override fixed combined model");
        RangeStore store = new RangeStore(context, System::currentTimeMillis);
        check(RangeStore.preferences(context).getBoolean("include_obd2_in_range", true),
                "Obsolete CAN-only preference is removed during migration");
        check(!RangeStore.preferences(context).getBoolean("automatic_learning", false)
                        && RangeStore.preferences(context).getInt("consumption_tenths", -1) == -1
                        && RangeStore.preferences(context).getLong("learn_windows", -1L) == -1L,
                "Retired CAN learning preferences are removed during migration");
        int combined = store.update(s, context);
        check(combined > 0, "CAN tank and live OBD average produce range");

        savedAverage(context, 10);
        for (double invalid : new double[]{Double.NaN, 0, 2, 21, Double.POSITIVE_INFINITY}) {
            s.rangeConsumption = invalid;
            check(RangeStore.consumption(context, s) == 10,
                    "Invalid live OBD value restores persisted OBD average");
            check(store.update(s, context) == combined,
                    "Estimator keeps using saved OBD average");
        }
        check(RangeStore.consumption(context, null) == 10,
                "Saved OBD average is available before a new live sample");
        store.save();
        store = new RangeStore(context, System::currentTimeMillis);
        check(store.update(s, context) == combined,
                "Combined range survives store reload");

        RangeTrackingSession session = new RangeTrackingSession(context, () -> 1000L, () -> {}, () -> {});
        session.start();
        session.update(s);
        session.stop();
        int cached = RangeDisplayStore.remainingKm(context);
        check(cached == combined && RangeDisplayStore.tankLitres(context) == 30,
                "Combined display is persisted");
        session = new RangeTrackingSession(context, () -> 1000L, () -> {}, () -> {});
        session.start();
        session.update(new VehicleState());
        check(RangeDisplayStore.remainingKm(context) == cached,
                "No CAN after restart preserves combined display");
        session.update(s);
        check(session.remainingKm() == combined,
                "New CAN resumes with saved OBD average");
        session.stop();

        Context refillContext = new Context();
        savedAverage(refillContext, 6.8);
        long[] refillNow = {1_800_000_000_000L};
        RangeStore refillStore = new RangeStore(refillContext, () -> refillNow[0]);
        VehicleState refillState = state();
        refillState.rangeConsumption = 6.8d;
        refillState.fuelLiters = 12d;
        check(refillStore.update(refillState, refillContext) >= 0,
                "Refill integration establishes the tank baseline");
        refillState.fuelLiters = 18d;
        refillNow[0] += 1_000L;
        refillStore.update(refillState, refillContext);
        refillNow[0] += 31_000L;
        refillStore.update(refillState, refillContext);
        check(Math.abs(RangeStore.consumption(refillContext, new VehicleState()) - 6.8d) < 0.0001d,
                "Confirmed refill preserves the independent rolling range profile");

        Context empty = new Context();
        check(Double.isNaN(RangeStore.consumption(empty, state())),
                "No OBD baseline never substitutes CAN-only consumption");
        check(new RangeStore(empty, System::currentTimeMillis).update(state(), empty) == -1,
                "No OBD baseline pauses estimator");

        synchronized (VehicleRepository.class) {
            VehicleState raw = VehicleRepository.mutable();
            raw.rangeConsumption = 9;
            raw.instConsumption = 12;
            raw.instConsumptionUpdatedAtMs = VehicleRepository.now() - 16_000;
        }
        VehicleState expired = VehicleRepository.snapshot();
        check(Double.isNaN(expired.rangeConsumption)
                        && Double.isNaN(expired.instConsumption),
                "Expired OBD readings are stripped before range calculation");
        check(RangeStore.consumption(context, expired) == 10,
                "Expired sample still uses saved rolling OBD baseline");
        synchronized (VehicleRepository.class) {
            VehicleRepository.mutable().instConsumptionUpdatedAtMs = VehicleRepository.now();
        }
        check(VehicleRepository.snapshot().rangeConsumption == 9,
                "Fresh OBD average survives snapshot copy");

        System.out.println("CombinedRangeModelTest: all cases passed");
    }
}
