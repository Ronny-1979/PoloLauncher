package de.ronny.pololauncher;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stale replies, shifted streams, fuel cut, reconnect back-off and log flooding of the OBD client. */
public final class Obd2ResilienceTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void set(Object target, String name, Object value) throws Exception {
        Field f = Obd2Client.class.getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static Object get(Object target, String name) throws Exception {
        Field f = Obd2Client.class.getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = Obd2Client.class.getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(target, args); } catch (InvocationTargetException e) { throw (Exception) e.getCause(); }
    }

    /** A byte queue like a real serial link: leftovers stay until somebody reads them. */
    static final class Pipe extends InputStream {
        final ArrayDeque<Integer> queue = new ArrayDeque<>();
        int map = 0x32, rpmHigh = 0x2E, rpmLow = 0xE0, speed = 0x64;
        boolean wrongAnswers;
        void inject(String text) { for (byte b : text.getBytes(StandardCharsets.US_ASCII)) queue.add(b & 255); }
        public synchronized int available() { return queue.size(); }
        public synchronized int read() { return queue.isEmpty() ? -1 : queue.poll(); }
        final OutputStream output = new OutputStream() {
            final StringBuilder command = new StringBuilder();
            public void write(int b) {
                if (b != '\r') { command.append((char) b); return; }
                String c = command.toString(); command.setLength(0);
                String answer = wrongAnswers ? "41 00 BE 3E B8 13>" : switch (c) {
                    case "010C" -> String.format("41 0C %02X %02X>", rpmHigh, rpmLow);
                    case "010D" -> String.format("41 0D %02X>", speed);
                    case "010B" -> String.format("41 0B %02X>", map);
                    case "010F" -> "41 0F 41>";
                    default -> "?>";
                };
                inject(answer);
            }
        };
    }

    static Obd2Client running(Context context, Pipe pipe) throws Exception {
        Obd2Client client = new Obd2Client();
        set(client, "running", true); set(client, "thread", Thread.currentThread());
        set(client, "in", pipe); set(client, "out", pipe.output); set(client, "averageStoreContext", context);
        return client;
    }

    static void round(Obd2Client client, Context context, long at) throws Exception {
        SystemClock.now = at;
        call(client, "pollOnce", new Class<?>[]{Context.class}, context);
    }

    public static void main(String[] args) throws Exception {
        SystemClock.now = 1_000_000L;
        Context context = new Context();

        // 1) Late reply of an earlier command sits in the buffer: it must not be read as the answer.
        Pipe stale = new Pipe();
        Obd2Client client = running(context, stale);
        stale.inject("SEARCHING...\r41 00 BE 3E B8 13\r\r>");
        DiagLog.clear();
        round(client, context, 10_000);
        VehicleState live = VehicleRepository.snapshot();
        check(live.obdRpm == 3000 && live.obdSpeedKmh == 100, "Stale bytes drained; the very first round is already aligned: " + live.obdRpm);
        check(!DiagLog.text(20).contains("verschoben"), "No resynchronisation was needed: the drain did the work");

        // 2) A stream that answers every command with an OLD reply is detected and never published.
        VehicleRepository.snapshot();
        Pipe shifted = new Pipe(); shifted.wrongAnswers = true;
        Obd2Client lagging = running(context, shifted);
        long polls = (long) get(lagging, "completedPolls");
        round(lagging, context, 20_000);
        check((long) get(lagging, "completedPolls") == polls, "Shifted round not counted as a completed poll");
        check(!(boolean) get(lagging, "latestPollUsable"), "Shifted round is not usable data");
        check(DiagLog.text(20).contains("verschoben"), "Resynchronisation is logged");
        check(shifted.queue.isEmpty(), "Input buffer discarded after the shift");

        // 3) Overrun: high rpm + deep vacuum + rolling => no fuel counted, and it is visible in diagnostics.
        Context c3 = new Context();
        Pipe overrun = new Pipe(); overrun.map = 20;
        Obd2Client dfco = running(c3, overrun);
        for (int i = 0; i < 5; i++) round(dfco, c3, 30_000 + i * 1_000);
        VehicleState s3 = VehicleRepository.snapshot();
        check(s3.instConsumption == 0d && s3.obdFuelLitersPerHour == 0d, "Overrun counts no fuel");
        check((long) get(dfco, "roundsFuelCut") == 5, "Overrun rounds counted");
        check(dfco.diagnostics(c3).contains("Schubabschaltung geschätzt: AN (5 von 5 Runden)"), "Diagnostics show the classification");
        Obd2Client.setDfcoEnabled(c3, false);
        Pipe off = new Pipe(); off.map = 20;
        Obd2Client dfcoOff = running(c3, off);
        round(dfcoOff, c3, 40_000); round(dfcoOff, c3, 41_000);
        check(VehicleRepository.snapshot().obdFuelLitersPerHour > 0d, "Switched off: the raw model value is used");
        check(Obd2Client.dfcoEnabled(new Context()), "Fuel-cut estimation defaults to on");

        // 4) The learned efficiency factor scales the modelled fuel.
        Context c4 = new Context();
        Pipe cruise = new Pipe();
        Obd2Client plain = running(c4, cruise);
        round(plain, c4, 50_000); round(plain, c4, 51_000);
        double base = VehicleRepository.snapshot().obdFuelLitersPerHour;
        Pipe cruise2 = new Pipe();
        Obd2Client scaled = running(c4, cruise2);
        set(scaled, "veScale", 0.8);
        round(scaled, c4, 60_000); round(scaled, c4, 61_000);
        double reduced = VehicleRepository.snapshot().obdFuelLitersPerHour;
        check(Math.abs(reduced / base - 0.8) < 1e-9, "Fuel proportional to the efficiency factor: " + reduced / base);

        // 5) Reconnect back-off 2, 4, 8, 16, 30, 30 s, reset by a new adapter address.
        Obd2Client backoff = new Obd2Client();
        long[] expected = {2_000, 4_000, 8_000, 16_000, 30_000, 30_000};
        for (long e : expected) check((long) call(backoff, "nextReconnectDelay", new Class<?>[]{}) == e, "Back-off step " + e);

        // 6) Identical failure messages are folded instead of flooding the 1200-line buffer.
        DiagLog.clear();
        Obd2Client noisy = new Obd2Client();
        for (int i = 0; i < 50; i++) {
            SystemClock.now = 100_000 + i * 2_000L; // 100 s in total
            call(noisy, "logRepeated", new Class<?>[]{String.class, String.class}, "connect:IOException", "OBD2: Verbindungsversuch fehlgeschlagen");
        }
        int lines = DiagLog.text(100).split("\n").length;
        check(lines <= 3, "50 identical failures produce at most 3 lines, got " + lines);
        check(DiagLog.text(100).contains("gleiche Meldungen unterdrückt"), "Folded count is reported");

        // 7) An Error inside the poll loop does not kill the thread silently.
        Context c7 = new Context();
        Obd2Client dying = new Obd2Client();
        Obd2Client.setDeviceAddress(c7, "AA:BB:CC:DD:EE:FF");
        android.bluetooth.BluetoothAdapter.adapter = new android.bluetooth.BluetoothAdapter() {
            @Override public android.bluetooth.BluetoothDevice getRemoteDevice(String address) { throw new NoSuchMethodError("simulated Error"); }
        };
        DiagLog.clear();
        dying.start(c7);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!DiagLog.text(50).contains("NoSuchMethodError") && System.nanoTime() < deadline) Thread.sleep(20);
        Thread loop = (Thread) get(dying, "thread");
        check(DiagLog.text(50).contains("NoSuchMethodError") && loop.isAlive(), "Error is logged and the loop keeps running");
        dying.stop();
        android.bluetooth.BluetoothAdapter.adapter = null;
        System.out.println("Obd2ResilienceTest: all cases passed");
    }
}
