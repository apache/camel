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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The WARN and ERROR lines of the log of each integration per second, for the Timeline (CAMEL-26691): a cause when
 * something went wrong without an exception in an exchange. Read from the log file the TUI already shows, as it grows:
 * the last part of it at first, then what is added at each refresh. A line without a time of its own (a stack trace) is
 * part of the line before and not counted.
 */
final class TimelineLog {

    private static final Logger LOG = LoggerFactory.getLogger(TimelineLog.class);

    // how much of a log is read the first time: the lines of the last minutes, not the whole file
    static final int FIRST_READ = 256 * 1024;
    // at most this much is read at a refresh, so a log that grows fast does not hold it up
    private static final int MAX_READ = 1024 * 1024;
    private static final Pattern LINE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})\\S*\\s+(WARN|ERROR)\\b");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // where the log of each integration was read to
    private final Map<String, Long> offsets = new HashMap<>();
    private final ZoneId zone;

    TimelineLog() {
        this(ZoneId.systemDefault());
    }

    TimelineLog(ZoneId zone) {
        this.zone = zone;
    }

    /** Reads what was added to the log of the integration since the last time, into the timeline. */
    void read(String pid, Path file, Timeline timeline) {
        if (pid == null || file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();
            Long known = offsets.get(pid);
            // the first time from the last part; a log that got shorter was rolled or cleared: from its start
            long from = known == null ? Math.max(0, size - FIRST_READ) : known > size ? 0 : known;
            long to = Math.min(size, from + MAX_READ);
            if (to <= from) {
                offsets.put(pid, from);
                return;
            }
            byte[] bytes = new byte[(int) (to - from)];
            raf.seek(from);
            raf.readFully(bytes);
            // only whole lines: the rest is read the next time
            int end = bytes.length;
            while (end > 0 && bytes[end - 1] != '\n') {
                end--;
            }
            if (end == 0 && to < size) {
                // a line longer than the read: skip it
                end = bytes.length;
            }
            String text = new String(bytes, 0, end, StandardCharsets.UTF_8);
            // read from the middle of the file: the first line is a part of one
            int start = known == null && from > 0 ? text.indexOf('\n') + 1 : 0;
            for (String line : text.substring(Math.max(0, start)).split("\n")) {
                count(pid, line, timeline);
            }
            offsets.put(pid, from + end);
        } catch (IOException e) {
            // the log is gone or cannot be read: nothing to count
            LOG.debug("Cannot read the log of the integration with pid {} for the timeline: {}", pid, e.getMessage());
        }
    }

    private void count(String pid, String line, Timeline timeline) {
        if (line.length() < 20) {
            return;
        }
        String plain = line.indexOf('\u001B') >= 0 ? ANSI.matcher(line).replaceAll("") : line;
        Matcher m = LINE.matcher(plain);
        if (m.find()) {
            try {
                long second = LocalDateTime.parse(m.group(1) + " " + m.group(2), TIME).atZone(zone).toEpochSecond();
                timeline.logLine(pid, second, "ERROR".equals(m.group(3)));
            } catch (DateTimeParseException e) {
                // not a time: not a line of the log format
            }
        }
    }

    void retain(Set<String> pids) {
        offsets.keySet().retainAll(pids);
    }
}
