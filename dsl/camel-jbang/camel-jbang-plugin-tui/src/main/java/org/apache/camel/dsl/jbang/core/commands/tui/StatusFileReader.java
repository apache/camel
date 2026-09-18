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

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Reads the full status document that camel-cli-connector writes for each running integration to
 * {@code ~/.camel/<pid>-status.json}. The tabs show a digest of it; the AI tools and MCP resources hand out whole
 * top-level sections so a model can fetch exactly the piece it needs.
 */
final class StatusFileReader {

    static final String SECTION_LIST = "sections";
    static final int DEFAULT_LOG_LINES = 200;
    static final int MAX_LOG_LINES = 5000;

    private final Path camelDir;

    StatusFileReader(Path camelDir) {
        this.camelDir = camelDir;
    }

    static StatusFileReader defaultReader() {
        return new StatusFileReader(CommandLineHelper.getCamelDir());
    }

    Path statusFile(String pid) {
        return camelDir.resolve(pid + "-status.json");
    }

    Path logFile(String pid) {
        return camelDir.resolve(pid + ".log");
    }

    boolean hasLog(String pid) {
        return pid != null && !pid.isBlank() && Files.isRegularFile(logFile(pid.trim()));
    }

    /**
     * The last {@code lines} lines of the process log ({@code ~/.camel/<pid>.log}), read from the end of the file so a
     * large log is not loaded whole; {@code null} when there is no log file. {@code lines} is clamped to
     * {@link #MAX_LOG_LINES}.
     */
    String tailLog(String pid, int lines) {
        if (!hasLog(pid)) {
            return null;
        }
        int wanted = Math.max(1, Math.min(lines, MAX_LOG_LINES));
        Path file = logFile(pid.trim());
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                return "";
            }
            // read backwards in chunks until the buffer holds more line breaks than lines wanted (or the whole file)
            int chunk = 64 * 1024;
            byte[] tail = new byte[0];
            long position = length;
            while (position > 0 && countNewlines(tail) <= wanted) {
                int size = (int) Math.min(chunk, position);
                position -= size;
                raf.seek(position);
                byte[] merged = new byte[size + tail.length];
                raf.readFully(merged, 0, size);
                System.arraycopy(tail, 0, merged, size, tail.length);
                tail = merged;
            }
            // skip the final line break, then step back over 'wanted' line breaks
            int cut = tail.length;
            if (cut > 0 && tail[cut - 1] == '\n') {
                cut--;
            }
            int seen = 0;
            for (int i = cut - 1; i >= 0; i--) {
                if (tail[i] == '\n' && ++seen == wanted) {
                    return new String(tail, i + 1, tail.length - i - 1, StandardCharsets.UTF_8);
                }
            }
            return new String(tail, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static int countNewlines(byte[] bytes) {
        int count = 0;
        for (byte b : bytes) {
            if (b == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * The parsed document, or {@code null} when there is no readable status file for the pid.
     */
    JsonObject read(String pid) {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        Path file = statusFile(pid.trim());
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return (JsonObject) Jsoner.deserialize(text);
        } catch (Exception e) {
            // the connector rewrites the file every second, a torn read is expected now and then
            return null;
        }
    }

    /**
     * Top-level section names of the document, in file order, or an empty list when it cannot be read.
     */
    List<String> sections(String pid) {
        JsonObject doc = read(pid);
        return doc == null ? List.of() : List.copyOf(doc.keySet());
    }

    /**
     * One top-level section, or {@code null} when the document or the section does not exist.
     */
    Object section(String pid, String section) {
        JsonObject doc = read(pid);
        return doc == null || section == null ? null : doc.get(section);
    }
}
