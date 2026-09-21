package de.ronny.pololauncher;

import java.util.Locale;

/**
 * Parses ELM327 Mode 01 ASCII responses and turns the raw data bytes into
 * physical values, plus the fuel-consumption formulas derived from them.
 * Pure logic, no Android dependency, so it can run under the plain-Java
 * test harness in tests/run-tests.sh exactly like CanFrameNormalizer.
 */
final class Obd2PidDecoder {
    private Obd2PidDecoder() {}

    /** Removes the ELM327 prompt, echo noise and surrounding whitespace from one raw line. */
    static String cleanLine(String raw) {
        if (raw == null) return "";
        return raw.replace(">", "").replace("\r", " ").replace("\n", " ").trim();
    }

    /**
     * Extracts the data bytes from a Mode 01 reply line, e.g. "41 0C 1A F8" -> [0x1A, 0xF8].
     * Returns null for anything that is not a matching, well-formed data line: empty lines,
     * "NO DATA", "STOPPED", "?", a different PID's echo, or malformed hex.
     * A "SEARCHING..." prefix is stripped rather than rejecting the whole line: a real session
     * log (v0.7.0) showed a clone adapter send "SEARCHING... 41 00 BE 3E B8 13" as one line -
     * still finishing protocol negotiation but with a genuine, usable reply appended on the
     * same line. Discarding that reply was itself a bug this fix corrects.
     * expectedPid is matched case-insensitively; pass null to accept any PID under mode "41".
     */
    static int[] dataBytes(String line, String expectedPid) {
        if (line == null) return null;
        String s = cleanLine(line).toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.contains("NO DATA") || s.contains("STOPPED")
                || s.contains("ERROR") || s.contains("UNABLE") || s.contains("BUS INIT")
                || s.contains("?")) return null;
        int searchingAt = s.indexOf("SEARCHING");
        if (searchingAt >= 0) {
            s = s.substring(searchingAt + "SEARCHING".length()).replace(".", "").trim();
            if (s.isEmpty()) return null;
        }
        String[] tok = s.split("\\s+");
        if (tok.length < 3 || !"41".equals(tok[0])) return null;
        if (expectedPid != null && !expectedPid.equalsIgnoreCase(tok[1])) return null;
        int[] data = new int[tok.length - 2];
        for (int i = 2; i < tok.length; i++) {
            try {
                int v = Integer.parseInt(tok[i], 16);
                if (v < 0 || v > 0xFF) return null;
                data[i - 2] = v;
            } catch (NumberFormatException notHex) {
                return null;
            }
        }
        return data;
    }

    /**
     * The PID echoed by a Mode 01 reply ("41 0C ..." -> "0C"), or null when the reply is not a
     * well-formed Mode 01 answer. Used to notice replies that belong to an earlier command.
     */
    static String echoedPid(String line) {
        if (line == null) return null;
        String s = cleanLine(line).toUpperCase(Locale.ROOT);
        int searchingAt = s.indexOf("SEARCHING");
        if (searchingAt >= 0) s = s.substring(searchingAt + "SEARCHING".length()).replace(".", "").trim();
        String[] tok = s.split("\\s+");
        return tok.length >= 3 && "41".equals(tok[0]) && tok[1].length() == 2 ? tok[1] : null;
    }

    /** PID 0x0C engine RPM: ((A*256)+B)/4. -1 when missing/implausible. */
    static int rpm(int[] d) {
        if (d == null || d.length < 2) return -1;
        int v = (d[0] * 256 + d[1]) / 4;
        return v >= 0 && v < 10_000 ? v : -1;
    }

    /** PID 0x0D vehicle speed in km/h: A. NaN when missing/implausible. */
    static double speedKmh(int[] d) {
        if (d == null || d.length < 1) return Double.NaN;
        int v = d[0];
        return v >= 0 && v <= 255 ? v : Double.NaN;
    }

    /** PID 0x10 mass air flow in g/s: ((A*256)+B)/100. NaN when missing/implausible. */
    static double mafGs(int[] d) {
        if (d == null || d.length < 2) return Double.NaN;
        double v = (d[0] * 256 + d[1]) / 100.0;
        return v >= 0 && v < 700 ? v : Double.NaN;
    }

    /** PID 0x2F fuel tank level in percent: A*100/255. NaN when missing/implausible. */
    static double fuelPercent(int[] d) {
        if (d == null || d.length < 1) return Double.NaN;
        double v = d[0] * 100.0 / 255.0;
        return v >= 0 && v <= 100 ? v : Double.NaN;
    }

    /**
     * Decodes an OBD2 "supported PIDs" bitmap response (e.g. from 0100 or 0120) and reports
     * whether pidNumber is supported. firstPidInBitmap is the first PID that bitmap covers
     * (1 for 0100's response, 0x21 for 0120's response, etc.). Returns null when data is null
     * (no usable reply) or pidNumber falls outside the bitmap this data actually covers.
     */
    static Boolean pidSupportedFromBitmap(int[] data, int pidNumber, int firstPidInBitmap) {
        if (data == null) return null;
        int index = pidNumber - firstPidInBitmap;
        if (index < 0 || index >= data.length * 8) return null;
        int byteIndex = index / 8;
        int bitInByte = 7 - (index % 8); // MSB of each byte is the lowest PID number in that byte
        return (data[byteIndex] & (1 << bitInByte)) != 0;
    }

    /** PID 0x0B intake manifold absolute pressure in kPa: A. NaN when missing/implausible. */
    static double mapKpa(int[] d) {
        if (d == null || d.length < 1) return Double.NaN;
        int v = d[0];
        return v >= 0 && v <= 255 ? v : Double.NaN;
    }

    /** PID 0x0F intake air temperature in °C: A-40. NaN when missing/implausible. */
    static double intakeAirTempC(int[] d) {
        if (d == null || d.length < 1) return Double.NaN;
        double v = d[0] - 40;
        return v >= -40 && v <= 215 ? v : Double.NaN;
    }

    /**
     * Speed-density estimate of intake air mass flow, for engines with no MAF sensor
     * (this fork's Polo 1.2 CGPB reports intake manifold pressure instead - see README).
     * Derived from the ideal gas law: for a 4-stroke engine the whole displacement volume
     * is drawn in once every two revolutions, so
     *   airMassRate [g/s] = mapKpa * displacementLiters * ve * rpm * K / intakeTempKelvin
     * with K = 1000 / (287 * 120) folding in the Pa/kPa and L/m^3 unit conversions, the
     * specific gas constant of air (287 J/(kg*K)), and revolutions-per-intake-event (120 =
     * 60 s/min * 2 rev/cycle). volumetricEfficiency is an assumed constant (not measured),
     * so this is an estimate, not a measurement like real MAF would be.
     */
    static double mafEquivalentFromMap(double mapKpa, int rpm, double displacementLiters,
                                        double volumetricEfficiency, double intakeTempC) {
        if (!Double.isFinite(mapKpa) || mapKpa <= 0 || mapKpa > 260) return Double.NaN;
        if (rpm < 0 || rpm > 9000) return Double.NaN;
        if (!Double.isFinite(intakeTempC) || intakeTempC < -40 || intakeTempC > 100) return Double.NaN;
        if (displacementLiters <= 0 || volumetricEfficiency <= 0) return Double.NaN;
        double kelvin = intakeTempC + 273.15;
        final double K = 1000.0 / (287.0 * 120.0);
        double gramsPerSecond = mapKpa * displacementLiters * volumetricEfficiency * rpm * K / kelvin;
        return gramsPerSecond >= 0 && gramsPerSecond <= 200 ? gramsPerSecond : Double.NaN;
    }

    /** Rolling engine above idle with a nearly closed throttle (manifold vacuum): typical overrun. */
    static final int FUEL_CUT_MIN_RPM = 1300;
    static final double FUEL_CUT_MAX_MAP_KPA = 30d;
    static final double FUEL_CUT_MIN_SPEED_KMH = 10d;

    /**
     * Estimate for deceleration fuel cut-off (overrun). The speed-density model derives fuel from
     * air mass and would otherwise count fuel while the injectors are actually off. This is a
     * heuristic on MAP/RPM/speed only (thresholds above, tuned for a 1.2 MPI Polo, NOT verified
     * on the car): the diagnostic screen shows how many rounds were classified this way so the
     * thresholds can be checked against a real drive. The user can switch the estimate off.
     */
    static boolean fuelCutSuspected(double mapKpa, int rpm, double speedKmh) {
        return Double.isFinite(mapKpa) && Double.isFinite(speedKmh)
                && rpm >= FUEL_CUT_MIN_RPM && speedKmh >= FUEL_CUT_MIN_SPEED_KMH
                && mapKpa <= FUEL_CUT_MAX_MAP_KPA;
    }

    /**
     * Litres/hour from mass air flow, petrol only: stoichiometric air/fuel ratio 14.7,
     * petrol density approximately 720 g/l. Not valid for diesel (no fixed AFR). Works
     * equally for a real MAF reading or a MAP-based speed-density estimate: the formula
     * only cares about the air mass flow, not how it was obtained.
     */
    static double literPerHourFromMaf(double mafGs) {
        if (!Double.isFinite(mafGs) || mafGs < 0) return Double.NaN;
        double lph = (mafGs * 3600.0) / (14.7 * 720.0);
        return lph >= 0 && lph <= 60 ? lph : Double.NaN;
    }

    /**
     * L/100km from L/h and current speed. Below minKmh the result is dominated by
     * division noise rather than a meaningful number, so callers get NaN and should
     * show the L/h figure (idle/city creep) instead.
     */
    static double literPer100kmFromLph(double lph, double speedKmh, double minKmh) {
        if (!Double.isFinite(lph) || !Double.isFinite(speedKmh) || speedKmh < minKmh) return Double.NaN;
        double v = lph / speedKmh * 100.0;
        return v >= 0 && v <= 60 ? v : Double.NaN;
    }
}
