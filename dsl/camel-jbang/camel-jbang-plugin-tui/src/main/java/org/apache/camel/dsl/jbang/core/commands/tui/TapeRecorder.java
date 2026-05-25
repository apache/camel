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
import java.util.List;
import java.util.Locale;
import java.util.Set;

class TapeRecorder {

    private static final Set<String> SPECIAL_KEYS = Set.of(
            "enter", "tab", "space", "backspace", "delete", "escape", "esc",
            "up", "down", "left", "right", "home", "end", "pageup", "pagedown",
            "pgup", "pgdn");

    private final List<String> lines = new ArrayList<>();
    private long startTime;
    private long lastEventTime;
    private int keyCount;
    private boolean active;

    void start(String title) {
        lines.clear();
        if (title != null && !title.isBlank()) {
            lines.add("# " + title);
            lines.add("");
        }
        startTime = System.currentTimeMillis();
        lastEventTime = startTime;
        keyCount = 0;
        active = true;
    }

    void recordKey(String key) {
        if (!active || key == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastEventTime;
        if (elapsed > 200) {
            lines.add("Sleep " + formatSleep(elapsed));
        }
        String tapeCmd = toTapeCommand(key);
        if (tapeCmd != null) {
            lines.add(tapeCmd);
        } else if (key.length() == 1) {
            lines.add("Type \"" + escapeTapeString(key) + "\"");
        }
        keyCount++;
        lastEventTime = now;
    }

    void recordKeys(List<String> keys, int delay) {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastEventTime;
        if (elapsed > 200) {
            lines.add("Sleep " + formatSleep(elapsed));
        }

        StringBuilder charBatch = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (i > 0 && delay > 0) {
                flushCharBatch(charBatch);
                lines.add("Sleep " + delay + "ms");
            }
            String tapeCmd = toTapeCommand(key);
            if (tapeCmd != null) {
                flushCharBatch(charBatch);
                lines.add(tapeCmd);
            } else if (key.length() == 1) {
                charBatch.append(key);
            }
            keyCount++;
        }
        flushCharBatch(charBatch);

        lastEventTime = now + (long) (keys.size() - 1) * delay;
    }

    String stop() {
        active = false;
        return String.join("\n", lines) + "\n";
    }

    boolean isActive() {
        return active;
    }

    int getKeyCount() {
        return keyCount;
    }

    long getDurationMs() {
        return lastEventTime - startTime;
    }

    private void flushCharBatch(StringBuilder batch) {
        if (!batch.isEmpty()) {
            lines.add("Type \"" + escapeTapeString(batch.toString()) + "\"");
            batch.setLength(0);
        }
    }

    static String toTapeCommand(String key) {
        if (key.length() == 1) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.startsWith("ctrl+")) {
            return "Ctrl+" + key.substring(5);
        }
        if (lower.startsWith("shift+")) {
            return "Shift+" + key.substring(6);
        }

        if (SPECIAL_KEYS.contains(lower)) {
            return switch (lower) {
                case "esc" -> "Escape";
                case "pgup", "pageup" -> "PageUp";
                case "pgdn", "pagedown" -> "PageDown";
                default -> capitalize(lower);
            };
        }

        if (lower.matches("f\\d{1,2}")) {
            return key.toUpperCase(Locale.ROOT);
        }

        return null;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    static String formatSleep(long ms) {
        if (ms >= 1000 && ms % 1000 == 0) {
            return (ms / 1000) + "s";
        }
        if (ms >= 1000) {
            double seconds = ms / 1000.0;
            String formatted = String.format(Locale.ROOT, "%.1f", seconds);
            return formatted + "s";
        }
        long rounded = (ms / 100) * 100;
        return Math.max(100, rounded) + "ms";
    }

    private static String escapeTapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
