package de.ronny.pololauncher;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decoder for the HCT/Microntek broadcast "com.microntek.sync".
 *
 * On this QCM6125/HCTGQ radio the CANBOX service broadcasts byte arrays in the
 * form [function, declaredLength, payload...].  The original serial 0x2E start
 * byte/checksum are not present in the broadcast. Some functions are padded by
 * the HCT service, therefore bytes after 2 + declaredLength are ignored.
 *
 * This class only decodes. It never opens a UART and never transmits itself; the read-only
 * 0x90 poll requests are sent separately by {@link HctOemAutoPoller} through the vendor Binder.
 */
public final class HctSyncDecoder {
    private final Map<Integer, Integer> counts = new LinkedHashMap<>();
    private final Map<Integer, String> lastPayload = new LinkedHashMap<>();
    private long totalPackets;
    private long validPackets;
    private int lastDoorMask = -1;
    private int lastMode1Status = -1;
    private int lastMode3Status = -1;
    private long lastMode2LogAt;
    private double lastMode2Speed = Double.NaN;
    private double lastMode2Voltage = Double.NaN;
    private double lastMode2Outside = Double.NaN;
    private long lastMode2Odo = -1;
    private int lastMode2Tank = -1;

    public synchronized void feed(byte[] raw) {
        if (raw == null || raw.length < 2) return;
        totalPackets++;

        // Serial transports are length/checksum checked and normalized before
        // entering this confirmed VW/HCT decoder. Never treat a start as an ID.
        if ((raw[0] & 0xFF) == 0x2E) return;

        final int function = raw[0] & 0xFF;
        final int declaredLen = raw[1] & 0xFF;
        if (declaredLen > 250 || raw.length < 2 + declaredLen) {
            DiagLog.add(String.format(Locale.US,
                    "HCT-SYNC unplausibel fn=0x%02X len=%d raw=%s",
                    function, declaredLen, Hex.of(raw)));
            return;
        }

        validPackets++;
        counts.put(function, counts.getOrDefault(function, 0) + 1);
        // Log packet changes prominently. 0x41/Mode2 is deliberately throttled: RPM
        // makes almost every packet unique and otherwise pushes rare discovery frames out
        // of the diagnostic ring buffer. The decoder still processes every Mode2 packet.
        boolean noisyMode2 = function == 0x41 && declaredLen >= 1 && (raw[2] & 0xFF) == 2;
        if (!noisyMode2) {
            byte[] data = Arrays.copyOfRange(raw, 2, 2 + declaredLen);
            String payloadHex = Hex.of(data);
            String previous = lastPayload.put(function, payloadHex);
            if (!payloadHex.equals(previous)) {
                int ignored = raw.length - (2 + declaredLen);
                DiagLog.add(String.format(Locale.US,
                        "SYNC fn=0x%02X len=%d data=[%s]%s",
                        function, declaredLen, payloadHex,
                        ignored > 0 ? " (" + ignored + " Padding-Bytes ignoriert)" : ""));
            }
        }

        switch (function) {
            case 0x24:
                decodeVwDoors(raw, 2, declaredLen, "HCT SYNC 0x24");
                break;
            case 0x21:
                // CANBUS type 1: A/C path in the original android.microntek.canbus APK.
                // Do not reuse generic SimpleSoft trip/fuel meanings for this profile.
                break;
            case 0x22:
            case 0x23:
                // CANBUS type 1: the original decoder routes these functions to radar paths.
                // They are intentionally NOT interpreted as fuel/consumption in v0.15.
                break;
            case 0x27:
                // Seen as an init/status reply on this profile. The original type-1 CmdProc
                // does not expose it as trip/fuel telemetry, so keep it raw only.
                break;
            case 0x41:
                decodeCarInfo(raw, 2, declaredLen);
                break;
            case 0x69:
                // Generic app-data path in the original type-1 decoder. Keep raw only until
                // a real Polo frame gives us a defensible field mapping.
                break;
            default:
                // Unknown functions remain visible in DIAG so we can map tank,
                // range, washer fluid etc. from real captures later.
                break;
        }
    }

    /**
     * Mapping learned from Ronny's actual Polo 6R log:
     * 0x02 = front right, 0x08 = rear right, 0x10 = trunk.
     * 0x04 = rear left is now also confirmed by vehicle capture.
     * 0x01 = front left follows the bit layout but cannot be verified while the vehicle's
     * front-left door contact is defective (see {@link VehiclePreferences#doorFlFault}).
     */
    private void decodeVwDoors(byte[] d, int offset, int length, String source) {
        if (length < 2) return;
        int mask = d[offset + 1] & 0x1F;
        applyDoorMask(mask, source);
    }

    private void applyDoorMask(int mask, String source) {
        boolean changed = mask != lastDoorMask;
        lastDoorMask = mask;
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            // Keep decoding bit 0x01 for the day the sensor is repaired; the dashboard hides
            // front-left while the user setting "Kontakt defekt" is on.
            s.doorFL = (mask & 0x01) != 0;
            s.doorFR = (mask & 0x02) != 0; // confirmed by vehicle capture
            s.doorRL = (mask & 0x04) != 0; // confirmed by vehicle capture
            s.doorRR = (mask & 0x08) != 0; // confirmed by vehicle capture
            s.trunk  = (mask & 0x10) != 0; // confirmed by vehicle capture
            s.doorsKnown = true;
            s.trunkKnown = true;
            s.doorsUpdatedAtMs = VehicleRepository.now();
            VehicleRepository.touch(source);
        }
        if (changed) {
            DiagLog.add(String.format(Locale.US,
                    "TÜRMASKE 0x%02X -> VL=%s† VR=%s HL=%s HR=%s Koffer=%s  (†VL-Sensor defekt)",
                    mask,
                    yn((mask & 0x01) != 0), yn((mask & 0x02) != 0),
                    yn((mask & 0x04) != 0), yn((mask & 0x08) != 0),
                    yn((mask & 0x10) != 0)));
        }
    }

    /*
     * CANBUS type 1 warning:
     * Public SimpleSoft function tables use 0x21/0x22/0x23/0x27 for trip/fuel data on
     * other profiles. Ronny's radio reports getCanbusType()==1, and the original
     * android.microntek.canbus Canbus01 decoder assigns those IDs differently.
     * Therefore v0.15 deliberately leaves them raw instead of manufacturing values.
     */

    private void decodeCarInfo(byte[] d, int offset, int length) {
        if (length < 1) return;
        int mode = d[offset] & 0xFF;

        // Mode 1 mapping is confirmed by the user's live Polo logs and cross-checked against the OEM code.
        // Mode 1: status. Lower five bits mirror the door mask. 0x80=seat belt warning,
        // 0x40=washer warning. The user's OEM vehicle screen/live comparison now confirms
        // 0x20 = handbrake engaged for this CANBUS type-1 profile.
        if (mode == 1 && length >= 2) {
            int status = d[offset + 1] & 0xFF;
            int mask = status & 0x1F;
            applyDoorMask(mask, "HCT SYNC 0x41/1");
            synchronized (VehicleRepository.class) {
                VehicleState s = VehicleRepository.mutable();
                s.seatbeltOpen = (status & 0x80) != 0;
                s.seatbeltKnown = true;
                s.seatbeltUpdatedAtMs = VehicleRepository.now();
                s.washerLow = (status & 0x40) != 0;
                s.washerKnown = true;
                s.washerUpdatedAtMs = VehicleRepository.now();
                // 0x20 (handbrake) is only logged below: the dashboard has no handbrake tile.
                VehicleRepository.touch("HCT SYNC 0x41/1 Status");
            }
            if (status != lastMode1Status) {
                lastMode1Status = status;
                DiagLog.add(String.format(Locale.US,
                        "HCT 0x41/1 OEM Status=0x%02X Türen=0x%02X Gurt=%s Scheibenwasser=%s Handbremse=%s",
                        status, mask,
                        (status & 0x80) != 0 ? "OFFEN" : "zu",
                        (status & 0x40) != 0 ? "WARNUNG" : "normal",
                        (status & 0x20) != 0 ? "ANGEZOGEN" : "GELÖST"));
            }
            return;
        }

        // 0x41 / mode 2 is live-confirmed on this Polo and matches the OEM controlinfo decoder.
        // Expected broadcast: [41, 0D, 02, rpmH,rpmL, speedH,speedL, voltH,voltL,
        //             tempH,tempL, odoH,odoM,odoL, tankLiters, ...padding]
        // offset points at the payload's mode byte (02).
        if (mode == 2 && length >= 13) {
            int rpm = be16(d, offset + 1);
            double speed = be16(d, offset + 3) * 0.01;
            double voltage = be16(d, offset + 5) * 0.01;
            int rawTemp = signedBe16(d, offset + 7);
            double outside = rawTemp * 0.1;
            long odometer = be24(d, offset + 9);
            int tankLiters = d[offset + 12] & 0xFF;

            synchronized (VehicleRepository.class) {
                VehicleState s = VehicleRepository.mutable();
                boolean hit = false;
                long signalNow = VehicleRepository.now();
                if (rpm >= 0 && rpm < 9000) { s.rpm = rpm; s.rpmUpdatedAtMs = signalNow; hit = true; }
                if (voltage >= 5 && voltage < 20) { s.voltage = voltage; s.voltageUpdatedAtMs = signalNow; hit = true; }
                if (outside >= -60 && outside <= 90) { s.outsideC = outside; s.outsideUpdatedAtMs = signalNow; hit = true; }
                if (odometer > 0 && odometer < 2_000_000) { s.odometerKm = odometer; s.odometerUpdatedAtMs = signalNow; hit = true; }
                // On this type-1 CANBOX the same valid Mode2 frame can temporarily return
                // tank=0 when the value is unavailable (seen around ignition/state changes).
                // Do not replace a previously valid tank reading with that synthetic zero.
                // A real empty tank is not distinguishable from "unavailable" here, so zero
                // remains unknown until we find a separate validity flag.
                if (tankLiters > 0 && tankLiters <= 60) {
                    s.fuelLiters = tankLiters;
                    s.fuelUpdatedAtMs = signalNow;
                    // The dashboard bar is derived from the Polo 6R nominal 45 L tank.
                    s.fuelPercent = Math.max(0.0, Math.min(100.0,
                            tankLiters / VehicleRepository.POLO_6R_TANK_LITERS * 100.0));
                    hit = true;
                }
                if (hit) VehicleRepository.touch("HCT SYNC 0x41/2");
            }
            long now = System.nanoTime() / 1_000_000L;
            boolean importantChange = lastMode2Odo != odometer || lastMode2Tank != tankLiters
                    || Double.isNaN(lastMode2Outside) || Math.abs(lastMode2Outside - outside) >= 0.5
                    || Double.isNaN(lastMode2Speed) || Math.abs(lastMode2Speed - speed) >= 2.0
                    || Double.isNaN(lastMode2Voltage) || Math.abs(lastMode2Voltage - voltage) >= 0.5;
            if (importantChange || now - lastMode2LogAt >= 3000L) {
                lastMode2LogAt = now;
                lastMode2Speed = speed; lastMode2Voltage = voltage;
                lastMode2Outside = outside; lastMode2Odo = odometer; lastMode2Tank = tankLiters;
                String tankText = tankLiters == 0 ? "-- (raw 0 ignoriert)" : tankLiters + " L";
                DiagLog.add(String.format(Locale.GERMANY,
                        "HCT 0x41/2: rpm=%d speed=%.2f km/h Bordnetz=%.2f V Außen=%.1f °C km=%d Tank=%s",
                        rpm, speed, voltage, outside, odometer, tankText));
            }
            return;
        }

        // Mode 3 behavior is confirmed by the live ignition/engine test: it is a warning/status byte, not RPM/temperature.
        if (mode == 3 && length >= 2) {
            int status = d[offset + 1] & 0xFF;
            if (status != lastMode3Status) {
                lastMode3Status = status;
                DiagLog.add(String.format(Locale.US,
                        "HCT 0x41/3 OEM Status=0x%02X Ölwarnung=%s Ladewarnung=%s",
                        status,
                        (status & 0x80) != 0 ? "AN" : "aus",
                        (status & 0x40) != 0 ? "AN" : "aus"));
            }
        }
    }

    public synchronized String summary() {
        if (counts.isEmpty()) return "noch keine com.microntek.sync-Pakete";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer,Integer> e : counts.entrySet()) {
            if (b.length() > 0) b.append("  ");
            b.append(String.format(Locale.US, "0x%02X:%d", e.getKey(), e.getValue()));
        }
        return b.toString();
    }

    public synchronized long validPacketCount() { return validPackets; }
    public synchronized long totalPacketCount() { return totalPackets; }
    public synchronized int lastDoorMask() { return lastDoorMask; }

    private static int be16(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    private static int signedBe16(byte[] d, int o) {
        int v = be16(d, o);
        return v >= 0x8000 ? v - 0x10000 : v;
    }

    private static long be24(byte[] d, int o) {
        return ((long)(d[o] & 0xFF) << 16) |
                ((long)(d[o + 1] & 0xFF) << 8) |
                (d[o + 2] & 0xFF);
    }

    private static String yn(boolean b) { return b ? "AUF" : "zu"; }
}
