package de.ronny.pololauncher;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Short ring buffer of the important OBD2 connection events. The shared diagnostic log is flooded
 * by CAN frames and forgets these events within a minute; this one keeps the last entries, so a
 * connection problem is still visible in the log that is read minutes later.
 */
final class ObdHistory {
    private final int max;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private String lastText = "";
    private int repeats;

    ObdHistory(int max) { this.max = Math.max(1, max); }

    /** Adds one event; an identical repeat of the previous text is counted, not stored again. */
    synchronized void add(long wallMs, String text) {
        if (text == null) return;
        if (text.equals(lastText) && !lines.isEmpty()) {
            repeats++;
            lines.removeLast();
            lines.addLast(stamp(wallMs) + "  " + text + " (x" + (repeats + 1) + ")");
            return;
        }
        lastText = text;
        repeats = 0;
        lines.addLast(stamp(wallMs) + "  " + text);
        while (lines.size() > max) lines.removeFirst();
    }

    synchronized int size() { return lines.size(); }

    /** Oldest first, one event per line. */
    synchronized String dump() {
        if (lines.isEmpty()) return "(noch keine Ereignisse)\n";
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(line).append('\n');
        return out.toString();
    }

    private static String stamp(long wallMs) {
        return new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date(wallMs));
    }
}
