package de.ronny.pololauncher;

import android.app.Application;

/** Process-wide initialization that must run before any launcher Activity. */
public final class PoloApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
        UiStallWatchdog.start(this);
        // Blur the glass wallpaper once in the background so the first UI draw finds it ready
        // (on the radio it once blocked the UI thread for 7 s while the system was busy).
        final android.content.Context app = this;
        Thread warm = new Thread(() -> GlassBlurCache.get(app), "glass-blur-warmup");
        warm.setDaemon(true);
        warm.start();
        if (CrashReporter.hasCrash(this))
            DiagLog.add("Vorheriger App-Absturz gespeichert: " + CrashReporter.summary(this));
        // If ACC/Android killed a running Germany build, the persisted request survives the process.
        // START_REDELIVER_INTENT is the primary recovery path; this is the vendor-firmware fallback.
        GermanyOfflineStore.autoResumePending(this);
    }
}
