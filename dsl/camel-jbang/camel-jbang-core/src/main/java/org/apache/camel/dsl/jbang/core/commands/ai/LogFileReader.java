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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Reads the tail of an integration's log file ({@code ~/.camel/<pid>.log}, which {@code camel run} writes) as
 * structured records, newest first, the way the Camel TUI's Log tab hands it to an AI agent: a stack trace or wrapped
 * text is one record with a {@code detail} block, not fifty lines that all claim to be INFO.
 */
public final class LogFileReader {

    /** Continuation lines kept per log record before the rest is summarised as a count. */
    static final int MAX_DETAIL_LINES = 20;

    /** How much of the end of the file is read; a limit of a few hundred records fits with room to spare. */
    private static final int TAIL_BYTES = 512 * 1024;

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                               + "\\d+\\s+---\\s+"
                                                               + "\\[([^]]*)]\\s+"
                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private static final Pattern ANSI = Pattern.compile("\\u001b\\[[;\\d]*[ -/]*[@-~]");

    /** A line of a stack trace or of the error handler's message history: what is left out without details. */
    private static final Pattern TRACE_LINE = Pattern.compile("^\\s+at \\S+\\(.*\\)$|^Caused by: |^\\s*\\.\\.\\. \\d+ more$"
                                                              + "|^Message History|^Stacktrace$");

    /** The line that names an exception and its message: {@code java.net.ConnectException: supplier unreachable}. */
    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^(?:Caused by: )?((?:[a-zA-Z_$][\\w$]*\\.)+[\\w$]*(?:Exception|Error|Throwable)\\b.*)$");

    /** A frame of a stack trace: {@code at org.example.OrderBean.process(OrderBean.java:42)}. */
    private static final Pattern FRAME = Pattern.compile("^\\tat (\\S+\\(.*\\))$");

    /** Frames of the runtime, not of the user's code: Camel, the JDK, Groovy, Vert.x and the like. */
    private static final List<String> RUNTIME_PACKAGES = List.of(
            "org.apache.camel.", "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "groovy.",
            "org.codehaus.groovy.", "org.apache.groovy.", "io.vertx.", "io.netty.", "io.smallrye.", "io.quarkus.",
            "org.springframework.", "com.fasterxml.", "org.jboss.", "kotlin.");

    /** Internal fields of a record, removed before it is returned. */
    private static final String TRACE = "_trace";
    private static final String CAUSE = "_cause";
    private static final String ORIGIN = "_origin";
    private static final String DETAIL_LINES = "_detailLines";

    private LogFileReader() {
    }

    /** The log file of a process: {@code <pid>.log}, or {@code <name>.log} when the pid file does not exist. */
    public static Path logFile(long pid, String name) {
        Path dir = CommandLineHelper.getCamelDir();
        Path pidLog = dir.resolve(pid + ".log");
        if (!Files.exists(pidLog) && name != null && !name.isBlank()) {
            Path nameLog = dir.resolve(name + ".log");
            if (Files.exists(nameLog)) {
                return nameLog;
            }
        }
        return pidLog;
    }

    /**
     * @param  pid    the process
     * @param  name   the integration name, for the log file fallback
     * @param  limit  maximum records to return
     * @param  filter case-insensitive substring the message or detail must contain; null for all
     * @param  level  only records of this level (INFO, WARN, ERROR, DEBUG, TRACE); null for all
     * @return        lines (newest first), totalLines and returnedLines, or an error when there is no log file
     */
    public static JsonObject read(long pid, String name, int limit, String filter, String level) {
        return read(pid, name, limit, filter, level, true);
    }

    /**
     * @param  pid     the process
     * @param  name    the integration name, for the log file fallback
     * @param  limit   maximum records to return
     * @param  filter  case-insensitive substring the message or detail must contain; null for all
     * @param  level   only records of this level (INFO, WARN, ERROR, DEBUG, TRACE); null for all
     * @param  details whether a stack trace stays in its record; without, an error keeps its first line, the number of
     *                 lines left out and the exception as {@code cause} when the first line does not name it
     * @return         lines (newest first), totalLines and returnedLines, or an error when there is no log file
     */
    public static JsonObject read(long pid, String name, int limit, String filter, String level, boolean details) {
        Path file = logFile(pid, name);
        JsonObject result = new JsonObject();
        result.put("file", file.toString());
        if (!Files.isRegularFile(file)) {
            result.put("error", "No log file " + file + " (an integration started with camel run writes one;"
                                + " Spring Boot and Quarkus applications log to their own console)");
            return result;
        }
        List<String> lines;
        try {
            lines = tail(file);
        } catch (IOException e) {
            result.put("error", "Cannot read " + file + ": " + e.getMessage());
            return result;
        }
        return build(lines, limit, filter, level, details, result);
    }

    /** Groups raw lines into records and filters them, newest first, stack traces included; visible for tests. */
    static JsonObject build(List<String> lines, int limit, String filter, String level, JsonObject result) {
        return build(lines, limit, filter, level, true, result);
    }

    /**
     * Groups raw lines into records and filters them, newest first; visible for tests. Without details a stack trace is
     * left out of its record (CAMEL-25296): an error handler record is about forty lines of message history and stack
     * trace behind a first line that already names the route, the source line and the exception, and camel_get_errors
     * has the stack trace of every failed exchange. Other multi-line text, such as a pretty-printed body, is kept.
     */
    static JsonObject build(
            List<String> lines, int limit, String filter, String level, boolean details, JsonObject result) {
        List<JsonObject> records = fold(toRecords(lines));
        String needle = filter == null || filter.isBlank() ? null : filter.toLowerCase();
        JsonArray rows = new JsonArray();
        for (int i = records.size() - 1; i >= 0 && rows.size() < limit; i--) {
            JsonObject r = records.get(i);
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(r.getString("level"))) {
                continue;
            }
            if (needle != null) {
                String message = r.getStringOrDefault("message", "").toLowerCase();
                String detail = r.getStringOrDefault("detail", "").toLowerCase();
                if (!message.contains(needle) && !detail.contains(needle)) {
                    continue;
                }
            }
            rows.add(details ? r : withoutTrace(r));
        }
        int left = 0;
        for (Object o : rows) {
            JsonObject r = (JsonObject) o;
            if (r.containsKey("detailLines")) {
                left++;
            }
            r.remove(TRACE);
            r.remove(CAUSE);
            r.remove(ORIGIN);
            r.remove(DETAIL_LINES);
        }
        result.put("lines", rows);
        result.put("totalLines", lines.size());
        result.put("returnedLines", rows.size());
        if (left > 0) {
            result.put("note", "The stack traces of " + left + " record(s) are left out (detailLines says how many lines):"
                               + " details=true returns them, camel_get_errors has the failed exchanges.");
        }
        return result;
    }

    private static JsonObject withoutTrace(JsonObject r) {
        if (!Boolean.TRUE.equals(r.get(TRACE))) {
            return r;
        }
        r.remove("detail");
        r.put("detailLines", r.get(DETAIL_LINES));
        Object cause = r.get(CAUSE);
        if (cause instanceof String c && !r.getStringOrDefault("message", "").contains(c)) {
            r.put("cause", c);
        }
        if (r.get(ORIGIN) instanceof String origin) {
            r.put("at", origin);
        }
        return r;
    }

    private static List<String> tail(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            long start = Math.max(0, length - TAIL_BYTES);
            raf.seek(start);
            byte[] buf = new byte[(int) (length - start)];
            raf.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            int from = 0;
            if (start > 0) {
                // the first line is most likely cut in the middle
                from = text.indexOf('\n') + 1;
            }
            for (String line : text.substring(from).split("\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private static List<JsonObject> toRecords(List<String> lines) {
        List<JsonObject> records = new ArrayList<>();
        JsonObject head = null;
        List<String> detail = null;
        int hidden = 0;
        boolean trace = false;
        String cause = null;
        // per exception of the trace, outermost first: the first frame of the user's code in it, or null
        List<String> origins = new ArrayList<>();
        for (String raw : lines) {
            String line = ANSI.matcher(raw).replaceAll("");
            Matcher m = LOG_PATTERN.matcher(line);
            boolean matches = m.matches();
            if (!matches && head != null) {
                // the whole block counts for what it is and what threw, also past the lines that are kept
                trace |= TRACE_LINE.matcher(line).find();
                Matcher ex = EXCEPTION_LINE.matcher(line);
                if (ex.matches() && (cause == null || line.startsWith("Caused by: "))) {
                    // the first exception line, unless a Caused by follows: the last one is the root cause
                    cause = ex.group(1);
                    origins.add(null);
                } else if (!origins.isEmpty() && origins.get(origins.size() - 1) == null) {
                    // a frame directly under its exception (a Suppressed block is indented further and skipped)
                    Matcher frame = FRAME.matcher(line);
                    if (frame.matches()) {
                        String f = withoutModule(frame.group(1));
                        if (isUserCode(f)) {
                            origins.set(origins.size() - 1, f);
                        }
                    }
                }
                if (detail.size() < MAX_DETAIL_LINES) {
                    detail.add(line);
                } else {
                    hidden++;
                }
                continue;
            }
            if (head != null) {
                records.add(finish(head, detail, hidden, trace, cause, origin(origins)));
            }
            head = new JsonObject();
            if (matches) {
                String time = m.group(2);
                head.put("time", time.length() > 12 ? time.substring(0, 12) : time);
                head.put("level", m.group(3));
                String logger = m.group(5);
                int lastDot = logger.lastIndexOf('.');
                head.put("logger", lastDot > 0 ? logger.substring(lastDot + 1) : logger);
                head.put("message", m.group(6));
            } else {
                head.put("time", "");
                head.put("level", "INFO");
                head.put("message", line);
            }
            detail = new ArrayList<>();
            hidden = 0;
            trace = false;
            cause = null;
            origins = new ArrayList<>();
        }
        if (head != null) {
            records.add(finish(head, detail, hidden, trace, cause, origin(origins)));
        }
        return records;
    }

    /**
     * Folds a run of identical records into one with a {@code repeated} count and the time of its first occurrence, so
     * a storm of the same failure is one line to read instead of hundreds that push everything else out of the window
     * (CAMEL-24911).
     */
    private static List<JsonObject> fold(List<JsonObject> records) {
        List<JsonObject> folded = new ArrayList<>();
        for (JsonObject r : records) {
            JsonObject last = folded.isEmpty() ? null : folded.get(folded.size() - 1);
            if (last != null && sameRecord(last, r)) {
                // keep the newest occurrence, and remember when the run started and how long it is
                String firstTime = last.getStringOrDefault("firstTime", last.getStringOrDefault("time", ""));
                long repeated = last.getLongOrDefault("repeated", 1) + 1;
                r.put("firstTime", firstTime);
                r.put("repeated", repeated);
                folded.set(folded.size() - 1, r);
            } else {
                folded.add(r);
            }
        }
        return folded;
    }

    private static boolean sameRecord(JsonObject a, JsonObject b) {
        return Objects.equals(a.get("level"), b.get("level"))
                && Objects.equals(a.get("logger"), b.get("logger"))
                && Objects.equals(a.get("message"), b.get("message"))
                && Objects.equals(a.get("detail"), b.get("detail"));
    }

    /**
     * Where it went wrong in the user's code: the root cause is printed last, so walk from the bottom. A Caused by
     * section ends with "... N more" for the frames it shares with the exception above it, which may be the very frame
     * of the user's code, so a section without one hands over to the one above.
     */
    private static String origin(List<String> origins) {
        for (int i = origins.size() - 1; i >= 0; i--) {
            if (origins.get(i) != null) {
                return origins.get(i);
            }
        }
        return null;
    }

    /**
     * A frame without its module or class loader: {@code java.base/java.lang.Thread.run(..)},
     * {@code app//org.example..}.
     */
    private static String withoutModule(String frame) {
        int paren = frame.indexOf('(');
        int slash = frame.lastIndexOf('/', paren < 0 ? frame.length() : paren);
        return slash < 0 ? frame : frame.substring(slash + 1);
    }

    private static boolean isUserCode(String frame) {
        for (String p : RUNTIME_PACKAGES) {
            if (frame.startsWith(p)) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject finish(
            JsonObject head, List<String> detail, int hidden, boolean trace, String cause, String origin) {
        if (!detail.isEmpty()) {
            if (trace) {
                head.put(TRACE, Boolean.TRUE);
                head.put(DETAIL_LINES, detail.size() + hidden);
                if (cause != null) {
                    head.put(CAUSE, cause);
                }
                if (origin != null) {
                    head.put(ORIGIN, origin);
                }
            }
            String text = String.join("\n", detail);
            if (hidden > 0) {
                text += "\n... " + hidden + " more lines";
            }
            head.put("detail", text);
        }
        return head;
    }
}
