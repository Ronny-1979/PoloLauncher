package de.ronny.pololauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Direct client for the exported HCT CANBUS Binder service on this QCM6125 firmware.
 *
 * Recovered from Ronny's original android.microntek.canbus v13 APK:
 *   action:      com.microntek.canbusserver
 *   package:     android.microntek.canbus
 *   descriptor:  android.microntek.canbus.aidl.CanBusServiceInf
 *
 * Important transaction numbers recovered from the generated AIDL Stub:
 *   2  registerCallback(ICanBusAidlCallBack)
 *   3  unregisterCallback(ICanBusAidlCallBack)
 *   36 sendCanbusData(byte[])
 *   38 getCanParameters(String)
 *   39 getCanbusType()
 *   40 getCanbusConfig()
 *
 * The original CanBusServer$5.sendCanbusData(byte[]) calls CanBusServer.WritePort(byte[])
 * directly, so this is the clean vendor-service path and avoids Android 13 hidden-API
 * access to android.microntek.CarManager. No UART is opened by this app.
 */
public final class HctCanbusBinderRuntime {
    private static final String ACTION = "com.microntek.canbusserver";
    private static final String PACKAGE = "android.microntek.canbus";
    private static final String SERVICE_DESCRIPTOR = "android.microntek.canbus.aidl.CanBusServiceInf";
    private static final String CALLBACK_DESCRIPTOR = "android.microntek.canbus.aidl.ICanBusAidlCallBack";

    private static final int TX_REGISTER_CALLBACK = 2;
    private static final int TX_UNREGISTER_CALLBACK = 3;
    private static final int TX_SEND_CANBUS_DATA = 36;
    private static final int TX_GET_CAN_PARAMETERS = 38;
    private static final int TX_GET_CANBUS_TYPE = 39;
    private static final int TX_GET_CANBUS_CONFIG = 40;

    private static Context appContext;
    private static IBinder remote;
    private static boolean bound;
    private static boolean binding;
    private static boolean callbackRegistered;
    private static Callback activeCallback;
    private static long nextBindAtMs;
    private static int refs;
    private static long callbackFrames;
    private static String lastTransport = "passiv – nichts gesendet";
    private static long lastMode2RawLogAt;
    private static final Map<Integer, Long> CALLBACK_FUNCTION_COUNTS = new LinkedHashMap<>();
    private static final Set<String> UNIQUE_CALLBACK_FRAMES = new LinkedHashSet<>();
    private static final Set<Integer> HIGHLIGHTED_TYPE1_UNMAPPED = new LinkedHashSet<>();
    private static final int MAX_LOGGED_UNIQUE_FRAMES = 400;
    private static final HctSyncDecoder RAW_DECODER = new HctSyncDecoder();
    // Two bounded workers keep teardown/reconnect live even if one vendor transact hangs.
    private static final ThreadPoolExecutor BINDER_IO = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
        Thread thread = new Thread(r, "polo-hct-binder");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    private HctCanbusBinderRuntime() {}

    public static synchronized void acquire(Context context) {
        refs++;
        appContext = context.getApplicationContext();
        if (refs == 1) ensureBoundLocked();
    }

    public static void release(Context context) {
        IBinder callbackBinder = null;
        Callback oldCallback;
        synchronized (HctCanbusBinderRuntime.class) {
            refs = Math.max(0, refs - 1);
            if (refs != 0) return;
            if (callbackRegistered) callbackBinder = remote;
            oldCallback = activeCallback;
            callbackRegistered = false;
            unbindLocked();
            nextBindAtMs = 0L;
            appContext = null;
        }
        // A vendor Binder has no client-side timeout. Never wait for it from an
        // Activity/service lifecycle callback on Android's main thread.
        if (callbackBinder != null) {
            IBinder binder = callbackBinder;
            executeBinderTask("Callback abmelden", () -> unregisterCallback(binder, oldCallback));
        }
    }

    public static synchronized void ensureBound(Context context) {
        appContext = context.getApplicationContext();
        ensureBoundLocked();
    }

    private static void ensureBoundLocked() {
        if (refs <= 0 || bound || remote != null || binding || appContext == null
                || android.os.SystemClock.elapsedRealtime() < nextBindAtMs) return;
        binding = true;
        Intent i = new Intent(ACTION);
        i.setClassName(PACKAGE, PACKAGE + ".CanBusServer");
        try {
            boolean ok = appContext.bindService(i, CONNECTION, Context.BIND_AUTO_CREATE);
            bound = ok;
            if (!ok) {
                binding = false;
                nextBindAtMs = android.os.SystemClock.elapsedRealtime() + 5_000L;
                DiagLog.add("HCT AIDL: bindService lieferte false für " + ACTION);
            } else {
                DiagLog.add("HCT AIDL: Bind angefordert -> " + ACTION);
            }
        } catch (Throwable t) {
            binding = false;
            bound = false;
            nextBindAtMs = android.os.SystemClock.elapsedRealtime() + 5_000L;
            DiagLog.add("HCT AIDL: Bind FEHLER: " + root(t));
        }
    }

    private static void unbindLocked() {
        if (bound && appContext != null) {
            try { appContext.unbindService(CONNECTION); }
            catch (Throwable t) { DiagLog.add("HCT AIDL Unbind: " + root(t)); }
        }
        bound = false;
        binding = false;
        callbackRegistered = false;
        activeCallback = null;
        remote = null;
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            final Callback callback = new Callback();
            synchronized (HctCanbusBinderRuntime.class) {
                if (refs <= 0 || !bound) { unbindLocked(); return; }
                remote = service;
                activeCallback = callback;
                callbackRegistered = false;
                binding = false;
                bound = true;
                DiagLog.add("HCT AIDL VERBUNDEN: " + name.flattenToShortString());
            }
            // ServiceConnection callbacks run on the main thread. Registration,
            // descriptor lookup and diagnostics are synchronous vendor calls and
            // therefore belong on the dedicated Binder worker.
            executeBinderTask("Verbindung initialisieren", () -> initializeConnectedBinder(service, callback));
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (HctCanbusBinderRuntime.class) {
                DiagLog.add("HCT AIDL getrennt: " + name.flattenToShortString());
                remote = null;
                callbackRegistered = false;
                activeCallback = null;
                binding = false;
                // Android retains the binding and reconnects it automatically.
                // Keep bound=true so release() can still unbind this connection.
            }
        }

        @Override public void onBindingDied(ComponentName name) {
            synchronized (HctCanbusBinderRuntime.class) {
                DiagLog.add("HCT AIDL Binding gestorben: " + name.flattenToShortString());
                unbindLocked();
                nextBindAtMs = android.os.SystemClock.elapsedRealtime() + 5_000L;
            }
        }

        @Override public void onNullBinding(ComponentName name) {
            synchronized (HctCanbusBinderRuntime.class) {
                DiagLog.add("HCT AIDL Null-Binding: " + name.flattenToShortString());
                unbindLocked();
                nextBindAtMs = android.os.SystemClock.elapsedRealtime() + 5_000L;
            }
        }
    };

    // Every connection owns a different Binder identity. A late unregister can only
    // remove its own callback, even when Android reconnects to the same service.
    private static final class Callback extends Binder {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK_DESCRIPTOR);
                return true;
            }
            if (code == 1) { // ICanBusAidlCallBack.CanBusServiceData(byte[])
                data.enforceInterface(CALLBACK_DESCRIPTOR);
                byte[] raw = data.createByteArray();
                synchronized (HctCanbusBinderRuntime.class) {
                    if (activeCallback == this && refs > 0) handleCallbackFrame(raw);
                }
                if (reply != null) reply.writeNoException();
                return true;
            }
            if (code == 2) { // UpdateAirUiView()
                data.enforceInterface(CALLBACK_DESCRIPTOR);
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private static void initializeConnectedBinder(IBinder service, Callback callback) {
        try {
            if (!registerCallback(service, callback)) return;
        } catch (Throwable t) {
            DiagLog.add("HCT AIDL Callback Registrierung FEHLER: " + root(t));
            return;
        }
        DiagLog.add("HCT AIDL descriptor=" + safeDescriptor(service));
        logServiceInfo(service);
    }

    private static boolean registerCallback(IBinder binder, Callback callback) throws RemoteException {
        synchronized (HctCanbusBinderRuntime.class) {
            if (remote != binder || activeCallback != callback || refs <= 0 || callbackRegistered) return false;
        }
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            d.writeStrongBinder(callback);
            if (!binder.transact(TX_REGISTER_CALLBACK, d, r, 0)) {
                throw new RemoteException("transact(registerCallback) lieferte false");
            }
            r.readException();
        } finally {
            r.recycle();
            d.recycle();
        }
        synchronized (HctCanbusBinderRuntime.class) {
            if (remote == binder && activeCallback == callback && refs > 0) {
                callbackRegistered = true;
                DiagLog.add("HCT AIDL Callback registriert (passiver Rohdaten-Empfang)");
                return true;
            }
        }
        // The owner disappeared while the remote call was running. Undo the stale
        // registration on this same worker without blocking the main thread.
        unregisterCallback(binder, callback);
        return false;
    }

    private static void unregisterCallback(IBinder binder, Callback callback) {
        if (binder == null) return;
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            d.writeStrongBinder(callback);
            if (binder.transact(TX_UNREGISTER_CALLBACK, d, r, 0)) r.readException();
        } catch (Throwable ignored) {
        } finally {
            r.recycle();
            d.recycle();
        }
    }

    private static void handleCallbackFrame(byte[] raw) {
        if (raw == null) return;
        int function = -1;
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0x2E) function = raw[1] & 0xFF;
        else if (raw.length >= 1) function = raw[0] & 0xFF;

        synchronized (HctCanbusBinderRuntime.class) {
            callbackFrames++;
            if (function >= 0) {
                CALLBACK_FUNCTION_COUNTS.put(function, CALLBACK_FUNCTION_COUNTS.getOrDefault(function, 0L) + 1L);
            }
            // 0x41/Mode2 changes almost every sample because RPM/voltage move slightly.
            // Older builds filled the ring buffer with hundreds of equivalent lines and
            // hid the rare frames we are actually looking for. In v0.14 Mode2 is sampled
            // at most once every 3 s while every other distinct frame stays visible.
            boolean noisyMode2 = raw.length >= 4 && (raw[0] & 0xFF) == 0x2E
                    && (raw[1] & 0xFF) == 0x41 && (raw[3] & 0xFF) == 0x02;
            long now = android.os.SystemClock.uptimeMillis();
            boolean allowUniqueLog = !noisyMode2 || now - lastMode2RawLogAt >= 3000L;
            String hx = null;
            if (allowUniqueLog && UNIQUE_CALLBACK_FRAMES.size() < MAX_LOGGED_UNIQUE_FRAMES) {
                hx = Hex.of(raw);
            }
            if (hx != null && UNIQUE_CALLBACK_FRAMES.add(hx)) {
                if (noisyMode2) lastMode2RawLogAt = now;
                DiagLog.add(String.format(Locale.US, "AIDL RX NEU%s [%s]",
                        function >= 0 ? String.format(Locale.US, " fn=0x%02X", function) : "", hx));
            }
            if (function >= 0 && isType1UnmappedFunction(function)
                    && HIGHLIGHTED_TYPE1_UNMAPPED.add(function)) {
                if (hx == null) hx = Hex.of(raw);
                DiagLog.add(String.format(Locale.US,
                        "CANBUS1 UNGEKLÄRT fn=0x%02X erster Frame=[%s]", function, hx));
            }
        }

        // If callback supplies one complete 0x2E serial frame, strip start/checksum
        // and feed the already proven HCT SYNC decoder. If it already supplies
        // [function,len,payload...], feed it directly.
        try {
            byte[] normalized = CanFrameNormalizer.normalize(raw);
            if (normalized != null) RAW_DECODER.feed(normalized);
            else DiagLog.add("AIDL RX verworfen: Länge/Prüfsumme ungültig");
        } catch (Throwable t) {
            DiagLog.add("AIDL RX Decoder FEHLER: " + root(t));
        }
    }

    public static synchronized boolean isConnected() {
        return remote != null && remote.isBinderAlive();
    }

    public static synchronized long callbackFrameCount() { return callbackFrames; }
    public static synchronized String lastTransport() { return lastTransport; }
    public static synchronized int uniqueCallbackFrameCount() { return UNIQUE_CALLBACK_FRAMES.size(); }

    public static synchronized void resetCallbackStats() {
        callbackFrames = 0L;
        lastTransport = "passiv – nichts gesendet";
        CALLBACK_FUNCTION_COUNTS.clear();
        UNIQUE_CALLBACK_FRAMES.clear();
        HIGHLIGHTED_TYPE1_UNMAPPED.clear();
        lastMode2RawLogAt = 0L;
    }

    public static synchronized String callbackSummary() {
        if (CALLBACK_FUNCTION_COUNTS.isEmpty()) return "noch keine AIDL-Rohdaten";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Long> e : CALLBACK_FUNCTION_COUNTS.entrySet()) {
            if (out.length() > 0) out.append("  ");
            out.append(String.format(Locale.US, "0x%02X:%d", e.getKey(), e.getValue()));
        }
        out.append(" | unique=").append(UNIQUE_CALLBACK_FRAMES.size());
        return out.toString();
    }

    /** Exact function roles recovered for this radio's CANBUS type-1 decoder. */
    public static String type1KnownMap() {
        return "0x16=Speed | 0x21=Klima | 0x22/0x23=Radar | 0x24=Türen | " +
                "0x26=Lenkwinkel/Rückfahrpfad | 0x30=CANBOX-Version | 0x41=Fahrzeugdaten | 0x69=AppData";
    }

    /** Functions not assigned by the original Canbus01 CmdProc. 0x27 is excluded here
     * because the OEM init path explicitly requests it and it behaves like init/status. */
    public static synchronized String callbackType1UnmappedSummary() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Long> e : CALLBACK_FUNCTION_COUNTS.entrySet()) {
            int fn = e.getKey();
            if (!isType1UnmappedFunction(fn)) continue;
            if (out.length() > 0) out.append("  ");
            out.append(String.format(Locale.US, "0x%02X:%d", fn, e.getValue()));
        }
        return out.length() == 0 ? "keine" : out.toString();
    }

    private static boolean isType1UnmappedFunction(int fn) {
        switch (fn) {
            case 0x16:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x26:
            case 0x27: // OEM-init/status response; not a confirmed sensor value
            case 0x30:
            case 0x41:
            case 0x69:
                return false;
            default:
                return true;
        }
    }

    public static void logServiceInfo() {
        IBinder b;
        synchronized (HctCanbusBinderRuntime.class) { b = remote; }
        if (b == null) {
            DiagLog.add("=== HCT CANBUS SERVICE INFO: noch nicht verbunden ===");
            return;
        }
        executeBinderTask("Service-Info", () -> logServiceInfo(b));
    }

    private static void executeBinderTask(String name, Runnable task) {
        try { BINDER_IO.execute(task); }
        catch (RejectedExecutionException full) {
            DiagLog.add("HCT AIDL: Binder-Warteschlange voll, verworfen: " + name);
        }
    }

    private static void logServiceInfo(IBinder b) {
        DiagLog.add("=== HCT CANBUS SERVICE INFO ===");
        try { DiagLog.add("AIDL getCanbusType() = " + getCanbusType(b)); }
        catch (Throwable t) { DiagLog.add("AIDL getCanbusType FEHLER: " + root(t)); }
        try { DiagLog.add("AIDL getCanbusConfig() = " + Arrays.toString(getCanbusConfig(b))); }
        catch (Throwable t) { DiagLog.add("AIDL getCanbusConfig FEHLER: " + root(t)); }
        String[] keys = {"cfg_canbus=", "cfg_canbus_cfg=", "cfg_cansub_1=", "cfg_cansub_6=", "cfg_cansub_8=", "cfg_cansub_10=", "cfg_cansub_11="};
        for (String key : keys) {
            try { DiagLog.add("AIDL PARAM " + key + " " + printable(getCanParameters(b, key))); }
            catch (Throwable t) { DiagLog.add("AIDL PARAM " + key + " FEHLER: " + root(t)); }
        }
        DiagLog.add("=== HCT CANBUS SERVICE INFO ENDE ===");
    }

    /**
     * Sends one complete CANBOX frame through the vendor's own exported Binder service.
     * Returns false while the service is still connecting; callers can simply retry.
     * No direct UART and no hidden CarManager API are used in this process.
     */
    public static boolean sendCanbusData(Context context, byte[] frame) throws Exception {
        if (frame == null || frame.length == 0) throw new IllegalArgumentException("leerer CANBOX-Frame");
        IBinder b;
        synchronized (HctCanbusBinderRuntime.class) {
            appContext = context.getApplicationContext();
            ensureBoundLocked();
            b = remote;
        }
        if (b == null || !b.isBinderAlive()) return false;
        sendViaBinder(b, frame);
        synchronized (HctCanbusBinderRuntime.class) { lastTransport = "HCT AIDL sendCanbusData"; }
        return true;
    }

    private static void sendViaBinder(IBinder b, byte[] frame) throws Exception {
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            d.writeByteArray(frame);
            if (!b.transact(TX_SEND_CANBUS_DATA, d, r, 0)) {
                throw new RemoteException("transact(sendCanbusData) lieferte false");
            }
            r.readException();
        } finally {
            r.recycle();
            d.recycle();
        }
    }

    private static int getCanbusType(IBinder b) throws Exception {
        Parcel d = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            if (!b.transact(TX_GET_CANBUS_TYPE, d, r, 0)) throw new RemoteException("getCanbusType transact=false");
            r.readException();
            return r.readInt();
        } finally { r.recycle(); d.recycle(); }
    }

    private static int[] getCanbusConfig(IBinder b) throws Exception {
        Parcel d = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            if (!b.transact(TX_GET_CANBUS_CONFIG, d, r, 0)) throw new RemoteException("getCanbusConfig transact=false");
            r.readException();
            return r.createIntArray();
        } finally { r.recycle(); d.recycle(); }
    }

    private static String getCanParameters(IBinder b, String key) throws Exception {
        Parcel d = Parcel.obtain(); Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(SERVICE_DESCRIPTOR);
            d.writeString(key);
            if (!b.transact(TX_GET_CAN_PARAMETERS, d, r, 0)) throw new RemoteException("getCanParameters transact=false");
            r.readException();
            return r.readString();
        } finally { r.recycle(); d.recycle(); }
    }

    public static byte[] build2E(int command, byte[] payload) {
        int len = payload == null ? 0 : payload.length;
        byte[] out = new byte[len + 4];
        out[0] = 0x2E;
        out[1] = (byte) command;
        out[2] = (byte) len;
        int sum = (out[1] & 0xFF) + (out[2] & 0xFF);
        for (int i = 0; i < len; i++) {
            out[3 + i] = payload[i];
            sum += payload[i] & 0xFF;
        }
        out[out.length - 1] = (byte) ((sum & 0xFF) ^ 0xFF);
        return out;
    }

    public static byte[] buildCarInfoQuery(int mode) {
        return build2E(0x90, new byte[] { 0x41, (byte) mode });
    }

    /** Generic OEM read request used by controlinfo: 0x90 [function, sub]. */
    public static byte[] buildReadQuery(int function, int sub) {
        return build2E(0x90, new byte[] { (byte) function, (byte) sub });
    }

    private static String safeDescriptor(IBinder b) {
        try { return b.getInterfaceDescriptor(); } catch (Throwable t) { return "?"; }
    }

    private static String printable(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) b.append(c);
            else b.append(String.format(Locale.US, "\\u%04X", (int)c));
        }
        return b.toString();
    }

    public static String root(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        String m = x.getMessage();
        return x.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }
}
