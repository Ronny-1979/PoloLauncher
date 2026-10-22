package de.ronny.pololauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores a user-requested Germany build after a real Android boot/package restart. */
public final class GermanyOfflineResumeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            GermanyOfflineStore.autoResumePending(context);
        } else if (GermanyOfflineStore.ACTION_RESUME_TICK.equals(action)) {
            // Periodic safety net while a build/update is pending: restart a dead service, keep ticking.
            if (GermanyOfflineStore.runPending(context)) {
                GermanyOfflineStore.autoResumePending(context);
                GermanyOfflineStore.scheduleResumeTick(context);
            }
        }
    }
}
