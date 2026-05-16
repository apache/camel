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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared utility methods for TUI commands.
 */
final class TuiHelper {

    private TuiHelper() {
    }

    /**
     * Eagerly load classes used by the TUI input reader daemon thread and picocli post-processing. Without this, during
     * JVM shutdown the classloader may already be closing while the input reader thread is still trying to load these
     * classes lazily — causing ClassNotFoundException stack traces on exit.
     */
    static void preloadClasses(ClassLoader cl) {
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyModifiers", cl);
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyEvent", cl);
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyCode", cl);
        ObjectHelper.loadClass("picocli.CommandLine$IExitCodeGenerator", cl);
    }

    /**
     * Find PIDs of running Camel integrations matching the given name pattern.
     */
    static List<Long> findPids(String name, Function<String, Path> statusFileResolver) {
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid(), statusFileResolver);
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
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

    /**
     * Truncate a string to max length, appending an ellipsis if truncated.
     */
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
     * Strip ANSI escape sequences and carriage returns from a log line. Handles CSI sequences (\e[...finalChar where
     * finalChar is any char in @-~), 2-char Fe/Fp escape sequences, and \r.
     */
    static String stripAnsi(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        int i = 0;
        while (i < line.length()) {
            char ch = line.charAt(i);
            if (ch == '\u001B' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (next == '[') {
                    // CSI sequence: \e[ + params/intermediates + final byte (any char @-~)
                    i += 2;
                    while (i < line.length() && (line.charAt(i) < '@' || line.charAt(i) > '~')) {
                        i++;
                    }
                    i++; // skip the final byte
                } else if (next >= '@' && next <= '_') {
                    // 2-char Fe/Fp escape sequence (e.g. \eM, \e7, \e8)
                    i += 2;
                } else {
                    i++; // unrecognised, skip just the ESC
                }
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
     * Parse a raw log line (which may contain ANSI SGR color codes) into a TamboUI {@link Line} of styled
     * {@link Span}s, skipping the first {@code hSkip} display-width columns (for horizontal scrolling). Color state is
     * tracked through the skipped region so colors that start before the visible area still apply correctly.
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
            if (ch == '\u001B' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '[') {
                    i += 2;
                    int seqStart = i;
                    while (i < raw.length() && (raw.charAt(i) < '@' || raw.charAt(i) > '~')) {
                        i++;
                    }
                    char finalByte = i < raw.length() ? raw.charAt(i) : 0;
                    i++;
                    if (finalByte == 'm') {
                        if (text.length() > 0) {
                            spans.add(Span.styled(text.toString(), style));
                            text.setLength(0);
                        }
                        style = applySgr(style, raw.substring(seqStart, i - 1));
                    }
                } else if (next >= '@' && next <= '_') {
                    i += 2;
                } else {
                    i++;
                }
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

    private static Style applySgr(Style style, String params) {
        if (params.isEmpty()) {
            return Style.EMPTY;
        }
        String[] parts = params.split(";", -1);
        int j = 0;
        while (j < parts.length) {
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
                case 22 -> style = style.notBold().notDim();
                case 23 -> style = style.notItalic();
                case 24 -> style = style.notUnderlined();
                case 30 -> style = style.fg(Color.ansi(AnsiColor.BLACK));
                case 31 -> style = style.fg(Color.ansi(AnsiColor.RED));
                case 32 -> style = style.fg(Color.ansi(AnsiColor.GREEN));
                case 33 -> style = style.fg(Color.ansi(AnsiColor.YELLOW));
                case 34 -> style = style.fg(Color.ansi(AnsiColor.BLUE));
                case 35 -> style = style.fg(Color.ansi(AnsiColor.MAGENTA));
                case 36 -> style = style.fg(Color.ansi(AnsiColor.CYAN));
                case 37 -> style = style.fg(Color.ansi(AnsiColor.WHITE));
                case 38 -> {
                    if (j + 2 < parts.length && "5".equals(parts[j + 1])) {
                        try {
                            style = style.fg(Color.indexed(Integer.parseInt(parts[j + 2])));
                            j += 2;
                        } catch (NumberFormatException e) {
                            /* skip */ }
                    } else if (j + 4 < parts.length && "2".equals(parts[j + 1])) {
                        try {
                            style = style.fg(Color.rgb(
                                    Integer.parseInt(parts[j + 2]),
                                    Integer.parseInt(parts[j + 3]),
                                    Integer.parseInt(parts[j + 4])));
                            j += 4;
                        } catch (NumberFormatException e) {
                            /* skip */ }
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
                case 48 -> {
                    if (j + 2 < parts.length && "5".equals(parts[j + 1])) {
                        try {
                            style = style.bg(Color.indexed(Integer.parseInt(parts[j + 2])));
                            j += 2;
                        } catch (NumberFormatException e) {
                            /* skip */ }
                    } else if (j + 4 < parts.length && "2".equals(parts[j + 1])) {
                        try {
                            style = style.bg(Color.rgb(
                                    Integer.parseInt(parts[j + 2]),
                                    Integer.parseInt(parts[j + 3]),
                                    Integer.parseInt(parts[j + 4])));
                            j += 4;
                        } catch (NumberFormatException e) {
                            /* skip */ }
                    }
                }
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
}
