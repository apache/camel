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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ERRORs on the screen of the Log tab, for fix with AI (Shift+F8): each ERROR line with the lines that follow it
 * (the Message History and the stack trace of a failed exchange), the same error repeated counted once, newest first.
 * The source line of a failed exchange comes from its Message History, whose last row is the step that failed.
 */
final class LogErrors {

    private static final Pattern EXCEPTION
            = Pattern
                    .compile("^(?:Caused by:\\s*)?((?:[a-zA-Z_$][\\w$]*\\.)+([A-Z][\\w$]*(?:Exception|Error|Throwable)))(.*)$");
    private static final Pattern HISTORY_ROW = Pattern.compile("^(\\S+:\\d+)\\s+\\S+/\\S+\\s");
    // a logger named after the source location (the log EIP in dev mode: orders.camel.yaml:44)
    private static final Pattern LOGGER_SOURCE
            = Pattern.compile("\\s(\\S+\\.(?:yaml|yml|xml|java|groovy|kts|kt|js):\\d+)\\s+:\\s");
    private static final Pattern NOISE = Pattern.compile("[0-9A-Fa-f]{8,}(?:-[0-9A-Fa-f]+)*|\\d+");
    private static final int MAX_CAUSES = 3;
    private static final int MAX_TEXT = 300;

    /**
     * An ERROR of the log.
     *
     * @param time      when it last happened
     * @param logger    the logger
     * @param message   the message of the ERROR line
     * @param exception the exception (simple class name and message), or null
     * @param causes    the Caused by lines, at most three
     * @param source    the source location (file:line) of the step that failed, from the Message History, or null
     * @param count     how many times it is on the screen
     */
    record LogError(String time, String logger, String message, String exception, List<String> causes, String source,
            int count) {

        /** One line for the pick list: count, time and what failed. */
        String label() {
            String what = exception != null ? exception : message;
            return (count > 1 ? "x" + count + "  " : "") + time + "  " + what;
        }

        /** The failure for the AI on a source line: the exception from the log, and how often. */
        String failure() {
            String what = exception != null ? exception : message;
            return "ERROR in the log" + (count > 1 ? " (" + count + " times)" : "") + ": " + what;
        }

        /** The error for the AI when its source line is not known: the ERROR line, the exception and its causes. */
        String text() {
            StringBuilder sb = new StringBuilder();
            sb.append(time).append(" ERROR ").append(logger != null ? logger + ": " : "").append(message);
            if (exception != null) {
                sb.append('\n').append(exception);
            }
            for (String c : causes) {
                sb.append('\n').append(c);
            }
            if (count > 1) {
                sb.append("\n(").append(count).append(" times on the screen)");
            }
            return sb.toString();
        }
    }

    private LogErrors() {
    }

    /**
     * The distinct ERRORs on the screen, newest first.
     *
     * @param entries the log entries of the tab
     * @param start   the index of the first entry on the screen
     * @param count   how many entries are on the screen
     */
    static List<LogError> inView(List<LogEntry> entries, int start, int count) {
        if (entries == null || entries.isEmpty() || count <= 0) {
            return List.of();
        }
        int from = Math.max(0, Math.min(start, entries.size() - 1));
        // an ERROR whose stack trace is on the screen but not its first line
        while (from > 0 && isContinuation(entries.get(from))) {
            from--;
        }
        int to = Math.min(entries.size(), Math.max(start, 0) + count);
        // the rest of the last ERROR on the screen (its Message History and stack trace)
        while (to < entries.size() && isContinuation(entries.get(to))) {
            to++;
        }
        Map<String, LogError> distinct = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            LogEntry head = entries.get(i);
            if (isContinuation(head) || !"ERROR".equalsIgnoreCase(head.level)) {
                continue;
            }
            List<String> detail = new ArrayList<>();
            for (int j = i + 1; j < entries.size() && isContinuation(entries.get(j)); j++) {
                detail.add(textOf(entries.get(j)));
            }
            LogError error = errorOf(head, detail);
            String key = keyOf(error);
            LogError seen = distinct.remove(key);
            int times = (seen != null ? seen.count() : 0) + Math.max(1, head.repeat);
            distinct.put(key, new LogError(
                    error.time(), error.logger(), error.message(), error.exception(),
                    error.causes(), error.source(), times));
        }
        List<LogError> answer = new ArrayList<>(distinct.values());
        // newest first: the one last put is the last on the screen
        Collections.reverse(answer);
        return answer;
    }

    static LogError errorOf(LogEntry head, List<String> detail) {
        String exception = null;
        List<String> causes = new ArrayList<>();
        String source = null;
        boolean history = false;
        for (String line : detail) {
            String s = line.strip();
            if (s.equals("Message History")) {
                history = true;
                continue;
            }
            if (history) {
                Matcher row = HISTORY_ROW.matcher(s);
                if (row.find()) {
                    // the last row is the step that failed
                    source = row.group(1);
                    continue;
                }
                if (s.equals("Stacktrace")) {
                    history = false;
                }
            }
            Matcher m = EXCEPTION.matcher(s);
            if (m.matches()) {
                String simple = shorten(m.group(2) + m.group(3));
                if (s.startsWith("Caused by:")) {
                    if (causes.size() < MAX_CAUSES) {
                        causes.add("Caused by: " + simple);
                    }
                } else if (exception == null) {
                    exception = simple;
                }
            }
        }
        if (exception == null) {
            // an exception in the ERROR line itself
            Matcher m = EXCEPTION.matcher(head.message != null ? head.message.strip() : "");
            if (m.matches()) {
                exception = shorten(m.group(2) + m.group(3));
            }
        }
        if (source == null && head.raw != null) {
            Matcher m = LOGGER_SOURCE.matcher(TuiHelper.stripAnsi(head.raw));
            if (m.find()) {
                source = m.group(1);
            }
        }
        return new LogError(
                head.time, head.logger, shorten(head.message != null ? head.message : ""), exception,
                causes, source, Math.max(1, head.repeat));
    }

    /** The same error repeated: the logger and what failed, without the ids, counters and times in them. */
    static String keyOf(LogError error) {
        String what = error.exception() != null ? error.exception() : error.message();
        return error.logger() + "|" + NOISE.matcher(what != null ? what : "").replaceAll("#");
    }

    private static boolean isContinuation(LogEntry e) {
        return e.time == null || e.time.isEmpty();
    }

    private static String textOf(LogEntry e) {
        if (e.message != null && !e.message.isEmpty()) {
            return e.message;
        }
        return e.raw != null ? TuiHelper.stripAnsi(e.raw) : "";
    }

    private static String shorten(String s) {
        String t = s.strip();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) + "..." : t;
    }
}
