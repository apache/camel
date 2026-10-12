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
    private int preKeyPosition = -1;

    void start(String title) {
        lines.clear();
        if (title != null && !title.isBlank()) {
            lines.add("# " + title);
            lines.add("");
        }
        lines.add("Set WindowBar Colorful");
        lines.add("Set Width 1200");
        lines.add("Set Height 1200");
        lines.add("Set CursorBlink false");
        lines.add("Set Theme \"Aardvark Blue\"");
        lines.add("");
        startTime = System.currentTimeMillis();
        lastEventTime = startTime;
        keyCount = 0;
        active = true;
    }

    void recordKey(String key) {
        if (!active || key == null) {
            return;
        }
        preKeyPosition = lines.size();
        long now = System.currentTimeMillis();
        long elapsed = now - lastEventTime;
        if (elapsed > 200) {
            lines.add("Sleep " + formatSleep(elapsed));
        }
        String tapeCmd = toTapeCommand(key);
        if (tapeCmd != null) {
            lines.add(tapeCmd);
        } else if (key.codePointCount(0, key.length()) == 1) {
            lines.add("Type \"" + escapeTapeString(key) + "\"");
        }
        keyCount++;
        lastEventTime = now;
    }

    int preKeyPosition() {
        return preKeyPosition;
    }

    void trimTo(int position) {
        if (position >= 0 && position < lines.size()) {
            lines.subList(position, lines.size()).clear();
            lastEventTime = System.currentTimeMillis();
        }
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
            } else if (key.codePointCount(0, key.length()) == 1) {
                charBatch.append(key);
            }
            keyCount++;
        }
        flushCharBatch(charBatch);

        lastEventTime = now + (long) (keys.size() - 1) * delay;
    }

    void recordSleep(long ms) {
        if (!active) {
            return;
        }
        lines.add("Sleep " + formatSleep(ms));
        lastEventTime = System.currentTimeMillis();
    }

    void recordCaption(String text, int durationSeconds) {
        if (!active) {
            return;
        }
        // Normalize: convert real newlines and \n markers to \\n for tape
        // (parseQuotedString interprets \\ as literal \, so \\n → typed \n)
        String normalized = text.replace("\n", "\\n");
        String escaped = normalized.replace("\\", "\\\\").replace("\"", "\\\"");
        lines.add("Ctrl+t");
        lines.add("Sleep 500ms");
        lines.add("Type \"" + escaped + "\"");
        lines.add("Enter");
        if (durationSeconds > 0) {
            lines.add("Sleep " + durationSeconds + "s");
        }
        lastEventTime = System.currentTimeMillis();
    }

    void resetClock() {
        if (!active) {
            return;
        }
        lastEventTime = System.currentTimeMillis();
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

    /**
     * The tape command of a key label (see {@link RecordingManager#keyLabel}) or a key given to tui_send_keys: Ctrl+,
     * Alt+ and Shift+ in that order, then the key's tape name (Up, PageDown, F8, Backspace) or the character. Null for
     * a single character typed without Ctrl or Alt, which is written as Type, and for a label it does not know.
     */
    static String toTapeCommand(String key) {
        if ("⇧Tab".equals(key)) {
            return "Shift+Tab";
        }
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        String rest = key;
        int plus;
        // a lone "+" is a key of its own, not a modifier separator
        while ((plus = rest.indexOf('+')) > 0 && plus < rest.length() - 1) {
            String modifier = rest.substring(0, plus).toLowerCase(Locale.ROOT);
            if ("ctrl".equals(modifier) || "control".equals(modifier)) {
                ctrl = true;
            } else if ("alt".equals(modifier) || "meta".equals(modifier)) {
                alt = true;
            } else if ("shift".equals(modifier)) {
                shift = true;
            } else {
                return null;
            }
            rest = rest.substring(plus + 1);
        }
        String name = tapeKeyName(rest);
        if (name == null) {
            if (rest.codePointCount(0, rest.length()) != 1 || !(ctrl || alt || shift)) {
                return null;
            }
            name = rest;
        }
        return (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + (shift ? "Shift+" : "") + name;
    }

    /** The tape name of a key such as Enter, an arrow or F8, or null when it is a character or no key. */
    private static String tapeKeyName(String key) {
        if (key.length() == 1) {
            return switch (key) {
                case TuiIcons.ARROW_UP -> "Up";
                case TuiIcons.ARROW_DOWN -> "Down";
                case TuiIcons.KEY_LEFT -> "Left";
                case TuiIcons.KEY_RIGHT -> "Right";
                case "⌫" -> "Backspace";
                default -> null;
            };
        }
        String lower = key.toLowerCase(Locale.ROOT);
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
