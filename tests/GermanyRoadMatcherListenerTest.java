package de.ronny.pololauncher;

import android.content.Context;
import android.location.Location;
import android.view.View;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression for v0.31.68 "map stops following": MainActivity hands the matcher an inline lambda
 * that nothing else references. The matcher must keep its listener alive itself; otherwise the
 * first garbage collection silently drops every camera position and the live map freezes
 * while raw GPS (speed limits) keeps working.
 */
public final class GermanyRoadMatcherListenerTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        Context context = new Context();
        // No Germany traffic index in the stub context => matcher passes raw fixes straight through.
        GermanyRoadPositionMatcher matcher = new GermanyRoadPositionMatcher(context, new View(),
                (source, display, snapped) -> delivered.incrementAndGet());
        check(!matcher.available(), "test setup: stub context has no traffic index");

        Location fix = new Location("gps");
        fix.setLatitude(51.05); fix.setLongitude(13.74); fix.setAccuracy(5f);

        matcher.accept(fix);
        check(delivered.get() == 1, "first fix is delivered");

        for (int i = 0; i < 8; i++) { System.gc(); System.runFinalization(); Thread.sleep(20); }

        matcher.accept(fix);
        matcher.accept(fix);
        check(delivered.get() == 3,
                "fixes after garbage collection must still reach the map (delivered=" + delivered.get() + ")");
        matcher.close();
        System.out.println("GermanyRoadMatcherListenerTest: matcher keeps its position listener alive across GC");
    }
}
