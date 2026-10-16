/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.GraphemeClusters;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared utility methods for TUI commands.
 */
final class TuiHelper {

    private TuiHelper() {
    }

    /**
     * Find PIDs of running Camel integrations matching the given name pattern. Scans the {@code ~/.camel/} directory
     * for status files rather than iterating all OS processes, which is significantly faster.
     */
    static List<Long> findPids(String name, Function<String, Path> statusFileResolver) {
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;

        for (long pid : findCandidatePids()) {
            if (pid == cur) {
                continue;
            }
            // check process is still alive
            if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                continue;
            }
            JsonObject root = loadStatus(pid, statusFileResolver);
            if (root != null) {
                ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
                String pName = ProcessHelper.extractName(root, ph);
                pName = FileUtil.onlyName(pName);
                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                    pids.add(pid);
                } else {
                    JsonObject context = (JsonObject) root.get("context");
                    if (context != null) {
                        pName = context.getString("name");
                        if ("CamelJBang".equals(pName)) {
                            pName = null;
                        }
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(pid);
                        }
                    }
                }
            }
        }
        return pids;
    }

    /**
     * List candidate PIDs by scanning {@code ~/.camel/} for {@code *-status.json} files. This is O(number of status
     * files) rather than O(total OS processes).
     */
    static List<Long> findCandidatePids() {
        List<Long> pids = new ArrayList<>();
        try {
            Path camelDir = CommandLineHelper.getCamelDir();
            if (Files.isDirectory(camelDir)) {
                try (var files = Files.list(camelDir)) {
                    files.forEach(p -> {
                        String fn = p.getFileName().toString();
                        if (fn.endsWith("-status.json")) {
                            try {
                                long pid = Long.parseLong(fn.substring(0, fn.length() - "-status.json".length()));
                                pids.add(pid);
                            } catch (NumberFormatException e) {
                                // not a PID-based status file
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return pids;
    }

    /**
     * Load the status JSON for a given PID.
     */
    static JsonObject loadStatus(long pid, Function<String, Path> statusFileResolver) {
        try {
            Path f = statusFileResolver.apply(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    static String stripCategory(String name) {
        if (name == null) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /**
     * An endpoint URI as people write it: Camel normalizes URIs with their placeholders and spaces percent-encoded
     * (platform-http:///stock/%7Bsku%7D), which reads poorly in a table. Only for display; the URI itself stays the
     * key.
     */
    static String displayUri(String uri) {
        if (uri == null || uri.indexOf('%') < 0) {
            return uri;
        }
        try {
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }

    /**
     * The spans cut to the given display width, ending with an ellipsis where text is cut, so a cell that is too narrow
     * says so instead of losing its end silently.
     */
    static Line fitLine(List<Span> spans, int max) {
        List<Span> answer = new ArrayList<>();
        int used = 0;
        for (Span span : spans) {
            int w = CharWidth.of(span.content());
            if (used + w <= max) {
                answer.add(span);
                used += w;
            } else {
                int room = max - used;
                if (room > 0) {
                    answer.add(Span.styled(
                            CharWidth.truncateWithEllipsis(span.content(), room, CharWidth.TruncatePosition.END),
                            span.style()));
                }
                break;
            }
        }
        return Line.from(answer);
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return CharWidth.of(s) > max
                ? CharWidth.truncateWithEllipsis(s, max, CharWidth.TruncatePosition.END)
                : s;
    }

    /**
     * Truncate a string to max length by removing from the start (keeping the end), prepending an ellipsis. Useful for
     * Java type names where the class name is at the end and more meaningful than the package prefix.
     */
    static String truncateStart(String s, int max) {
        if (s == null) {
            return "";
        }
        return CharWidth.of(s) > max
                ? CharWidth.truncateWithEllipsis(s, max, CharWidth.TruncatePosition.START)
                : s;
    }

    /**
     * Convert an Object (typically from JSON) to a long value.
     */
    static long objToLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }

    /**
     * Fix control characters in a log line without stripping ANSI color codes. Replaces tab with two spaces and drops
     * carriage returns, so the colored content can be passed to {@link #ansiToLine}.
     */
    static String fixControlChars(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        if (line.indexOf('\t') < 0 && line.indexOf('\r') < 0) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\r') {
                // skip
            } else if (ch == '\t') {
                sb.append("        "); // 8 spaces matches default terminal tab stop width
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Strip ANSI escape sequences and carriage returns from a log line, and expand tabs. All of an escape sequence is
     * dropped (see {@link #escapeEnd}), so the text of a window title or a hyperlink target does not show.
     */
    static String stripAnsi(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        if (line.indexOf('\u001B') < 0 && line.indexOf('\r') < 0 && line.indexOf('\t') < 0) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        int i = 0;
        while (i < line.length()) {
            char ch = line.charAt(i);
            if (ch == '\u001B') {
                i = escapeEnd(line, i);
            } else if (ch == '\r') {
                i++;
            } else if (ch == '\t') {
                sb.append("        "); // 8 spaces matches default terminal tab stop width
                i++;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * The index just past the escape sequence that starts with the ESC at {@code start}:
     * <ul>
     * <li>a CSI sequence, ESC [ up to its final byte (colors are ESC [ ... m);</li>
     * <li>an OSC string such as a window title or a hyperlink, ESC ] up to BEL or ESC \;</li>
     * <li>a DCS, SOS, PM or APC string, ESC P, X, ^ or _ up to ESC \;</li>
     * <li>an escape with intermediate bytes, such as ESC ( B, up to its final byte;</li>
     * <li>any other escape, ESC and one character.</li>
     * </ul>
     * A string that is not terminated runs to the end of the text, a sequence cut off by the next ESC ends there, and
     * an ESC that starts no sequence is dropped on its own.
     */
    static int escapeEnd(String s, int start) {
        int n = s.length();
        if (start + 1 >= n) {
            return n;
        }
        char next = s.charAt(start + 1);
        int i = start + 2;
        switch (next) {
            case '[' -> {
                while (i < n) {
                    char c = s.charAt(i);
                    if (c >= '@' && c <= '~') {
                        return i + 1;
                    }
                    if (c == '\u001B') {
                        return i;
                    }
                    i++;
                }
                return n;
            }
            case ']', 'P', 'X', '^', '_' -> {
                while (i < n) {
                    char c = s.charAt(i);
                    if (c == '\u0007' && next == ']') {
                        return i + 1;
                    }
                    if (c == '\u001B' && i + 1 < n && s.charAt(i + 1) == '\\') {
                        return i + 2;
                    }
                    i++;
                }
                return n;
            }
            default -> {
                if (next >= ' ' && next <= '/') {
                    while (i < n && s.charAt(i) >= ' ' && s.charAt(i) <= '/') {
                        i++;
                    }
                    return Math.min(n, i + 1);
                }
                return next >= '0' && next <= '~' ? start + 2 : start + 1;
            }
        }
    }

    /**
     * Parse a raw log line (which may contain ANSI SGR color codes) into a TamboUI {@link Line} of styled
     * {@link Span}s, skipping the first {@code hSkip} display-width columns (for horizontal scrolling). Color state is
     * tracked through the skipped region so colors that start before the visible area still apply correctly. Other
     * escape sequences are dropped (see {@link #escapeEnd}).
     */
    static Line ansiToLine(String raw, int hSkip) {
        if (raw == null || raw.isEmpty()) {
            return Line.from(Span.raw(""));
        }
        List<Span> spans = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Style style = Style.EMPTY;
        int consumed = 0;
        int i = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '\u001B') {
                int end = escapeEnd(raw, i);
                if (isSgr(raw, i, end)) {
                    if (text.length() > 0) {
                        spans.add(Span.styled(text.toString(), style));
                        text.setLength(0);
                    }
                    style = applySgr(style, raw.substring(i + 2, end - 1));
                }
                i = end;
            } else if (ch == '\r') {
                i++;
            } else {
                int cp = raw.codePointAt(i);
                int w = CharWidth.of(cp);
                int charLen = Character.charCount(cp);
                if (consumed >= hSkip) {
                    text.append(raw, i, i + charLen);
                } else if (consumed + w > hSkip) {
                    // Wide char straddles the skip boundary — skip it entirely
                }
                if (w > 0) {
                    consumed += w;
                }
                i += charLen;
            }
        }
        if (text.length() > 0) {
            spans.add(Span.styled(text.toString(), style));
        }
        if (spans.isEmpty()) {
            return Line.from(Span.raw(""));
        }
        return Line.from(spans);
    }

    /**
     * The line without its first {@code columns} display columns, for horizontal scrolling, keeping the style of each
     * span. A wide character cut by the edge is left out.
     */
    static Line skipColumns(Line line, int columns) {
        if (columns <= 0) {
            return line;
        }
        List<Span> result = new ArrayList<>();
        int skip = columns;
        for (Span span : line.spans()) {
            if (skip <= 0) {
                result.add(span);
                continue;
            }
            String text = span.content();
            int spanWidth = CharWidth.of(text);
            if (spanWidth <= skip) {
                skip -= spanWidth;
            } else {
                int i = 0;
                int consumed = 0;
                while (i < text.length() && consumed < skip) {
                    int cp = text.codePointAt(i);
                    consumed += CharWidth.of(cp);
                    i += Character.charCount(cp);
                }
                skip = 0;
                String remaining = text.substring(i);
                if (!remaining.isEmpty()) {
                    result.add(Span.styled(remaining, span.style()));
                }
            }
        }
        return Line.from(result);
    }

    /** Whether the escape sequence from start to end is an SGR one, ESC [ params m, and not a private CSI ... m. */
    private static boolean isSgr(String s, int start, int end) {
        return end - start >= 3 && s.charAt(start + 1) == '[' && s.charAt(end - 1) == 'm'
                && (end - start == 3 || "<=>?".indexOf(s.charAt(start + 2)) < 0);
    }

    private static Style applySgr(Style style, String params) {
        if (params.isEmpty()) {
            return Style.EMPTY;
        }
        String[] parts = params.split(";", -1);
        int j = 0;
        while (j < parts.length) {
            if (parts[j].indexOf(':') >= 0) {
                // the ITU T.416 form, with the color in sub-parameters: 38:2::r:g:b, 38:5:n, 4:3 (curly underline)
                style = applySgrSubParameters(style, parts[j].split(":", -1));
                j++;
                continue;
            }
            int code;
            try {
                code = parts[j].isEmpty() ? 0 : Integer.parseInt(parts[j]);
            } catch (NumberFormatException e) {
                j++;
                continue;
            }
            switch (code) {
                case 0 -> style = Style.EMPTY;
                case 1 -> style = style.bold();
                case 2 -> style = style.dim();
                case 3 -> style = style.italic();
                case 4 -> style = style.underlined();
                case 5 -> style = style.slowBlink();
                case 6 -> style = style.rapidBlink();
                case 7 -> style = style.reversed();
                case 8 -> style = style.hidden();
                case 9 -> style = style.crossedOut();
                case 22 -> style = style.notBold().notDim();
                case 23 -> style = style.notItalic();
                case 24 -> style = style.notUnderlined();
                case 25 -> style = style.removeModifier(Modifier.SLOW_BLINK).removeModifier(Modifier.RAPID_BLINK);
                case 27 -> style = style.notReversed();
                case 28 -> style = style.notHidden();
                case 29 -> style = style.notCrossedOut();
                case 30 -> style = style.fg(Color.ansi(AnsiColor.BLACK));
                case 31 -> style = style.fg(Color.ansi(AnsiColor.RED));
                case 32 -> style = style.fg(Color.ansi(AnsiColor.GREEN));
                case 33 -> style = style.fg(Color.ansi(AnsiColor.YELLOW));
                case 34 -> style = style.fg(Color.ansi(AnsiColor.BLUE));
                case 35 -> style = style.fg(Color.ansi(AnsiColor.MAGENTA));
                case 36 -> style = style.fg(Color.ansi(AnsiColor.CYAN));
                case 37 -> style = style.fg(Color.ansi(AnsiColor.WHITE));
                case 38, 48 -> {
                    Color color = null;
                    if (j + 2 < parts.length && "5".equals(parts[j + 1])) {
                        color = indexedColor(parts[j + 2]);
                        j += 2;
                    } else if (j + 4 < parts.length && "2".equals(parts[j + 1])) {
                        color = rgbColor(parts[j + 2], parts[j + 3], parts[j + 4]);
                        j += 4;
                    }
                    if (color != null) {
                        style = code == 38 ? style.fg(color) : style.bg(color);
                    }
                }
                case 39 -> style = style.fg(Color.RESET);
                case 40 -> style = style.bg(Color.ansi(AnsiColor.BLACK));
                case 41 -> style = style.bg(Color.ansi(AnsiColor.RED));
                case 42 -> style = style.bg(Color.ansi(AnsiColor.GREEN));
                case 43 -> style = style.bg(Color.ansi(AnsiColor.YELLOW));
                case 44 -> style = style.bg(Color.ansi(AnsiColor.BLUE));
                case 45 -> style = style.bg(Color.ansi(AnsiColor.MAGENTA));
                case 46 -> style = style.bg(Color.ansi(AnsiColor.CYAN));
                case 47 -> style = style.bg(Color.ansi(AnsiColor.WHITE));
                case 49 -> style = style.bg(Color.RESET);
                case 90 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_BLACK));
                case 91 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_RED));
                case 92 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_GREEN));
                case 93 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_YELLOW));
                case 94 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_BLUE));
                case 95 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_MAGENTA));
                case 96 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_CYAN));
                case 97 -> style = style.fg(Color.ansi(AnsiColor.BRIGHT_WHITE));
                case 100 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_BLACK));
                case 101 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_RED));
                case 102 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_GREEN));
                case 103 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_YELLOW));
                case 104 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_BLUE));
                case 105 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_MAGENTA));
                case 106 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_CYAN));
                case 107 -> style = style.bg(Color.ansi(AnsiColor.BRIGHT_WHITE));
                default -> {
                    /* ignore unknown SGR codes */ }
            }
            j++;
        }
        return style;
    }

    private static Style applySgrSubParameters(Style style, String[] sub) {
        switch (sub[0]) {
            case "38", "48" -> {
                Color color = null;
                if (sub.length >= 3 && "5".equals(sub[1])) {
                    color = indexedColor(sub[2]);
                } else if (sub.length >= 6 && "2".equals(sub[1])) {
                    // 38:2:<color space id>:r:g:b
                    color = rgbColor(sub[3], sub[4], sub[5]);
                } else if (sub.length == 5 && "2".equals(sub[1])) {
                    // 38:2:r:g:b, as some terminals write it
                    color = rgbColor(sub[2], sub[3], sub[4]);
                }
                if (color != null) {
                    style = "38".equals(sub[0]) ? style.fg(color) : style.bg(color);
                }
            }
            case "4" -> style = "0".equals(sub[1]) ? style.notUnderlined() : style.underlined();
            default -> {
                if (!sub[0].isEmpty()) {
                    style = applySgr(style, sub[0]);
                }
            }
        }
        return style;
    }

    private static Color indexedColor(String index) {
        try {
            int n = Integer.parseInt(index);
            return n >= 0 && n <= 255 ? Color.indexed(n) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Color rgbColor(String r, String g, String b) {
        try {
            return Color.rgb(Integer.parseInt(r), Integer.parseInt(g), Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static final Style[] DURATION_BAND_STYLES = {
            Style.EMPTY.fg(Color.GREEN),
            Style.EMPTY.fg(Color.LIGHT_GREEN),
            Style.EMPTY.fg(Color.YELLOW),
            Style.EMPTY.fg(Color.rgb(0xFF, 0xA5, 0x00)),
            Style.EMPTY.fg(Color.MAGENTA),
    };

    static Style colorForDuration(long duration, long minDuration, long maxDuration) {
        if (maxDuration <= minDuration) {
            return DURATION_BAND_STYLES[0];
        }
        double ratio = (Math.log1p(duration) - Math.log1p(minDuration))
                       / (Math.log1p(maxDuration) - Math.log1p(minDuration));
        int bandIndex = Math.max(0, Math.min((int) (ratio * 5), 4));
        // band 2 uses the theme warning color instead of the static array entry
        if (bandIndex == 2) {
            return Theme.warning();
        }
        return DURATION_BAND_STYLES[bandIndex];
    }

    // ---- UI helpers ----

    static void hint(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key + " ", Theme.hintKey()));
        spans.add(Span.raw(" " + label + "  "));
    }

    static void hintLast(List<Span> spans, String key, String label) {
        spans.add(Span.styled(" " + key + " ", Theme.hintKey()));
        spans.add(Span.raw(" " + label));
    }

    /**
     * Builds one line of key hints in the footer style, e.g. {@code hintLine("Enter", "confirm", "Esc", "cancel")}. Use
     * this for hints drawn inside a dialog so they match the footer bar.
     *
     * @param keyLabelPairs alternating key and label, must have an even length
     */
    static Line hintLine(String... keyLabelPairs) {
        if (keyLabelPairs.length % 2 != 0) {
            throw new IllegalArgumentException("keyLabelPairs must be key/label pairs");
        }
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < keyLabelPairs.length; i += 2) {
            if (i + 2 < keyLabelPairs.length) {
                hint(spans, keyLabelPairs[i], keyLabelPairs[i + 1]);
            } else {
                hintLast(spans, keyLabelPairs[i], keyLabelPairs[i + 1]);
            }
        }
        return Line.from(spans);
    }

    static int compareStr(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareToIgnoreCase(b);
    }

    // ---- Data formatting helpers ----

    static final String[] SMALL_CAMEL = {
            " ,,__",
            "/o.  \\___/\\",
            "\\__/       \\",
            "   |   |   |",
            "   |   |   |~",
            "  (_) (_) (_)",
    };

    static JsonObject pollJsonResponse(Path outputFile, long timeout) {
        long start = MonotonicClock.millis();
        while (MonotonicClock.millis() - start < timeout) {
            try {
                Thread.sleep(100);
                if (Files.exists(outputFile) && outputFile.toFile().length() > 0) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    static String formatSinceLast(IntegrationInfo info) {
        return formatSinceLast(info.sinceLastStarted, info.sinceLastCompleted, info.sinceLastFailed);
    }

    static String formatSinceLast(String started, String completed, String failed) {
        StringBuilder sb = new StringBuilder();
        if (started != null) {
            sb.append(started);
        }
        if (completed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(completed);
        }
        if (failed != null) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(failed);
        }
        return sb.toString();
    }

    static String formatSinceLastRoute(RouteInfo route) {
        return formatSinceLast(route.sinceLastStarted, route.sinceLastCompleted, route.sinceLastFailed);
    }

    static String throughputPerMinute(String msgPerSec) {
        if (msgPerSec == null || msgPerSec.isEmpty()) {
            return msgPerSec;
        }
        try {
            double perSec = Double.parseDouble(msgPerSec);
            double perMin = perSec * 60;
            return String.format(Locale.US, "%.1f", perMin);
        } catch (NumberFormatException e) {
            return msgPerSec;
        }
    }

    static String formatDurationMs(long ms) {
        if (ms <= 0) {
            return "0ms";
        }
        if (ms < 1000) {
            return ms + "ms";
        }
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + "d" + (hours % 24) + "h";
        }
        if (hours > 0) {
            return hours + "h" + (minutes % 60) + "m";
        }
        if (minutes > 0) {
            return minutes + "m" + (seconds % 60) + "s";
        }
        // 2.02s reads better than 2s20ms; a tenth is enough from 10 seconds on
        return seconds < 10
                ? String.format(Locale.US, "%.2fs", ms / 1000.0)
                : String.format(Locale.US, "%.1fs", ms / 1000.0);
    }

    /**
     * Wraps lines to a width with a hanging indent: the continuation of a "key = value" line starts under the value,
     * and of an indented line under its first character, where a plain word wrap would start it at the left edge.
     */
    static List<Line> hangingWrap(List<Line> lines, int width) {
        List<Line> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            if (width < 8 || line.width() <= width) {
                out.add(line);
            } else {
                wrapLine(line, width, out);
            }
        }
        return out;
    }

    private record StyledChar(String text, Style style, int width) {
    }

    private static void wrapLine(Line line, int width, List<Line> out) {
        List<StyledChar> chars = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        for (Span span : line.spans()) {
            String content = span.content();
            for (int i = 0; i < content.length();) {
                int cp = content.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                chars.add(new StyledChar(ch, span.style(), Math.max(0, CharWidth.of(cp))));
                plain.append(ch);
                i += Character.charCount(cp);
            }
        }
        String text = plain.toString();
        int indent = 0;
        int eq = text.indexOf(" = ");
        if (eq > 0 && eq + 3 < width * 3 / 5) {
            indent = eq + 3;
        } else {
            while (indent < text.length() && text.charAt(indent) == ' ') {
                indent++;
            }
        }
        indent = Math.min(indent, width / 2);

        int start = 0;
        boolean first = true;
        while (start < chars.size()) {
            int room = first ? width : width - indent;
            int used = 0;
            int end = start;
            int lastSpace = -1;
            while (end < chars.size() && used + chars.get(end).width() <= room) {
                if (" ".equals(chars.get(end).text())) {
                    lastSpace = end;
                }
                used += chars.get(end).width();
                end++;
            }
            if (end < chars.size() && lastSpace > start) {
                // break after the last space that fits, so a word is not cut
                end = lastSpace + 1;
            } else if (end == start) {
                end = start + 1;
            }
            List<Span> spans = new ArrayList<>();
            if (!first && indent > 0) {
                spans.add(Span.raw(" ".repeat(indent)));
            }
            StringBuilder run = new StringBuilder();
            Style runStyle = null;
            for (int i = start; i < end; i++) {
                StyledChar c = chars.get(i);
                if (runStyle != null && !runStyle.equals(c.style())) {
                    spans.add(Span.styled(run.toString(), runStyle));
                    run.setLength(0);
                }
                runStyle = c.style();
                run.append(c.text());
            }
            if (!run.isEmpty()) {
                spans.add(Span.styled(run.toString(), runStyle));
            }
            out.add(Line.from(spans));
            start = end;
            // a continuation does not start with the spaces it broke at
            while (start < chars.size() && " ".equals(chars.get(start).text())) {
                start++;
            }
            first = false;
        }
    }

    static String formatLoad(String l1, String l5, String l15) {
        String s1 = l1 != null && !"0.00".equals(l1) ? l1 : "0";
        String s5 = l5 != null && !"0.00".equals(l5) ? l5 : "0";
        String s15 = l15 != null && !"0.00".equals(l15) ? l15 : "0";
        return s1 + "/" + s5 + "/" + s15;
    }

    static String formatMemory(long used, long max) {
        if (used <= 0) {
            return "";
        }
        String u = formatBytes(used);
        if (max > 0) {
            return u + "/" + formatBytes(max);
        }
        return u;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        long mb = bytes / (1024 * 1024);
        if (mb >= 1024) {
            long gb = mb / 1024;
            long rem = mb % 1024;
            if (rem == 0) {
                return gb + "G";
            }
            return String.format(Locale.US, "%.1fG", mb / 1024.0);
        }
        return mb + "M";
    }

    static String formatThreads(int count, int peak) {
        if (count <= 0) {
            return "";
        }
        return count + "/" + peak;
    }

    static Style topTimeStyle(long ms) {
        if (ms >= 1000) {
            return Theme.error().bold();
        } else if (ms >= 100) {
            return Theme.warning();
        }
        return Style.EMPTY;
    }

    static Style topDeltaStyle(long delta) {
        if (delta > 0) {
            return Theme.error();
        } else if (delta < 0) {
            return Theme.success();
        }
        return Style.EMPTY;
    }

    static String buildBar(long value, long maxValue, int maxWidth) {
        if (value <= 0 || maxValue <= 0) {
            return "";
        }
        int len = (int) Math.round((double) value / maxValue * maxWidth);
        len = Math.max(len > 0 ? 1 : 0, Math.min(len, maxWidth));
        return "█".repeat(len);
    }

    static Line buildPercentileBarLine(long p50, long p95, long p99, int barWidth) {
        if (p99 <= 0 || barWidth <= 1) {
            return Line.from(Span.raw(""));
        }
        int segments = (p50 > 0 ? 1 : 0) + (p95 > p50 ? 1 : 0) + (p99 > p95 ? 1 : 0);
        if (segments < 2) {
            return Line.from(Span.raw(""));
        }
        int greenWidth = (int) Math.round((double) p50 / p99 * barWidth);
        int yellowWidth = (int) Math.round((double) (p95 - p50) / p99 * barWidth);
        int redWidth = barWidth - greenWidth - yellowWidth;
        if (greenWidth <= 0 && p50 > 0) {
            greenWidth = 1;
        }
        if (yellowWidth <= 0 && p95 > p50) {
            yellowWidth = 1;
        }
        if (redWidth <= 0 && p99 > p95) {
            redWidth = 1;
        }
        int total = greenWidth + yellowWidth + redWidth;
        if (total > barWidth) {
            redWidth = Math.max(0, barWidth - greenWidth - yellowWidth);
        }
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" "));
        if (greenWidth > 0) {
            spans.add(Span.styled("█".repeat(greenWidth), Theme.success()));
        }
        if (yellowWidth > 0) {
            spans.add(Span.styled("▒".repeat(yellowWidth), Theme.warning()));
        }
        if (redWidth > 0) {
            spans.add(Span.styled("░".repeat(redWidth), Theme.error()));
        }
        return Line.from(spans);
    }

    static String shortTypeName(String type) {
        if (type == null) {
            return "null";
        } else if (type.startsWith("java.util.concurrent")) {
            return type.substring(21);
        } else if (type.startsWith("java.lang.") || type.startsWith("java.util.")) {
            return type.substring(10);
        } else if (type.startsWith("org.apache.camel.support.")) {
            return type.substring(25);
        } else if (type.equals("org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream")) {
            return "WrappedInputStream";
        } else if (type.startsWith("org.apache.camel.converter.stream.")) {
            return type.substring(34);
        } else if (type.equals(
                "org.apache.camel.processor.aggregate.AbstractListAggregationStrategy.GroupedExchangeList")) {
            return "GroupedExchangeList";
        } else if (type.length() > 34) {
            int pos = type.lastIndexOf('.');
            if (pos == -1) {
                pos = type.length() - 34;
            }
            return type.substring(pos + 1);
        }
        return type;
    }

    // ---- File emoji helpers (shared by FilesBrowser, FolderBrowser) ----

    private static final String[] CAMEL_YAML_MARKERS = {
            "- from:", "- route:",
            "- routeTemplate:", "- route-template:",
            "- templatedRoute:", "- templated-route:",
            "- routeConfiguration:", "- route-configuration:",
            "- rest:", "- beans:"
    };

    private static final String[] CAMEL_XML_MARKERS = {
            "<route", "<routes", "<routeTemplate", "<routeTemplates",
            "<templatedRoute", "<templatedRoutes",
            "<rest", "<rests", "<routeConfiguration",
            "<beans", "<blueprint", "<camel"
    };

    static String fileEmoji(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if ("pom.xml".equals(lower)) {
            return detectPomEmoji(path);
        }
        if (lower.endsWith(".kamelet.yaml") || lower.endsWith(".kamelet.yml")
                || lower.endsWith(".camel.yaml") || lower.endsWith(".camel.yml")) {
            // the .camel.yaml / .kamelet.yaml naming convention denotes a Camel file by name, so a
            // freshly created (still empty) file shows the Camel icon without needing content
            return TuiIcons.CAMEL;
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return isCamelYaml(path) ? TuiIcons.CAMEL : TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".xml")) {
            return isCamelXml(path) ? TuiIcons.CAMEL : TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".java")) {
            return isCamelJava(path) ? TuiIcons.CAMEL : TuiIcons.JAVA;
        }
        if (lower.endsWith(".properties") || lower.endsWith(".cfg")) {
            return TuiIcons.DOCUMENT;
        }
        if (lower.endsWith(".json")) {
            return TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".txt")
                || lower.startsWith("readme")) {
            return TuiIcons.README;
        }
        return TuiIcons.DOCUMENT;
    }

    static String fileEmojiByName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".camel.yaml") || lower.endsWith(".camel.yml")
                || lower.endsWith(".kamelet.yaml") || lower.endsWith(".kamelet.yml")) {
            return TuiIcons.CAMEL;
        }
        if (lower.endsWith(".java")) {
            return TuiIcons.JAVA;
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".xml")) {
            return TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".properties") || lower.endsWith(".cfg")) {
            return TuiIcons.DOCUMENT;
        }
        if (lower.endsWith(".json")) {
            return TuiIcons.CLIPBOARD;
        }
        if (lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".txt")
                || lower.startsWith("readme")) {
            return TuiIcons.README;
        }
        return TuiIcons.DOCUMENT;
    }

    static String detectPomRuntime(Path pomFile) {
        try {
            String content = Files.readString(pomFile, StandardCharsets.UTF_8);
            if (content.contains("quarkus-maven-plugin") || content.contains("quarkus-bom")
                    || content.contains("camel-quarkus")) {
                return "quarkus";
            }
            if (content.contains("spring-boot-maven-plugin") || content.contains("spring-boot-starter")
                    || content.contains("camel-spring-boot")) {
                return "spring-boot";
            }
            if (content.contains("camel-bom") || content.contains("camel-maven-plugin")
                    || content.contains("camel-main")) {
                return "main";
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static String detectPomEmoji(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.contains("quarkus-maven-plugin") || content.contains("quarkus-bom")
                    || content.contains("camel-quarkus")) {
                return TuiIcons.QUARKUS;
            }
            if (content.contains("spring-boot-maven-plugin") || content.contains("spring-boot-starter")
                    || content.contains("camel-spring-boot")) {
                return TuiIcons.SPRING_BOOT;
            }
            if (content.contains("camel-core") || content.contains("camel-api")
                    || content.contains("org.apache.camel")) {
                return TuiIcons.CAMEL;
            }
        } catch (IOException e) {
            // ignore
        }
        return TuiIcons.CLIPBOARD;
    }

    static boolean isCamelYaml(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String marker : CAMEL_YAML_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    static boolean isCamelXml(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String marker : CAMEL_XML_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /**
     * Draws the scrollbar of a bordered list on its right border, between the corners, when its rows do not fit: at the
     * list's scroll position, its offset after the render, and not at its selection.
     *
     * @param area   the list with its border
     * @param rows   the rows of all items
     * @param offset the rows the list is scrolled by
     */
    static void renderListScrollbar(Frame frame, Rect area, int rows, int offset, ScrollbarState state) {
        int visibleRows = area.height() - 2;
        if (visibleRows <= 0 || rows <= visibleRows) {
            return;
        }
        Rect track = new Rect(area.x() + area.width() - 1, area.y() + 1, 1, visibleRows);
        state.contentLength(rows).viewportContentLength(visibleRows).position(offset);
        frame.renderStatefulWidget(Scrollbar.builder().build(), track, state);
    }

    /**
     * The item of a TamboUI list at a row of its area, or -1. The offset of a {@code ListState} counts rows, not items,
     * and an item that starts above the area shows its lower rows at the top, so the items are walked by their heights.
     *
     * @param row     the row in the list area, 0 for its first row
     * @param offset  the list's offset after the last render
     * @param heights the rows each item takes
     */
    static int listItemAtRow(int row, int offset, int[] heights) {
        if (row < 0) {
            return -1;
        }
        int target = offset + row;
        int top = 0;
        for (int i = 0; i < heights.length; i++) {
            top += heights[i];
            if (target < top) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The row of a TamboUI table at a line of its data area (below the header), or -1. The offset of a
     * {@code TableState} counts lines, not rows, and the table draws the first row that ends below the offset in full
     * at the top of the area.
     *
     * @param line    the line in the data area, 0 for its first line
     * @param offset  the table's offset after the last render
     * @param heights the lines each row takes
     */
    static int tableRowAtLine(int line, int offset, int[] heights) {
        if (line < 0) {
            return -1;
        }
        int first = 0;
        int end = 0;
        while (first < heights.length && end + heights[first] <= offset) {
            end += heights[first];
            first++;
        }
        int top = 0;
        for (int i = first; i < heights.length; i++) {
            top += heights[i];
            if (line < top) {
                return i;
            }
        }
        return -1;
    }

    static int listItemAt(Rect popup, int offset, int itemCount, int mouseX, int mouseY) {
        if (popup == null) {
            return -1;
        }
        int innerLeft = popup.x() + 1;
        int innerRight = popup.x() + popup.width() - 1;
        int firstRow = popup.y() + 1;
        int lastRow = popup.y() + popup.height() - 1;
        if (mouseX < innerLeft || mouseX >= innerRight) {
            return -1;
        }
        if (mouseY < firstRow || mouseY >= lastRow) {
            return -1;
        }
        int idx = offset + (mouseY - firstRow);
        return idx >= 0 && idx < itemCount ? idx : -1;
    }

    /** Words onto lines of at most the display width; a longer word gets a line of its own. */
    static List<String> wrapWords(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        for (String word : text.split(" ")) {
            int wordWidth = CharWidth.of(word);
            if (line.isEmpty()) {
                line.append(word);
                lineWidth = wordWidth;
            } else if (lineWidth + 1 + wordWidth <= maxWidth) {
                line.append(' ').append(word);
                lineWidth += 1 + wordWidth;
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                lineWidth = wordWidth;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * The rows a line takes in a paragraph with word wrap ({@code Overflow.WRAP_WORD}), counted the way TamboUI 0.6
     * wraps it: at the last whitespace that fits, else after the last '-', '/' or '\', else at the width. A line that
     * starts with spaces continues under its first non-blank character (hanging indent). Callers count the rows to
     * scroll, so a count lower than the paragraph's hides the last rows. Replace with
     * {@code Paragraph.lineCount(width)} once TamboUI has it (tamboui/tamboui#545).
     */
    static int wrappedRows(String text, int width) {
        if (width <= 0 || text == null || CharWidth.of(text) <= width) {
            return 1;
        }
        int indent = 0;
        while (indent < text.length() && text.charAt(indent) == ' ') {
            indent++;
        }
        if (indent == 0 || indent == text.length() || indent >= width) {
            return wordWrapRows(text, width);
        }
        // the first row as usual, the rest of the line under its first non-blank character
        int rest = wrapRowEnd(text, 0, width);
        while (rest < text.length() && Character.isWhitespace(text.charAt(rest))) {
            rest++;
        }
        String continuation = text.substring(rest);
        int continuationWidth = CharWidth.of(continuation);
        if (continuationWidth == 0) {
            return 1;
        }
        return 1 + (continuationWidth <= width - indent ? 1 : wordWrapRows(continuation, width - indent));
    }

    /** The rows a line takes in a paragraph with word wrap; see {@link #wrappedRows(String, int)}. */
    static int wrappedRows(Line line, int width) {
        StringBuilder text = new StringBuilder();
        for (Span span : line.spans()) {
            text.append(span.content());
        }
        return wrappedRows(text.toString(), width);
    }

    private static int wordWrapRows(String text, int width) {
        int rows = 0;
        int pos = 0;
        while (pos < text.length()) {
            int end = wrapRowEnd(text, pos, width);
            rows++;
            pos = end;
            // a row that ends at whitespace: the next row starts after all of it
            if (end < text.length() && Character.isWhitespace(text.codePointBefore(end))) {
                while (pos < text.length() && Character.isWhitespace(text.codePointAt(pos))) {
                    pos += Character.charCount(text.codePointAt(pos));
                }
            }
        }
        return rows;
    }

    /** The index just past the row that starts at {@code start}, when the text wraps at word boundaries. */
    private static int wrapRowEnd(String text, int start, int width) {
        String remaining = text.substring(start);
        int maxEnd = start + CharWidth.substringByWidth(remaining, width).length();
        if (maxEnd >= text.length()) {
            return text.length();
        }
        if (maxEnd == start) {
            // a character wider than the row still takes a row of its own
            return start + GraphemeClusters.clusterEnd(remaining, 0);
        }
        for (int i = text.offsetByCodePoints(maxEnd, -1); i > start; i = text.offsetByCodePoints(i, -1)) {
            if (Character.isWhitespace(text.codePointAt(i))) {
                return i + 1;
            }
        }
        for (int i = text.offsetByCodePoints(maxEnd, -1); i > start; i = text.offsetByCodePoints(i, -1)) {
            char ch = text.charAt(i);
            if (ch == '-' || ch == '/' || ch == '\\') {
                return i + 1;
            }
        }
        return maxEnd;
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            return List.of();
        }
    }

    static String readFirstLine(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    return truncate(trimmed, 60);
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static boolean isCamelJava(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains("RouteBuilder")
                    || content.contains("EndpointRouteBuilder");
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    static void triggerHeapDump(MonitorContext ctx, BiConsumer<String, Boolean> notifyCallback) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        notifyCallback.accept("Writing heap dump...", false);
        String pid = info.pid;
        Thread t = new Thread(() -> {
            JsonObject root = new JsonObject();
            root.put("action", "heap-dump");
            JsonObject jo = ctx.executeAction(pid, root, 60000);
            if (jo != null) {
                String error = jo.getString("error");
                if (error != null) {
                    notifyCallback.accept("Heap dump failed: " + error, true);
                } else {
                    String file = jo.getString("file");
                    long size = jo.getLongOrDefault("size", 0);
                    notifyCallback.accept("Heap dump: " + file + " (" + formatBytes(size) + ")", false);
                }
            } else {
                notifyCallback.accept("Heap dump: no response within 60s", true);
            }
        });
        t.setDaemon(true);
        t.setName("heap-dump-" + pid);
        t.start();
    }

    /**
     * Copies the given text to the system clipboard using the platform's native clipboard command ({@code pbcopy} on
     * macOS, {@code clip} on Windows, {@code xclip} on Linux).
     */
    static void copyToClipboard(String text) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd;
        if (os.contains("mac")) {
            cmd = new String[] { "pbcopy" };
        } else if (os.contains("win")) {
            cmd = new String[] { "clip" };
        } else {
            cmd = new String[] { "xclip", "-selection", "clipboard" };
        }
        Process p = new ProcessBuilder(cmd).start();
        try (java.io.OutputStream out = p.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        try {
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Width (in characters) needed to right-align the largest absolute value in the given numbers.
     */
    static int numWidth(long... values) {
        long max = 0;
        for (long v : values) {
            max = Math.max(max, Math.abs(v));
        }
        return Math.max(1, String.valueOf(max).length());
    }

}
