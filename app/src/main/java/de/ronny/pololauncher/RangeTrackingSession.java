package de.ronny.pololauncher;

import android.content.Context;
import java.util.function.LongSupplier;

/** One persistent writer, owned by the service, never by the dashboard. */
final class RangeTrackingSession {
    private final Context context;
    private final RangeStore store;
    private final RangeDisplayFilter displayFilter;
    private final LongSupplier clock;
    private final Runnable acquireCan;
    private final Runnable releaseCan;
    private boolean active;
    private int remaining = -1;
    private long updatedAt;
    private boolean published;

    RangeTrackingSession(Context context, LongSupplier clock, Runnable acquireCan, Runnable releaseCan) {
        this(context, clock, System::currentTimeMillis, acquireCan, releaseCan);
    }

    RangeTrackingSession(Context context, LongSupplier clock, LongSupplier wallClock,
                         Runnable acquireCan, Runnable releaseCan) {
        this.context = context.getApplicationContext();
        this.clock = clock;
        this.acquireCan = acquireCan;
        this.releaseCan = releaseCan;
        store = new RangeStore(this.context, wallClock);
        displayFilter = new RangeDisplayFilter(RangeDisplayStore.remainingKm(this.context));
    }

    synchronized void start() {
        if (active) return;
        acquireCan.run();
        active = true;
    }

    synchronized void update(VehicleState state) {
        if (!active) return;
        int computed = store.update(state, context);
        remaining = displayFilter.update(computed, clock.getAsLong());
        RangeDisplayStore.record(context, state.fuelLiters, remaining);
        updatedAt = clock.getAsLong();
        published = true;
    }

    synchronized int remainingKm() {
        long age = clock.getAsLong() - updatedAt;
        return active && published && age >= 0 && age <= 2_000L ? remaining : -1;
    }

    synchronized boolean isActive() { return active; }

    synchronized void stop() {
        if (!active) return;
        active = false;
        remaining = -1;
        published = false;
        try { store.save(); } finally { releaseCan.run(); }
    }
}
