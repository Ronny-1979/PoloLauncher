package de.ronny.pololauncher;

/** Plain-Java regression tests; execute main with assertions implemented below. */
public final class Obd2PidDecoderTest {
    private static void eq(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void eq(double expected, double actual, double tol, String message) {
        if (!Double.isFinite(expected) || !Double.isFinite(actual) || Math.abs(expected - actual) > tol) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void nan(double actual, String message) {
        if (!Double.isNaN(actual)) throw new AssertionError(message + ": expected NaN, got " + actual);
    }

    public static void main(String[] args) {
        boolean rejectedNaN = false;
        try { eq(6d, Double.NaN, 0.001, "NaN regression"); }
        catch (AssertionError expected) { rejectedNaN = true; }
        check(rejectedNaN, "Numeric comparison must reject NaN");
        // 010C -> 41 0C 1A F8 : rpm = (0x1A*256 + 0xF8) / 4 = (26*256+248)/4 = 6904/4 = 1726
        int[] rpmData = Obd2PidDecoder.dataBytes("41 0C 1A F8", "0C");
        check(rpmData != null, "RPM data parsed");
        eq(1726, Obd2PidDecoder.rpm(rpmData), "RPM decode");

        // 010D -> 41 0D 50 : speed = 0x50 = 80 km/h
        int[] speedData = Obd2PidDecoder.dataBytes("41 0D 50", "0D");
        eq(80d, Obd2PidDecoder.speedKmh(speedData), 1e-9, "Speed decode");

        // 0110 -> 41 10 04 4C : MAF = (0x04*256+0x4C)/100 = (1024+76)/100 = 11.00 g/s
        int[] mafData = Obd2PidDecoder.dataBytes("41 10 04 4C", "10");
        eq(11.00d, Obd2PidDecoder.mafGs(mafData), 1e-6, "MAF decode");

        // 012F -> 41 2F 80 : fuel = 128*100/255 = 50.196...
        int[] fuelData = Obd2PidDecoder.dataBytes("41 2F 80", "2F");
        eq(50.196d, Obd2PidDecoder.fuelPercent(fuelData), 1e-2, "Fuel percent decode");

        // Consumption formula: 11 g/s MAF -> l/h -> l/100km at 80 km/h.
        // l/h = (mafGs*3600)/(14.7*720) = (11*3600)/10584 = 3.74150 l/h.
        double lph = Obd2PidDecoder.literPerHourFromMaf(11.00d);
        eq(3.74150d, lph, 1e-3, "MAF to l/h");
        // l/100km = lph/speed*100 = 3.74150/80*100 = 4.67687.
        double per100 = Obd2PidDecoder.literPer100kmFromLph(lph, 80d, 3d);
        eq(4.67687d, per100, 1e-2, "l/h to l/100km at 80 km/h");

        // Below the minimum speed, l/100km must be NaN (division noise), not a huge number.
        nan(Obd2PidDecoder.literPer100kmFromLph(lph, 1d, 3d), "l/100km suppressed below min speed");
        nan(Obd2PidDecoder.literPer100kmFromLph(lph, Double.NaN, 3d), "l/100km NaN for unknown speed");
        nan(Obd2PidDecoder.literPerHourFromMaf(Double.NaN), "l/h NaN for unknown MAF");

        // Robustness against clone quirks and line noise.
        check(Obd2PidDecoder.dataBytes("SEARCHING...", "0C") == null, "Bare SEARCHING with no data still rejected");
        // v0.7.0: real adapter reply seen in the field - SEARCHING noise followed by a genuine,
        // usable response on the same line must now be parsed, not discarded.
        int[] afterSearching = Obd2PidDecoder.dataBytes("SEARCHING... 41 00 BE 3E B8 13", "00");
        check(afterSearching != null, "Valid data after SEARCHING noise is recovered, not discarded");
        eq(0xBE, afterSearching[0], "First data byte after SEARCHING noise parsed correctly");
        check(Obd2PidDecoder.dataBytes("NO DATA", "0C") == null, "NO DATA rejected");
        check(Obd2PidDecoder.dataBytes("?", "0C") == null, "? rejected");
        check(Obd2PidDecoder.dataBytes("", "0C") == null, "Empty line rejected");
        check(Obd2PidDecoder.dataBytes(null, "0C") == null, "Null line rejected");
        check(Obd2PidDecoder.dataBytes("41 0D 50", "0C") == null, "Mismatched PID echo rejected");
        check(Obd2PidDecoder.dataBytes("41 0C ZZ 00", "0C") == null, "Malformed hex rejected");
        check(Obd2PidDecoder.dataBytes("7E8 06 41 0C 1A F8", "0C") == null,
                "Header-prefixed line without matching mode byte rejected");
        // Prompt/echo noise and lowercase hex must still parse.
        int[] lowerCase = Obd2PidDecoder.dataBytes("41 0c 1a f8>", "0C");
        eq(1726, Obd2PidDecoder.rpm(lowerCase), "Lowercase hex with trailing prompt parses");

        // Implausible/out-of-range values are rejected rather than silently clamped.
        eq(-1, Obd2PidDecoder.rpm(new int[]{0xFF, 0xFF}), "Implausible RPM rejected");
        // The two-byte MAF PID can never exceed 655.35 g/s (0xFFFF/100); confirm that
        // maximum-byte reading is still accepted rather than spuriously rejected.
        eq(655.35d, Obd2PidDecoder.mafGs(new int[]{0xFF, 0xFF}), 1e-6, "Maximum-byte MAF still accepted");
        // dataBytes() does not reject by length itself; rpm()/mafGs() require 2 bytes each.
        eq(-1, Obd2PidDecoder.rpm(new int[]{0x1A}), "Single-byte RPM payload rejected");
        nan(Obd2PidDecoder.mafGs(new int[]{0x04}), "Single-byte MAF payload rejected");

        // Supported-PID bitmap decoding (0100/0120 style responses).
        int[] allSupported = {0xFF, 0xFF, 0xFF, 0xFF};
        check(Obd2PidDecoder.pidSupportedFromBitmap(allSupported, 0x0C, 0x01), "RPM supported when bitmap is all-ones");
        check(Obd2PidDecoder.pidSupportedFromBitmap(allSupported, 0x0D, 0x01), "Speed supported when bitmap is all-ones");
        check(Obd2PidDecoder.pidSupportedFromBitmap(allSupported, 0x10, 0x01), "MAF supported when bitmap is all-ones");
        int[] noneSupported = {0x00, 0x00, 0x00, 0x00};
        check(!Obd2PidDecoder.pidSupportedFromBitmap(noneSupported, 0x0C, 0x01), "RPM unsupported when bitmap is all-zero");
        check(Obd2PidDecoder.pidSupportedFromBitmap(null, 0x0C, 0x01) == null, "Null bitmap data reports unknown, not false");
        // Realistic example from this fork's vehicle: RPM/Speed/MAP supported, MAF not.
        // 0x0C=index 11, 0x0D=index 12, 0x0B=index 10 -> all in byte1 (indices 8-15).
        // byte1 bits (MSB first, index 8..15): want indices 10,11,12 set, index 15 (0x10) clear.
        // bit(7-(10-8))=bit5(0x20) + bit(7-(11-8))=bit4(0x10) + bit(7-(12-8))=bit3(0x08) = 0x38.
        int[] noMaf = {0x00, 0x38, 0x00, 0x00};
        check(Obd2PidDecoder.pidSupportedFromBitmap(noMaf, 0x0B, 0x01), "MAP supported in realistic no-MAF ECU example");
        check(Obd2PidDecoder.pidSupportedFromBitmap(noMaf, 0x0C, 0x01), "RPM supported in realistic no-MAF ECU example");
        check(Obd2PidDecoder.pidSupportedFromBitmap(noMaf, 0x0D, 0x01), "Speed supported in realistic no-MAF ECU example");
        check(!Obd2PidDecoder.pidSupportedFromBitmap(noMaf, 0x10, 0x01), "MAF correctly reported unsupported in that example");
        // PID 0x2F comes from the SECOND bitmap (0120 response), first PID in it is 0x21.
        // index = 0x2F - 0x21 = 14 -> byte index 1, bit position 7-(14%8)=1 -> value 0x02.
        int[] tankSupported = {0x00, 0x02, 0x00, 0x00};
        check(Obd2PidDecoder.pidSupportedFromBitmap(tankSupported, 0x2F, 0x21), "Tank level (0x2F) decoded correctly from second bitmap");
        check(Obd2PidDecoder.pidSupportedFromBitmap(new int[]{0x00, 0x00, 0x00, 0x00}, 0x2F, 0x21) == Boolean.FALSE,
                "Tank level correctly false, not null, when within range but zero");
        check(Obd2PidDecoder.pidSupportedFromBitmap(allSupported, 0x99, 0x01) == null, "Out-of-range PID number reports unknown");

        // PID 0x0B intake manifold pressure: 41 0B 32 -> 0x32 = 50 kPa.
        int[] mapData = Obd2PidDecoder.dataBytes("41 0B 32", "0B");
        eq(50d, Obd2PidDecoder.mapKpa(mapData), 1e-9, "MAP decode");
        // PID 0x0F intake air temperature: 41 0F 5A -> 0x5A(90) - 40 = 50 degC.
        int[] iatData = Obd2PidDecoder.dataBytes("41 0F 5A", "0F");
        eq(50d, Obd2PidDecoder.intakeAirTempC(iatData), 1e-9, "IAT decode");
        // 41 0F 28 -> 0x28(40) - 40 = 0 degC (must not be treated as "no reading").
        int[] iatZero = Obd2PidDecoder.dataBytes("41 0F 28", "0F");
        eq(0d, Obd2PidDecoder.intakeAirTempC(iatZero), 1e-9, "IAT zero degC decode");

        // MAP-based speed-density MAF-equivalent (this fork's Polo 1.2 CGPB, no MAF sensor).
        // Worked example: MAP=50 kPa, RPM=850, displacement=1.198 L, VE=0.78, IAT=25 degC (298.15 K).
        // gramsPerSecond = 50 * 1.198 * 0.78 * 850 * (1000/(287*120)) / 298.15
        double mafEq = Obd2PidDecoder.mafEquivalentFromMap(50d, 850, 1.198d, 0.78d, 25d);
        eq(3.8676d, mafEq, 1e-3, "MAP-based air mass estimate at idle");
        // Higher load/RPM must yield a proportionally higher estimate, not a fixed number.
        double mafEqHighLoad = Obd2PidDecoder.mafEquivalentFromMap(90d, 3000, 1.198d, 0.85d, 35d);
        check(mafEqHighLoad > mafEq * 5, "Higher MAP/RPM yields substantially higher air mass estimate");
        // Invalid inputs must yield NaN, never a bogus number silently used downstream.
        nan(Obd2PidDecoder.mafEquivalentFromMap(Double.NaN, 850, 1.198d, 0.78d, 25d), "NaN MAP rejected");
        nan(Obd2PidDecoder.mafEquivalentFromMap(50d, -1, 1.198d, 0.78d, 25d), "Negative RPM rejected");
        nan(Obd2PidDecoder.mafEquivalentFromMap(50d, 850, 1.198d, 0.78d, Double.NaN), "NaN IAT rejected");
        nan(Obd2PidDecoder.mafEquivalentFromMap(50d, 850, 0d, 0.78d, 25d), "Zero displacement rejected");
        nan(Obd2PidDecoder.mafEquivalentFromMap(300d, 850, 1.198d, 0.78d, 25d), "Implausible MAP rejected");
        // The MAF-to-consumption formula must work identically whether fed a real MAF
        // reading or this MAP-based estimate - it only cares about air mass flow.
        double lphFromMap = Obd2PidDecoder.literPerHourFromMaf(mafEq);
        check(Double.isFinite(lphFromMap) && lphFromMap > 0, "l/h computable from MAP-based estimate too");

        System.out.println("Obd2PidDecoderTest: all cases passed");
    }
}
