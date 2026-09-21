package de.ronny.pololauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Receiver for vendor/system CAN broadcasts. It only listens (the read-only poll requests are sent
 * by {@link HctOemAutoPoller}) and does not communicate with the OEM "Fahrzeug" app.
 *
 * <p>The receiver is registered as exported because the vendor apps send unprotected broadcasts,
 * so any app can reach it. Therefore: the whole {@link #onReceive} body is guarded, and the
 * generic key-name heuristics ({@link #applyNumeric}/{@link #applyBoolean}) are OFF unless the
 * user switches on "Sniffer-Heuristik" in the diagnostics settings. Confirmed frames
 * ({@code com.microntek.sync}, exact known actions) are always decoded.</p>
 */
public final class SystemCanReceiver extends BroadcastReceiver {
    /** Vendor Parcelable reflection must never hold Android's broadcast/main thread. */
    private static final ThreadPoolExecutor VENDOR_INSPECTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
        Thread t = new Thread(r, "can-vendor-inspector");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardOldestPolicy());
    /** Diagnostic-only switch, see class comment. Refreshed from preferences on every broadcast. */
    static final String PREFS_DEBUG = "cockpit_debug";
    static final String KEY_SNIFFER = "heuristic_sniffer";
    private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
    volatile boolean heuristicSniffer;
    private long lastFailureLogAt;
    private boolean registered;
    private final HctSyncDecoder hctSync = new HctSyncDecoder();
    private final SerialCanFramer serial = new SerialCanFramer(bytes -> hctSync.feed(bytes));
    private final LinkedHashSet<String> actions = new LinkedHashSet<>();
    private int lastLoggedRpm = Integer.MIN_VALUE;
    private double lastLoggedSpeed = Double.NaN;
    private double lastLoggedTemperature = Double.NaN;

    private static final String[] BASE_ACTIONS = {
            "com.microntek.sync",
            "com.microntek.canbus.rpm",
            "com.microntek.canbus.speed",
            "com.canbus.temperature",
            "com.microntek.systemui",
            "com.microntek.controlinfo.door",
            "com.microntek.controlinfo.canbus61carinfo",
            "com.microntek.controlinfo.canbus83carinfo",
            "com.microntek.canbusdisplay",
            "com.microntek.canbus20activity",
            "com.microntek.btcanbusinfo",
            "com.hiworld.canbus.send",
            "com.ahucanbus.display",
            "com.microntek.canbusinfo",
            "com.microntek.canbus.info",
            "com.microntek.carinfo",
            "com.microntek.controlinfo",
            "com.microntek.mcu",
            // Additional action strings found in the original HCT CANBUS APK.
            // Some are packages on certain firmware variants and broadcast actions on others;
            // registering them is passive and harmless.
            "com.microntek.travel",
            "com.microntek.dashboard",
            "com.microntek.mycar",
            "com.microntek.canbus.receiver",
            "com.microntek.canbus.send",
            "com.microntek.canbusdoor",
            "com.microntek.air"
    };

    public SystemCanReceiver() {
        for (String a : BASE_ACTIONS) actions.add(a);
    }

    public synchronized void start(Context context) {
        if (registered) return;
        register(context.getApplicationContext());
        DiagLog.add("Passiver HCT/Microntek-Empfänger aktiv (" + actions.size() + " Actions)");
    }

    public synchronized void addActions(Context context, Collection<String> more) {
        if (more == null || more.isEmpty()) return;
        int before = actions.size();
        for (String a : more) if (isPlausibleAction(a)) actions.add(a);
        if (actions.size() == before) return;
        Context app = context.getApplicationContext();
        if (registered) {
            try { app.unregisterReceiver(this); } catch (Throwable ignored) {}
            registered = false;
        }
        register(app);
        DiagLog.add("Sniffer erweitert: " + before + " -> " + actions.size() + " Actions");
    }

    public synchronized int actionCount() { return actions.size(); }
    public synchronized Set<String> actionSnapshot() { return new LinkedHashSet<>(actions); }
    public String syncSummary() { return hctSync.summary(); }
    public long syncPacketCount() { return hctSync.validPacketCount(); }
    public int lastDoorMask() { return hctSync.lastDoorMask(); }

    public synchronized void stop(Context context) {
        if (!registered) return;
        try { context.getApplicationContext().unregisterReceiver(this); } catch (Exception ignored) {}
        registered = false;
    }

    private void register(Context context) {
        try {
            IntentFilter f = new IntentFilter();
            for (String a : actions) f.addAction(a);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(this, f, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(this, f);
            registered = true;
        } catch (RuntimeException failed) {
            // A failed registration must not take the caller (foreground service start) down.
            registered = false;
            DiagLog.add("HCT/Microntek-Empfänger konnte nicht registriert werden: " + failed);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        // Runs on the main thread and is reachable by other apps: a malformed extra (for example
        // a Parcelable class that cannot be unparcelled) must never crash the launcher process.
        try {
            heuristicSniffer = context != null
                    && context.getSharedPreferences(PREFS_DEBUG, Context.MODE_PRIVATE).getBoolean(KEY_SNIFFER, false);
            handleBroadcast(intent);
        } catch (Throwable failure) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastFailureLogAt >= FAILURE_LOG_INTERVAL_MS) {
                lastFailureLogAt = now;
                DiagLog.add("Broadcast verworfen (" + failure.getClass().getSimpleName() + "): action="
                        + intent.getAction());
            }
        }
    }

    private void handleBroadcast(Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        // Decide before formatting the extras. Mode2 arrives several times per second;
        // decoding remains active, but suppressed lines must not allocate hex strings
        // and descriptions that are thrown away immediately afterwards.
        boolean noisySyncMode2 = isNoisySyncMode2(action, extras);
        boolean logGeneric = !"com.microntek.canbus.rpm".equals(action)
                && !"com.microntek.canbus.speed".equals(action)
                && !noisySyncMode2;
        StringBuilder line = logGeneric ? new StringBuilder("BROADCAST ").append(action) : null;
        if (extras != null && !extras.isEmpty()) {
            consumeKnownBroadcast(action, extras);
            for (String key : extras.keySet()) {
                Object value;
                try { value = extras.get(key); } catch (Throwable t) { continue; }
                if (line != null) line.append(" | ").append(key).append('=').append(describe(value));
                consume(key, value, action);
            }
        } else if (line != null) {
            line.append(" | ohne Extras");
        }
        // RPM/Speed and HCT 0x41/Mode2 can be very noisy. Every Mode2 frame is still
        // decoded, and the AIDL raw path keeps sampled exact frames; suppress only the
        // redundant generic BROADCAST line so a 180-s capture does not bury rare IDs.
        if (line != null) {
            DiagLog.add(line.toString());
        }
    }

    private static boolean isNoisySyncMode2(String action, Bundle extras) {
        if (!"com.microntek.sync".equals(action) || extras == null) return false;
        try {
            Object x = extras.get("syncdata");
            if (!(x instanceof byte[])) return false;
            byte[] b = (byte[]) x;
            return b.length >= 3 && (b[0] & 0xFF) == 0x41 && (b[2] & 0xFF) == 0x02;
        } catch (Throwable ignored) {
            return false;
        }
    }


    /** Exact actions recovered from the installed android.microntek.canbus v13 APK. */
    private void consumeKnownBroadcast(String action, Bundle extras) {
        if (action == null || extras == null) return;
        try {
            if ("com.microntek.canbus.rpm".equals(action)) {
                Object v = extras.get("rpm");
                Double n = numberFrom(v);
                if (n != null && n >= 0 && n < 10000) {
                    int rpm = (int)Math.round(n);
                    synchronized (VehicleRepository.class) {
                        VehicleRepository.mutable().rpm = rpm;
                        VehicleRepository.mutable().rpmUpdatedAtMs = VehicleRepository.now();
                        VehicleRepository.touch("HCT RPM Broadcast");
                    }
                    if (lastLoggedRpm == Integer.MIN_VALUE || Math.abs(rpm - lastLoggedRpm) >= 100) {
                        lastLoggedRpm = rpm;
                        DiagLog.add("HCT RPM=" + rpm + "  (com.microntek.canbus.rpm)");
                    }
                }
                return;
            }
            if ("com.microntek.canbus.speed".equals(action)) {
                Object v = extras.get("speed");
                Double n = numberFrom(v);
                if (n != null && n >= 0 && n < 400) {
                    // Diagnostic line only: the dashboard uses GPS/OBD speed, not this value.
                    if (Double.isNaN(lastLoggedSpeed) || Math.abs(n - lastLoggedSpeed) >= 1.0) {
                        lastLoggedSpeed = n;
                        DiagLog.add(String.format(Locale.GERMANY, "HCT SPEED=%.1f km/h  (com.microntek.canbus.speed)", n));
                    }
                }
                return;
            }
            if ("com.canbus.temperature".equals(action)) {
                Double n = null;
                if (extras.containsKey("temperatureC")) n = numberFrom(extras.get("temperatureC"));
                if (n == null && extras.containsKey("temperature")) n = numberFrom(extras.get("temperature"));
                if (n != null && n > -80 && n < 100) {
                    synchronized (VehicleRepository.class) {
                        VehicleRepository.mutable().outsideC = n;
                        VehicleRepository.mutable().outsideUpdatedAtMs = VehicleRepository.now();
                        VehicleRepository.touch("HCT Temperatur Broadcast");
                    }
                    if (Double.isNaN(lastLoggedTemperature) || Math.abs(n - lastLoggedTemperature) >= 0.5) {
                        lastLoggedTemperature = n;
                        DiagLog.add(String.format(Locale.GERMANY, "HCT TEMPERATUR=%.1f °C", n));
                    }
                }
                return;
            }
            if ("com.microntek.systemui".equals(action)) {
                String type = extras.getString("type", "");
                if (!type.isEmpty()) {
                    if ("fuel".equalsIgnoreCase(type) && extras.containsKey("fueldata")) {
                        DiagLog.add("SYSTEMUI FUEL Kandidat: fueldata=" + describe(extras.get("fueldata")) + " (Einheit noch nicht bestätigt)");
                    } else if ("door".equalsIgnoreCase(type) && extras.containsKey("doordata")) {
                        DiagLog.add("SYSTEMUI DOOR: doordata=" + describe(extras.get("doordata")));
                    } else {
                        DiagLog.add("SYSTEMUI type=" + type + " (weitere Extras folgen im Broadcast-Log)");
                    }
                }
            }
        } catch (Throwable t) {
            DiagLog.add("Known-Broadcast Decoderfehler: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void consume(String key, Object value, String action) {
        consume(key, value, action, 0);
    }

    private void consume(String key, Object value, String action, int depth) {
        if (value == null) return;
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if ("com.microntek.sync".equals(action) || "syncdata".equalsIgnoreCase(key)) {
                if (serial.pending() || (bytes.length > 0 && (bytes[0] & 0xff) == 0x2e))
                    serial.feed(bytes);
                else hctSync.feed(bytes);
            }
            // Unknown transports remain diagnostic-only. Never apply borrowed
            // Toyota function mappings to this VW/HCT vehicle state.
            return;
        }
        if (value instanceof Bundle) {
            if (depth >= 4) return;
            Bundle b = (Bundle) value;
            int visited = 0;
            for (String k : b.keySet()) {
                if (visited++ >= 32) break;
                try { consume(key + "." + k, b.get(k), action, depth + 1); }
                catch (Throwable ignored) {}
            }
            return;
        }

        // Unknown vendor getters can perform Binder work, so vendor objects are only ever inspected
        // on the bounded worker (fields only). The bounded queue also prevents a broadcast storm
        // from growing memory forever.
        final boolean vendorObject = value instanceof Parcelable
                || value.getClass().getName().startsWith("android.microntek")
                || value.getClass().getName().startsWith("com.microntek");
        // Everything below guesses meaning from key names and is therefore diagnostic-only.
        if (!heuristicSniffer) {
            if (vendorObject) VENDOR_INSPECTOR.execute(() -> inspectVendorObject(key, value));
            return;
        }
        String k = ((action == null ? "" : action) + "." + (key == null ? "" : key)).toLowerCase(Locale.ROOT);
        if ("com.canbus.temperature".equals(action) && key != null && key.toLowerCase(Locale.ROOT).contains("temperature")) {
            Double n = numberFrom(value);
            if (n != null && n > -80 && n < 100) {
                synchronized (VehicleRepository.class) {
                    VehicleRepository.mutable().outsideC = n;
                    VehicleRepository.mutable().outsideUpdatedAtMs = VehicleRepository.now();
                    VehicleRepository.touch("Broadcast Temperatur");
                }
            }
            return;
        }

        Double n = numberFrom(value);
        if (n != null) applyNumeric(k, n);
        Boolean bool = booleanFrom(value);
        if (bool != null) applyBoolean(k, bool);

        if (vendorObject) VENDOR_INSPECTOR.execute(() -> inspectVendorObject(key, value));
    }

    /** Guessing decoder for unknown broadcasts; only reachable in sniffer mode. */
    private void applyNumeric(String k, double n) {
        synchronized (VehicleRepository.class) {
        VehicleState s = VehicleRepository.mutable(); boolean hit = true;
        long signalNow = VehicleRepository.now();
        if (containsAny(k,"rpm","engine_speed","revolution","tach") && n >= 0 && n < 10000) { s.rpm=(int)Math.round(n); s.rpmUpdatedAtMs=signalNow; }
        else if (containsAny(k,"odometer","mileage","odo","kilometer","km_total") && n > 0 && n < 2_000_000) { s.odometerKm=Math.round(n); s.odometerUpdatedAtMs=signalNow; }
        // Check level/percent before litres: "fuel_level" also contains "fuel_l".
        else if (containsAny(k,"fuel_percent","tank_percent","fuellevel","fuel_level","tank_level") && n >= 0 && n <= 100) {
            s.fuelPercent=n;
            s.fuelLiters=VehicleRepository.POLO_6R_TANK_LITERS*n/100.0;
            s.fuelUpdatedAtMs=signalNow;
        }
        else if (containsAny(k,"fuel_liter","tank_liter","fuelvolume","fuel_volume","fuel_l") && n >= 0 && n <= 60) { s.fuelLiters=n; s.fuelUpdatedAtMs=signalNow; }
        else if (containsAny(k,"outside","ambient","out_temp","exterior_temp") && n > -80 && n < 100) { s.outsideC=n; s.outsideUpdatedAtMs=signalNow; }
        else if (containsAny(k,"voltage","battery_voltage","batt_vol") && n >= 5 && n <= 20) { s.voltage=n; s.voltageUpdatedAtMs=signalNow; }
        else hit=false;
        if(hit) VehicleRepository.touch("Broadcast " + k);
        }
    }

    private void applyBoolean(String k, boolean v) {
        synchronized (VehicleRepository.class) {
        VehicleState s=VehicleRepository.mutable(); boolean hit=true;
        long signalNow=VehicleRepository.now();
        if (containsAny(k,"front_left","door_fl","left_front","fl_door","doorlf","lfdoor")){s.doorFL=v;s.doorsKnown=true;s.doorsUpdatedAtMs=signalNow;}
        else if(containsAny(k,"front_right","door_fr","right_front","fr_door","doorrf","rfdoor")){s.doorFR=v;s.doorsKnown=true;s.doorsUpdatedAtMs=signalNow;}
        else if(containsAny(k,"rear_left","back_left","door_rl","left_rear","rl_door","doorlr","lrdoor")){s.doorRL=v;s.doorsKnown=true;s.doorsUpdatedAtMs=signalNow;}
        else if(containsAny(k,"rear_right","back_right","door_rr","right_rear","rr_door","doorrR","rrdoor")){s.doorRR=v;s.doorsKnown=true;s.doorsUpdatedAtMs=signalNow;}
        else if(containsAny(k,"trunk","boot","tailgate","back_door")){s.trunk=v;s.trunkKnown=true;s.doorsUpdatedAtMs=signalNow;}
        else if(containsAny(k,"seatbelt","seat_belt","belt")){s.seatbeltOpen=v;s.seatbeltKnown=true;s.seatbeltUpdatedAtMs=signalNow;}
        else if(containsAny(k,"washer")){s.washerLow=v;s.washerKnown=true;s.washerUpdatedAtMs=signalNow;}
        else hit=false;
        if(hit) VehicleRepository.touch("Broadcast " + k);
        }
    }

    private void inspectVendorObject(String key, Object obj) {
        if (obj == null) return;
        StringBuilder out = new StringBuilder("VENDOR-OBJEKT ").append(key).append(" : ").append(obj.getClass().getName());
        int found = 0;
        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                if (found >= 30) break;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (isSimple(v)) {
                        out.append(" | ").append(f.getName()).append('=').append(describe(v));
                        consume(f.getName(), v, obj.getClass().getName());
                        found++;
                    }
                } catch (Throwable ignored) {}
            }
            // Inspect data fields only. Even a getter-looking method can mutate a
            // vendor service; return type and method name cannot prove it read-only.
        } catch (Throwable t) {
            out.append(" | REFLECTION ").append(t.getClass().getSimpleName());
        }
        DiagLog.add(out.toString());
    }

    private static boolean isSimple(Object v) {
        return v instanceof Boolean || v instanceof Number || v instanceof String || (v != null && v.getClass().isPrimitive());
    }

    private static boolean containsAny(String s,String... words){for(String w:words)if(s.contains(w.toLowerCase(Locale.ROOT)))return true;return false;}
    private static Double numberFrom(Object o){
        if(o instanceof Number)return ((Number)o).doubleValue();
        if(o instanceof String){String raw=(String)o;if(raw.length()>64)return null;String x=raw.replace(',','.').replaceAll("[^0-9+\\-.]","");if(!x.isEmpty())try{return Double.parseDouble(x);}catch(Exception ignored){}}
        return null;
    }
    private static Boolean booleanFrom(Object o) {
        if (o instanceof Boolean) return (Boolean)o;
        if (o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
            long n=((Number)o).longValue(); if(n==0)return false; if(n==1)return true;
        }
        if (o instanceof String) {
            String s=((String)o).trim().toLowerCase(Locale.ROOT);
            if(s.equals("true")||s.equals("open")||s.equals("opened")||s.equals("on"))return true;
            if(s.equals("false")||s.equals("close")||s.equals("closed")||s.equals("off"))return false;
        }
        return null;
    }
    private static String describe(Object v){
        if(v==null)return "null";
        if(v instanceof byte[])return "["+Hex.of((byte[])v)+"]";
        if(v instanceof Bundle)return "Bundle("+((Bundle)v).keySet().size()+" Einträge)";
        if(v instanceof CharSequence){String s=v.toString();return s.length()<=160?s:s.substring(0,160)+"…";}
        if(v instanceof Number||v instanceof Boolean||v instanceof Character)return String.valueOf(v);
        if(v.getClass().isArray()){
            int n=Array.getLength(v);StringBuilder b=new StringBuilder("[");
            for(int i=0;i<Math.min(n,32);i++){if(i>0)b.append(',');Object e=Array.get(v,i);b.append(e instanceof Number||e instanceof Boolean||e instanceof Character?String.valueOf(e):e==null?"null":"<"+e.getClass().getSimpleName()+">");}
            if(n>32)b.append(",...");return b.append(']').toString();
        }
        // Vendor Parcelable.toString() is not trusted: it may perform Binder work or
        // create a huge dump. Reflection of relevant scalar fields runs on the bounded worker.
        return "<"+v.getClass().getName()+">";
    }
    private static boolean isPlausibleAction(String a){
        if(a==null||a.length()<5||a.length()>180||a.indexOf('.')<1)return false;
        for(int i=0;i<a.length();i++){char c=a.charAt(i);if(!(Character.isLetterOrDigit(c)||c=='.'||c=='_'||c=='-' ))return false;}
        return true;
    }
}
