package de.ronny.pololauncher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Talks to a Bluetooth ELM327 (clone) adapter and estimates fuel consumption, written into
 * {@link VehicleState#instConsumption}/{@link VehicleState#avgConsumption} exactly
 * like the CAN receivers write their own signals.
 *
 * v0.3.0: this fork's vehicle (VW Polo V 1.2, engine code CGPB, HSN/TSN 0603/APM) was confirmed
 * via Torque's adapter diagnostics to support neither PID 0x10 (MAF) nor PID 0x2F (fuel level)
 * at all - the engine has no mass air flow sensor and uses intake manifold pressure (MAP)
 * instead (speed-density load calculation). Consumption is therefore estimated from PID 0x0B
 * (MAP) + 0x0C (RPM) + 0x0F (intake air temperature) via {@link Obd2PidDecoder#mafEquivalentFromMap}
 * instead of a real MAF reading - see that method's Javadoc for the formula and its VOLUMETRIC_EFFICIENCY
 * assumption. The tank/fuel-level value goes back to its original CAN source (HctSyncDecoder):
 * OBD2 cannot supply it on this vehicle, so there is no risk of the two racing each other.
 * Obd2Client is therefore this fork's writer for consumption only, not for fuel/tank.
 *
 * The DISPLACEMENT_LITERS/VOLUMETRIC_EFFICIENCY constants below are specific to this one
 * vehicle and engine; porting this fork to a different Polo/engine would need both revisited.
 *
 * Diagnostic logging retained from v0.2.0: every init command and PID query logs its raw
 * adapter reply to DiagLog, and a one-time "0100"/"0120" support-bitmap query after connecting
 * confirms what the ECU itself claims to support. No reconnect is forced just because a PID
 * comes back "NO DATA" - that is a valid, meaningful reply, not a lost link.
 *
 * Cheap ELM327 clones are also known to drop Bluetooth connections under continuous polling.
 * This client therefore polls at a moderate ~1 s cadence and, on a real I/O error (timeout,
 * closed stream), closes the socket and waits before reconnecting rather than hammering a
 * dead link.
 */
final class Obd2Client {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREFS = "obd2_prefs";
    private static final String KEY_ADDRESS = "device_address";
    private static final long RECONNECT_DELAY_MS = 2_000L;
    /** Failed Bluetooth connects back off exponentially up to this delay (radio time is shared with A2DP/HFP). */
    private static final long MAX_RECONNECT_DELAY_MS = 30_000L;
    private static final long LOG_REPEAT_INTERVAL_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 1_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final long CMD_TIMEOUT_MS = 1_500L;
    private static final long RESET_TIMEOUT_MS = 3_000L;
    /** A normal ELM reply is tiny; cap a clone that streams garbage without a prompt. */
    private static final int MAX_REPLY_CHARS = 16_384;
    /** Below this speed a l/100km figure is division noise, not a value; show l/h instead. */
    private static final double MIN_SPEED_FOR_100KM = 3.0;
    /** VW Polo V (6R) 1.2, engine code CGPB - HSN/TSN 0603/APM. Vehicle-specific. */
    private static final double DISPLACEMENT_LITERS = 1.198;
    /**
     * Assumed, not measured: a simple 3-cylinder NA engine's real volumetric efficiency
     * varies with RPM and load; this fixed estimate is the main source of error in the
     * MAP-based consumption figure, since there is no MAF measurement to calibrate against.
     */
    private static final double VOLUMETRIC_EFFICIENCY = 0.78;
    private static final String KEY_DFCO = "dfco_estimation";
    private static final String KEY_VE_SCALE = "ve_scale_bits";
    private static final String KEY_AVG_WEIGHTED_LITERS = "avg_weighted_liters_bits";
    private static final String KEY_AVG_WEIGHTED_KM = "avg_weighted_km_bits";
    /** How often the running average is written back to disk while driving. */
    private static final long AVERAGE_SAVE_INTERVAL_MS = 10_000L;

    private volatile boolean running;
    private volatile Thread thread;
    private volatile BluetoothSocket socket;
    private volatile String connectedAddress;
    private volatile boolean latestPollUsable;
    private volatile String lastPidSummary = "Noch keine PID-Antworten empfangen";
    private volatile long lastReplyAtMs;
    private volatile long lastUsableAtMs;
    private volatile long completedPolls;
    private volatile InputStream in;
    private volatile OutputStream out;
    /** Learned correction of {@link #VOLUMETRIC_EFFICIENCY}, see {@link Obd2Calibration}. */
    private volatile double veScale = 1d;
    private volatile long roundsTotal;
    private volatile long roundsFuelCut;
    private long reconnectDelayMs = RECONNECT_DELAY_MS;
    private boolean lastAttemptUsedRadio;
    private String lastAttemptAddress;
    private String lastLogKey = "";
    private long lastLogAt;
    private int suppressedLogs;
    private volatile boolean connected;
    private volatile String status = "Angehalten";
    private final Obd2ConsumptionAverager averager = new Obd2ConsumptionAverager();
    private long lastSampleAtMs;
    private long roundsSinceLastLog;
    private long lastHeartbeatAtMs;
    private long averageLastSavedAtMs;
    private Context averageStoreContext;

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String deviceAddress(Context context) {
        return preferences(context).getString(KEY_ADDRESS, null);
    }

    static void setDeviceAddress(Context context, String address) {
        preferences(context).edit().putString(KEY_ADDRESS, address).apply();
    }

    /** Deceleration fuel-cut estimation, see {@link Obd2PidDecoder#fuelCutSuspected}. On by default. */
    static boolean dfcoEnabled(Context context) {
        return preferences(context).getBoolean(KEY_DFCO, true);
    }

    static void setDfcoEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_DFCO, enabled).apply();
    }

    private static double savedVeScale(Context context) {
        double value = Double.longBitsToDouble(preferences(context).getLong(KEY_VE_SCALE, Double.doubleToLongBits(1d)));
        return Double.isFinite(value) ? Obd2Calibration.clamp(value) : 1d;
    }

    /** Current efficiency correction (1.0 = uncalibrated), also while the client is stopped. */
    synchronized double veScale(Context context) {
        return running ? veScale : savedVeScale(context);
    }

    /** Feeds one trusted window ratio (true fuel / modelled fuel) into the persisted correction. */
    synchronized void calibrate(Context context, double windowRatio) {
        double previous = running ? veScale : savedVeScale(context);
        double next = Obd2Calibration.nextScale(previous, windowRatio);
        veScale = next;
        preferences(context).edit().putLong(KEY_VE_SCALE, Double.doubleToLongBits(next)).apply();
        DiagLog.add(String.format(java.util.Locale.GERMANY,
                "OBD2-Kalibrierung: Fenster-Verhältnis %.3f -> Korrekturfaktor %.3f -> %.3f",
                windowRatio, previous, next));
    }

    synchronized void resetCalibration(Context context) {
        veScale = 1d;
        preferences(context).edit().remove(KEY_VE_SCALE).apply();
        DiagLog.add("OBD2: Kalibrierung zurückgesetzt (Faktor 1,000)");
    }

    /** {litres, km} accumulated by the OBD average; running values or the persisted baseline. */
    synchronized double[] totals(Context context) {
        Obd2ConsumptionAverager totals = averageTotals(context);
        return new double[]{totals.weightedLiters(), totals.weightedKm()};
    }

    /** Clears the running average, live and persisted, so a fresh figure starts accumulating. */
    synchronized void resetAverageConsumption(Context context) {
        synchronized (averager) {
        averager.reset();
        preferences(context).edit()
                .remove(KEY_AVG_WEIGHTED_LITERS).remove(KEY_AVG_WEIGHTED_KM).apply();
        synchronized (VehicleRepository.class) {
            VehicleRepository.mutable().avgConsumption = Double.NaN;
        }
        }
        DiagLog.add("OBD2: Ø-Verbrauch zurückgesetzt");
    }

    private void loadAverage(Context context) {
        SharedPreferences p = preferences(context);
        double liters = Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_LITERS, Double.doubleToLongBits(0d)));
        double km = Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_KM, Double.doubleToLongBits(0d)));
        averager.restore(liters, km);
    }

    /** Restored cumulative average: never publish it as a fresh OBD sample. */
    synchronized double displayAverage(Context context) {
        if (running) return averager.value();
        SharedPreferences p = preferences(context);
        Obd2ConsumptionAverager saved = new Obd2ConsumptionAverager();
        saved.restore(
                Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_LITERS, Double.doubleToLongBits(0d))),
                Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_KM, Double.doubleToLongBits(0d))));
        return saved.value();
    }

    private synchronized Obd2ConsumptionAverager averageTotals(Context context) {
        Obd2ConsumptionAverager totals = new Obd2ConsumptionAverager();
        if (running) {
            synchronized (averager) { totals.restore(averager.weightedLiters(), averager.weightedKm()); }
        } else {
            SharedPreferences p = preferences(context);
            totals.restore(Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_LITERS, Double.doubleToLongBits(0d))),
                    Double.longBitsToDouble(p.getLong(KEY_AVG_WEIGHTED_KM, Double.doubleToLongBits(0d))));
        }
        return totals;
    }

    synchronized double reliableDisplayAverage(Context context) {
        return averageTotals(context).reliableValue();
    }

    private void saveAverage() {
        if (averageStoreContext == null) return;
        synchronized (averager) {
        preferences(averageStoreContext).edit()
                .putLong(KEY_AVG_WEIGHTED_LITERS, Double.doubleToLongBits(averager.weightedLiters()))
                .putLong(KEY_AVG_WEIGHTED_KM, Double.doubleToLongBits(averager.weightedKm()))
                .apply();
        }
    }

    synchronized void start(Context context) {
        if (running) return;
        running = true;
        Context app = context.getApplicationContext();
        averageStoreContext = app;
        // v0.5.0: restore the running average instead of resetting it, so the figure keeps
        // accumulating across ignition cycles / app restarts - resetting it now would throw
        // away everything learned before this restart. Use Einstellungen -> Verbrauch/OBD2
        // to reset intentionally instead (Obd2Client.resetAverageConsumption).
        loadAverage(app);
        veScale = savedVeScale(app);
        reconnectDelayMs = RECONNECT_DELAY_MS;
        averageLastSavedAtMs = 0L;
        lastSampleAtMs = 0L;
        lastHeartbeatAtMs = 0L;
        thread = new Thread(() -> runLoop(app), "obd2-client");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, error) ->
                DiagLog.add("OBD2: Thread beendet durch " + error.getClass().getSimpleName() + ": " + error.getMessage()));
        thread.start();
        DiagLog.add("OBD2: Hintergrund-Thread gestartet");
    }

    synchronized void stop() {
        running = false;
        saveAverage();
        if (thread != null) thread.interrupt();
        thread = null;
        closeQuietly();
        connected = false;
        status = "Angehalten";
    }

    boolean isConnected() { return connected; }
    String status() { return status; }

    String diagnostics(Context context) {
        Obd2ConsumptionAverager totals = averageTotals(context);
        long now = System.currentTimeMillis();
        long age = lastUsableAtMs == 0 ? -1 : now - lastUsableAtMs;
        return "OBD2-ADAPTER / ELM327\n"
                + "Ausgewählter Adapter: " + java.util.Objects.toString(deviceAddress(context), "keiner") + "\n"
                + "Aktive Verbindung: " + (connected ? connectedAddress : "keine") + "\n"
                + "Bluetooth-Verbindung: " + (connected ? "VERBUNDEN" : "NICHT VERBUNDEN") + "\n"
                + "Poll-Thread: " + (running && thread != null && thread.isAlive() ? "AKTIV" : "NICHT AKTIV") + "\n"
                + "Status: " + status + "\n"
                + "Abgeschlossene Abfragerunden: " + completedPolls + "\n"
                + "Letzte Adapterantwort: " + (lastReplyAtMs == 0 ? "nie" : new java.util.Date(lastReplyAtMs)) + "\n"
                + "Brauchbare Verbrauchsdaten: " + (connected && latestPollUsable && age >= 0 && age <= 15_000 ? "JA" : "NEIN / VERALTET") + "\n"
                + "Letzte brauchbare Probe: " + (age < 0 ? "nie" : age / 1000 + " Sekunden her") + "\n"
                + "Letzte PID-Werte: " + lastPidSummary + "\n"
                + String.format(java.util.Locale.GERMANY, "OBD gesammelte Strecke: %.3f km\nOBD gesammelter Kraftstoff: %.4f L\n",
                        totals.weightedKm(), totals.weightedLiters())
                + "OBD Durchschnitts-Startphase: " + (totals.weightedKm() >= Obd2ConsumptionAverager.DISPLAY_MIN_KM
                        ? "abgeschlossen" : "unter 1 km / Anzeige wartet") + "\n"
                + String.format(java.util.Locale.GERMANY,
                        "OBD Schubabschaltung geschätzt: %s (%d von %d Runden)\nOBD Modellkorrektur (aus CAN-Tank gelernt): Faktor %.3f\n",
                        dfcoEnabled(context) ? "AN" : "AUS", roundsFuelCut, roundsTotal, veScale(context))
                + "OBD Summenquelle: " + (running ? "laufend, inkl. gespeicherter Grundlage" : "gespeicherte Grundlage") + "\n";
    }

    /**
     * v0.7.0: a real session (18.09.2026, ~12:27-12:38) showed this thread go completely
     * silent for 10+ minutes after its very first successful connect - no further poll logs,
     * but also no "Verbindung verloren" message, which only IOException produces. That gap is
     * the signature of an uncaught non-IOException killing this background thread outright:
     * previously only IOException was caught here, so any other exception (a bug, an adapter
     * reply we didn't anticipate) terminated the thread with zero log trace. Catching
     * RuntimeException too, and logging a periodic heartbeat, makes that failure mode visible
     * and recoverable instead of silent.
     */
    private void runLoop(Context context) {
        while (running && thread == Thread.currentThread()) {
            try {
                if (connected && !java.util.Objects.equals(connectedAddress, deviceAddress(context))) {
                    closeQuietly();
                    connected = false;
                }
                if (!connected) {
                    if (!tryConnect(context)) {
                        // Real Bluetooth attempts back off (2, 4, 8 ... 30 s); a missing adapter
                        // selection or switched-off Bluetooth only costs a cheap check.
                        sleepWhileUnchanged(lastAttemptUsedRadio ? nextReconnectDelay() : RECONNECT_DELAY_MS, context);
                        continue;
                    }
                    reconnectDelayMs = RECONNECT_DELAY_MS;
                }
                pollOnce(context);
                heartbeatIfDue();
                sleep(POLL_INTERVAL_MS);
            } catch (IOException lost) {
                if (!running || thread != Thread.currentThread()) return;
                logRepeated("lost:" + lost.getClass().getSimpleName(), "OBD2: Verbindung verloren ("
                        + lost.getClass().getSimpleName() + ": " + lost.getMessage() + "), verbinde neu");
                closeQuietly();
                connected = false;
                status = "Verbindung verloren, verbinde neu ...";
                sleepWhileUnchanged(nextReconnectDelay(), context);
            } catch (Throwable unexpected) {
                // Includes Errors: a silently dead poll thread is exactly the failure v0.7.0 found.
                if (!running || thread != Thread.currentThread()) return;
                logRepeated("unexpected:" + unexpected.getClass().getSimpleName(),
                        "OBD2: unerwarteter Fehler im Poll-Loop (" + unexpected.getClass().getSimpleName()
                        + ": " + unexpected.getMessage() + "), verbinde neu");
                closeQuietly();
                connected = false;
                status = "Unerwarteter Fehler, verbinde neu ...";
                sleepWhileUnchanged(nextReconnectDelay(), context);
            }
        }
    }

    private long nextReconnectDelay() {
        long delay = reconnectDelayMs;
        reconnectDelayMs = Math.min(MAX_RECONNECT_DELAY_MS, reconnectDelayMs * 2);
        return delay;
    }

    /** Sleeps in short slices so stop() and a newly selected adapter take effect promptly. */
    private void sleepWhileUnchanged(long ms, Context context) {
        String address = deviceAddress(context);
        long end = SystemClock.elapsedRealtime() + ms;
        while (running && thread == Thread.currentThread()) {
            long left = end - SystemClock.elapsedRealtime();
            if (left <= 0) return;
            if (!java.util.Objects.equals(address, deviceAddress(context))) {
                reconnectDelayMs = RECONNECT_DELAY_MS;
                return;
            }
            sleep(Math.min(left, 500L));
        }
    }

    /** Identical messages repeat at most once a minute (with a counter) instead of flooding DiagLog. */
    private void logRepeated(String key, String text) {
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastLogKey) && now - lastLogAt < LOG_REPEAT_INTERVAL_MS) {
            suppressedLogs++;
            return;
        }
        String suffix = suppressedLogs > 0 && key.equals(lastLogKey)
                ? " (+" + suppressedLogs + " gleiche Meldungen unterdrückt)" : "";
        suppressedLogs = 0;
        lastLogKey = key;
        lastLogAt = now;
        DiagLog.add(text + suffix);
    }

    private boolean tryConnect(Context context) {
        lastAttemptUsedRadio = false;
        String address = deviceAddress(context);
        if (!java.util.Objects.equals(address, lastAttemptAddress)) {
            lastAttemptAddress = address;
            reconnectDelayMs = RECONNECT_DELAY_MS;
        }
        if (address == null || address.isEmpty()) {
            status = "Kein OBD2-Adapter ausgewählt (Einstellungen -> Verbrauch/OBD2)";
            return false;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status = "Bluetooth ist aus oder nicht verfügbar";
            return false;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            try { adapter.cancelDiscovery(); } catch (Throwable ignored) {}
            BluetoothSocket s = null;
            lastAttemptUsedRadio = true;
            try {
                s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket = s; // Allow stop() to cancel a blocking connect.
                s.connect();
            } catch (IOException direct) {
                // Some ELM327 clones fail the standard SDP-based socket but accept
                // the well-known reflective channel-1 fallback used by most OBD apps.
                closeSilently(s);
                if (!running || thread != Thread.currentThread()) return false;
                s = fallbackSocket(device);
                socket = s;
                s.connect();
            }
            if (!running || thread != Thread.currentThread()) { closeSilently(s); return false; }
            socket = s;
            in = socket.getInputStream();
            out = socket.getOutputStream();
            initAdapter();
            connected = true;
            connectedAddress = address;
            lastSampleAtMs = 0L; // Never integrate the disconnected interval.
            status = "Verbunden";
            DiagLog.add("OBD2: verbunden mit " + address);
            probeSupportedPids();
            return true;
        } catch (SecurityException noPermission) {
            status = "Bluetooth-Berechtigung fehlt";
            return false;
        } catch (Exception failed) {
            if (!running || thread != Thread.currentThread()) return false;
            status = "Verbindung fehlgeschlagen: " + failed.getClass().getSimpleName();
            logRepeated("connect:" + failed.getClass().getSimpleName(),
                    "OBD2: Verbindungsversuch fehlgeschlagen: " + failed.getClass().getSimpleName()
                    + " " + failed.getMessage());
            closeQuietly();
            return false;
        }
    }

    private BluetoothSocket fallbackSocket(BluetoothDevice device) throws Exception {
        Method m = device.getClass().getMethod("createRfcommSocket", int.class);
        return (BluetoothSocket) m.invoke(device, 1);
    }

    private void initAdapter() throws IOException {
        sendAndLog("ATZ", RESET_TIMEOUT_MS);
        sendAndLog("ATE0", CMD_TIMEOUT_MS);
        sendAndLog("ATL0", CMD_TIMEOUT_MS);
        // v0.2.0: auto-detect the protocol instead of forcing ATSP6. A forced protocol the
        // vehicle does not speak would make every single PID fail at once - exactly what we
        // saw - so this diagnostic build lets the adapter negotiate it itself.
        sendAndLog("ATSP0", CMD_TIMEOUT_MS);
    }

    /** One-time per connection: asks the ECU itself which PIDs it supports (0x01-0x20 and 0x21-0x40). */
    private void probeSupportedPids() {
        try {
            String raw1 = sendAndLog("0100", CMD_TIMEOUT_MS);
            int[] data1 = Obd2PidDecoder.dataBytes(raw1, "00");
            if (data1 == null) {
                DiagLog.add("OBD2 Support-Check 0100: keine auswertbare Antwort ("
                        + Obd2PidDecoder.cleanLine(raw1) + ") - ECU antwortet ggf. auf anderes Protokoll/Header");
            }
            String raw2 = sendAndLog("0120", CMD_TIMEOUT_MS);
            int[] data2 = Obd2PidDecoder.dataBytes(raw2, "20");

            DiagLog.add(String.format(java.util.Locale.GERMANY,
                    "OBD2 ECU meldet unterstützt: RPM(0C)=%s Speed(0D)=%s MAP(0B)=%s IAT(0F)=%s | (bekannt fehlend: MAF(10)=%s Tank(2F)=%s)",
                    describeSupport(data1, 0x0C, 0x01), describeSupport(data1, 0x0D, 0x01),
                    describeSupport(data1, 0x0B, 0x01), describeSupport(data1, 0x0F, 0x01),
                    describeSupport(data1, 0x10, 0x01), describeSupport(data2, 0x2F, 0x21)));
        } catch (IOException e) {
            DiagLog.add("OBD2 Support-Check fehlgeschlagen: " + e.getMessage());
        }
    }

    private static String describeSupport(int[] bitmapData, int pid, int firstPidInBitmap) {
        Boolean supported = Obd2PidDecoder.pidSupportedFromBitmap(bitmapData, pid, firstPidInBitmap);
        return supported == null ? "unbekannt (keine Antwort)" : String.valueOf(supported);
    }

    private void pollOnce(Context context) throws IOException {
        String rpmRaw = sendAndRead("010C", CMD_TIMEOUT_MS);
        String speedRaw = sendAndRead("010D", CMD_TIMEOUT_MS);
        String mapRaw = sendAndRead("010B", CMD_TIMEOUT_MS);
        String iatRaw = sendAndRead("010F", CMD_TIMEOUT_MS);

        // Log raw replies periodically (every round for the first minute, then every 10th)
        // so a live drive doesn't get buried, but the first, most important rounds are complete.
        boolean logThisRound = roundsSinceLastLog % 10 == 0;
        roundsSinceLastLog++;
        if (logThisRound) {
            DiagLog.add("OBD2 010C(RPM)=" + Obd2PidDecoder.cleanLine(rpmRaw)
                    + " | 010D(Speed)=" + Obd2PidDecoder.cleanLine(speedRaw)
                    + " | 010B(MAP)=" + Obd2PidDecoder.cleanLine(mapRaw)
                    + " | 010F(IAT)=" + Obd2PidDecoder.cleanLine(iatRaw));
        }

        int[] rpmData = Obd2PidDecoder.dataBytes(rpmRaw, "0C");
        int[] speedData = Obd2PidDecoder.dataBytes(speedRaw, "0D");
        int[] mapData = Obd2PidDecoder.dataBytes(mapRaw, "0B");
        int[] iatData = Obd2PidDecoder.dataBytes(iatRaw, "0F");

        // Replies that answer an EARLIER command (stale bytes after a timeout) shift the whole
        // stream by one command and would never recover without an IOException. Resynchronise.
        int shifted = mismatch(rpmRaw, "0C") + mismatch(speedRaw, "0D")
                + mismatch(mapRaw, "0B") + mismatch(iatRaw, "0F");
        if (shifted >= 2) {
            logRepeated("resync", "OBD2: Antworten gegenüber Abfragen verschoben - Eingangspuffer wird neu synchronisiert");
            latestPollUsable = false;
            lastSampleAtMs = 0L;
            resyncInput();
            return;
        }

        int rpm = Obd2PidDecoder.rpm(rpmData);
        double speedKmh = Obd2PidDecoder.speedKmh(speedData);
        double mapKpa = Obd2PidDecoder.mapKpa(mapData);
        double iatC = Obd2PidDecoder.intakeAirTempC(iatData);
        double mafEquivalent = rpm >= 0
                ? Obd2PidDecoder.mafEquivalentFromMap(mapKpa, rpm, DISPLACEMENT_LITERS,
                        VOLUMETRIC_EFFICIENCY * veScale, iatC)
                : Double.NaN;
        double lph = Obd2PidDecoder.literPerHourFromMaf(mafEquivalent);
        // Overrun: the model sees air, but the injectors are (very probably) off.
        boolean fuelCut = Double.isFinite(lph) && dfcoEnabled(context)
                && Obd2PidDecoder.fuelCutSuspected(mapKpa, rpm, speedKmh);
        if (fuelCut) lph = 0d;
        roundsTotal++;
        if (fuelCut) roundsFuelCut++;
        double per100km = Obd2PidDecoder.literPer100kmFromLph(lph, speedKmh, MIN_SPEED_FOR_100KM);
        lastPidSummary = String.format(java.util.Locale.GERMANY,
                "RPM=%s | Speed=%s km/h | MAP=%s kPa | IAT=%s °C | Kraftstoff=%s l/h%s",
                rpm < 0 ? "--" : String.valueOf(rpm), diagnosticNumber(speedKmh),
                diagnosticNumber(mapKpa), diagnosticNumber(iatC), diagnosticNumber(lph),
                fuelCut ? " (Schubabschaltung geschätzt)" : "");

        long sampleAt = SystemClock.elapsedRealtime();
        double elapsedSeconds = lastSampleAtMs == 0 ? 0 : (sampleAt - lastSampleAtMs) / 1000.0;
        lastSampleAtMs = sampleAt;
        if (!running || thread != Thread.currentThread()) return;
        lastReplyAtMs = System.currentTimeMillis();
        completedPolls++;
        latestPollUsable = Double.isFinite(lph) && Double.isFinite(speedKmh);
        if (!latestPollUsable) lastSampleAtMs = 0;
        if (latestPollUsable) lastUsableAtMs = lastReplyAtMs;
        synchronized (averager) {
        if (Double.isFinite(lph)) averager.add(lph, speedKmh, elapsedSeconds);
        // Saved periodically, not just on a clean stop(): an abrupt process kill (forced
        // stop, OEM battery saver) never reaches stop(), and losing more than this interval
        // of accumulated average on every such kill would defeat the point of persisting it.
        if (sampleAt - averageLastSavedAtMs >= AVERAGE_SAVE_INTERVAL_MS) {
            saveAverage();
            averageLastSavedAtMs = sampleAt;
        }

        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            // Written every successful round, even as NaN while stationary: NaN correctly
            // means "not a meaningful l/100km right now", not "unknown/stale". Tank/fuel
            // stays untouched here - this vehicle's ECU does not support PID 0x2F, so the
            // CAN-sourced value from HctSyncDecoder remains the only tank source.
            s.instConsumption = per100km;
            s.obdFuelLitersPerHour = lph;
            s.obdSpeedKmh = speedKmh;
            s.obdRpm = rpm;
            s.avgConsumption = Double.isFinite(lph) && Double.isFinite(speedKmh) ? averager.value() : Double.NaN;
            s.instConsumptionUpdatedAtMs = VehicleRepository.now();
            VehicleRepository.touch("OBD2");
        }
        }
        // No forced reconnect just because MAP/IAT come back "NO DATA" or out of range:
        // that is a valid adapter/ECU answer, not a lost link (see class Javadoc). A real
        // link problem still surfaces as an IOException from sendAndRead() itself.
    }

    private void heartbeatIfDue() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastHeartbeatAtMs >= HEARTBEAT_INTERVAL_MS) {
            DiagLog.add("OBD2: Thread aktiv (Herzschlag), Status=" + status);
            lastHeartbeatAtMs = now;
        }
    }

    private static String diagnosticNumber(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.GERMANY, "%.1f", value) : "--";
    }

    /** Sends a command, logs its raw reply unconditionally, and returns it. For init/probe steps. */
    private String sendAndLog(String command, long timeoutMs) throws IOException {
        String raw = sendAndRead(command, timeoutMs);
        DiagLog.add("OBD2 " + command + " -> " + Obd2PidDecoder.cleanLine(raw));
        return raw;
    }

    private String sendAndRead(String command, long timeoutMs) throws IOException {
        if (!running || thread != Thread.currentThread()) throw new IOException("OBD2-Thread angehalten");
        // stop()/closeQuietly() null these fields from another thread: work on local copies.
        final InputStream input = in;
        final OutputStream output = out;
        if (output == null || input == null) throw new IOException("Kein Socket");
        // Anything still buffered belongs to an earlier command (late reply after a timeout).
        drain(input);
        output.write((command + "\r").getBytes("US-ASCII"));
        output.flush();
        return readUntilPrompt(input, timeoutMs);
    }

    private static void drain(InputStream input) throws IOException {
        byte[] junk = new byte[128];
        int budget = 4_096;
        while (budget > 0) {
            int available = input.available();
            if (available <= 0) return;
            int read = input.read(junk, 0, Math.min(available, junk.length));
            if (read <= 0) return;
            budget -= read;
        }
    }

    private static int mismatch(String raw, String expectedPid) {
        String echoed = Obd2PidDecoder.echoedPid(raw);
        return echoed != null && !echoed.equalsIgnoreCase(expectedPid) ? 1 : 0;
    }

    /** Lets the replies still in flight arrive, then discards them all. */
    private void resyncInput() throws IOException {
        sleep(400L);
        InputStream input = in;
        if (input != null) drain(input);
    }

    private String readUntilPrompt(InputStream input, long timeoutMs) throws IOException {
        StringBuilder sb = new StringBuilder();
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        byte[] buf = new byte[128];
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!running || thread != Thread.currentThread()) throw new IOException("OBD2-Thread angehalten");
            int avail = input.available();
            if (avail > 0) {
                int read = input.read(buf, 0, Math.min(avail, buf.length));
                if (read < 0) throw new IOException("Stream geschlossen");
                sb.append(new String(buf, 0, read, java.nio.charset.StandardCharsets.US_ASCII));
                if (sb.length() > MAX_REPLY_CHARS)
                    throw new IOException("Adapterantwort zu groß / Prompt fehlt");
                if (sb.indexOf(">") >= 0) return sb.toString();
            } else {
                try { Thread.sleep(15); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("Unterbrochen"); }
            }
        }
        throw new IOException("Zeitüberschreitung bei Antwort");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void closeQuietly() {
        closeSilently(socket);
        socket = null;
        in = null;
        out = null;
    }

    private void closeSilently(BluetoothSocket s) {
        if (s == null) return;
        try { s.close(); } catch (IOException ignored) {}
    }
}
