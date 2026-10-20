package de.ronny.pololauncher;

import java.util.Locale;

/** Android-independent parser/resolver for OSM maxspeed tags. */
final class SpeedLimitValue {
    static final int UNKNOWN = -1;
    static final int UNRESTRICTED = 0;

    private SpeedLimitValue() {}

    static int resolve(String maxspeed, String forward, String backward,
                       String zoneMaxspeed, String sourceMaxspeed, String maxspeedType,
                       boolean directionKnown, boolean travellingForward) {
        if (directionKnown) {
            int directional = parse(travellingForward ? forward : backward);
            if (directional != UNKNOWN) return directional;
        }

        int value = parse(maxspeed);
        if (value != UNKNOWN) return value;

        value = parse(zoneMaxspeed);
        if (value != UNKNOWN) return value;

        value = parseContext(sourceMaxspeed);
        if (value != UNKNOWN) return value;

        return parseContext(maxspeedType);
    }

    static int parse(String raw) {
        if (raw == null) return UNKNOWN;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return UNKNOWN;

        // Lane-specific / conditional compound values cannot be represented by one sign safely.
        if (value.indexOf(';') >= 0 || value.indexOf('|') >= 0 || value.contains("@"))
            return UNKNOWN;

        if ("none".equals(value) || "de:motorway".equals(value))
            return UNRESTRICTED;
        if ("de:urban".equals(value)) return 50;
        if ("de:rural".equals(value)) return 100;

        // OSM German zone variants seen in zone:maxspeed / maxspeed:type / source:maxspeed:
        // DE:30, DE:zone30 and DE:zone:30.
        if (value.startsWith("de:")) {
            int number = trailingInteger(value);
            if (valid(number)) return number;
        }

        boolean mph = value.contains("mph");
        int number = leadingInteger(value);
        if (!valid(number)) return UNKNOWN;
        if (mph) number = (int) Math.round(number * 1.609344d);
        return valid(number) ? number : UNKNOWN;
    }

    private static int parseContext(String raw) {
        if (raw == null) return UNKNOWN;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("de:urban".equals(value)) return 50;
        if ("de:rural".equals(value)) return 100;
        if ("de:motorway".equals(value)) return UNRESTRICTED;
        if (value.startsWith("de:zone")) {
            int number = trailingInteger(value);
            return valid(number) ? number : UNKNOWN;
        }
        return UNKNOWN;
    }

    private static int leadingInteger(String value) {
        int result = 0;
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                result = result * 10 + (c - '0');
                digits++;
            } else if (digits > 0) {
                break;
            } else if (!Character.isWhitespace(c)) {
                return -1;
            }
        }
        return digits == 0 ? -1 : result;
    }

    private static int trailingInteger(String value) {
        int end = value.length() - 1;
        while (end >= 0 && !Character.isDigit(value.charAt(end))) end--;
        if (end < 0) return -1;
        int start = end;
        while (start >= 0 && Character.isDigit(value.charAt(start))) start--;
        try { return Integer.parseInt(value.substring(start + 1, end + 1)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static boolean valid(int value) {
        return value >= 5 && value <= 250;
    }
}
