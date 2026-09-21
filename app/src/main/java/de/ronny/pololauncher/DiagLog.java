package de.ronny.pololauncher;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

public final class DiagLog {
    private static final int MAX = 1200;
    private static final int MAX_LINE_CHARS = 1200;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY);

    private DiagLog() {}

    public static synchronized void add(String text) {
        if (text == null) text = "null";
        if (text.length() > MAX_LINE_CHARS) {
            text = text.substring(0, MAX_LINE_CHARS) + " … [gekürzt]";
        }
        String line = TS.format(new Date()) + "  " + text;
        LINES.addFirst(line);
        while (LINES.size() > MAX) LINES.removeLast();
    }

    public static synchronized String text() {
        return text(MAX);
    }

    public static synchronized String text(int maxLines) {
        StringBuilder b = new StringBuilder();
        int remaining = Math.max(0, maxLines);
        for (String s : LINES) {
            if (remaining-- == 0) break;
            b.append(s).append('\n');
        }
        return b.toString();
    }

    public static synchronized void clear() { LINES.clear(); }
}
