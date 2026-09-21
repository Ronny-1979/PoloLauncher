package de.ronny.pololauncher;

import android.content.*;
import android.os.*;
import java.util.Set;
import java.util.concurrent.*;

public final class CanReconnectTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "Timed out waiting for simulated Binder"); }
    static final class Remote extends Binder {
        final Set<IBinder> callbacks = ConcurrentHashMap.newKeySet();
        final CountDownLatch blocked = new CountDownLatch(1), resume = new CountDownLatch(1);
        final CountDownLatch secondRegistered = new CountDownLatch(1), removed = new CountDownLatch(1);
        final boolean blockRegister;
        int registrations;
        volatile IBinder first, second;
        Remote(boolean blockRegister) { this.blockRegister = blockRegister; }
        @Override public boolean transact(int code, Parcel data, Parcel reply, int flags) {
            try {
                IBinder callback = data.readStrongBinder();
                if (code == 2) {
                    int count;
                    synchronized (this) { count = ++registrations; }
                    if (count == 1) {
                        first = callback;
                        if (blockRegister) { blocked.countDown(); await(resume); }
                    } else second = callback;
                    callbacks.add(callback);
                    if (count == 2) secondRegistered.countDown();
                } else if (code == 3) {
                    if (!blockRegister && callback == first) { blocked.countDown(); await(resume); }
                    callbacks.remove(callback); removed.countDown();
                }
                return true;
            } catch (Exception e) { throw new AssertionError(e); }
        }
    }
    static boolean registered() throws Exception {
        var flag = HctCanbusBinderRuntime.class.getDeclaredField("callbackRegistered"); flag.setAccessible(true);
        synchronized (HctCanbusBinderRuntime.class) { return flag.getBoolean(null); }
    }
    static void waitRegistered() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!registered() && System.nanoTime() < until) Thread.yield();
        check(registered(), "Current callback registered locally");
    }
    public static void main(String[] args) throws Exception {
        var field = HctCanbusBinderRuntime.class.getDeclaredField("CONNECTION"); field.setAccessible(true);
        ServiceConnection connection = (ServiceConnection) field.get(null);
        ComponentName name = new ComponentName("test", "Service");
        for (boolean blockRegister : new boolean[]{false, true}) {
            Context context = new Context(); Remote service = new Remote(blockRegister);
            HctCanbusBinderRuntime.acquire(context); connection.onServiceConnected(name, service);
            if (blockRegister) await(service.blocked); else waitRegistered();
            HctCanbusBinderRuntime.release(context);
            if (!blockRegister) await(service.blocked);
            HctCanbusBinderRuntime.acquire(context); connection.onServiceConnected(name, service);
            await(service.secondRegistered); waitRegistered();
            service.resume.countDown(); await(service.removed);
            check(service.first != service.second, "Every connection has its own callback identity");
            check(service.callbacks.contains(service.second) && !service.callbacks.contains(service.first),
                    "Late old cleanup cannot unregister the new connection");
            check(registered(), "Local and remote callback state agree");
            long frames = HctCanbusBinderRuntime.callbackFrameCount();
            Parcel data = Parcel.obtain(); data.writeByteArray(CanFrameTest.frame(0x24,(byte)0,(byte)2));
            service.first.transact(1, data, Parcel.obtain(), 0);
            check(HctCanbusBinderRuntime.callbackFrameCount() == frames, "Late old callback ignored");
            service.second.transact(1, data, Parcel.obtain(), 0);
            check(HctCanbusBinderRuntime.callbackFrameCount() == frames + 1, "Current callback still delivers");
            HctCanbusBinderRuntime.release(context);
        }
        System.out.println("CanReconnectTest: delayed registration, unregister and stale-frame cases passed");
    }
}
