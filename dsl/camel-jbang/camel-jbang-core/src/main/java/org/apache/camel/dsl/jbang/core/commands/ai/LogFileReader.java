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
        return build(lines, limit, filter, level, result);
    }

    /** Groups raw lines into records and filters them, newest first; visible for tests. */
    static JsonObject build(List<String> lines, int limit, String filter, String level, JsonObject result) {
        List<JsonObject> records = toRecords(lines);
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
            rows.add(r);
        }
        result.put("lines", rows);
        result.put("totalLines", lines.size());
        result.put("returnedLines", rows.size());
        return result;
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
        for (String raw : lines) {
            String line = ANSI.matcher(raw).replaceAll("");
            Matcher m = LOG_PATTERN.matcher(line);
            boolean matches = m.matches();
            if (!matches && head != null) {
                if (detail.size() < MAX_DETAIL_LINES) {
                    detail.add(line);
                } else {
                    hidden++;
                }
                continue;
            }
            if (head != null) {
                records.add(finish(head, detail, hidden));
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
        }
        if (head != null) {
            records.add(finish(head, detail, hidden));
        }
        return records;
    }

    private static JsonObject finish(JsonObject head, List<String> detail, int hidden) {
        if (!detail.isEmpty()) {
            String text = String.join("\n", detail);
            if (hidden > 0) {
                text += "\n... " + hidden + " more lines";
            }
            head.put("detail", text);
        }
        return head;
    }
}
