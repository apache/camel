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

import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;

class TapeRecorder {

    /** The pause between the steps of a {@code Drag} when the tape gives none, as in TamboUI. */
    static final long DRAG_STEP_MILLIS = 30;

    private static final Set<String> SPECIAL_KEYS = Set.of(
            "enter", "tab", "space", "backspace", "delete", "escape", "esc",
            "up", "down", "left", "right", "home", "end", "pageup", "pagedown",
            "pgup", "pgdn");

    private final List<String> lines = new ArrayList<>();
    // MonotonicClock times: the sleeps between the recorded events
    private long startTime;
    private long lastEventTime;
    private int keyCount;
    private boolean active;
    private int preKeyPosition = -1;
    // the index of the line after the Set lines, where stop() notes the screen size of the mouse commands
    private int headerEnd;
    // a button pressed and not released yet: its release writes it as a click or a drag
    private MouseEvent pressed;
    private long pressedAt;
    private boolean dragged;
    // a MouseDown was written for a press that something else came before the release of
    private boolean buttonHeld;
    // the last line is a scroll, which the next scroll in the same direction at the same cell adds to
    private int scrollLine = -1;
    private MouseEvent scroll;
    private int scrollCount;
    private int screenWidth;
    private int screenHeight;

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
        headerEnd = lines.size();
        pressed = null;
        buttonHeld = false;
        scrollLine = -1;
        startTime = MonotonicClock.millis();
        lastEventTime = startTime;
        keyCount = 0;
        active = true;
    }

    void recordKey(String key) {
        if (!active || key == null) {
            return;
        }
        flushPress();
        preKeyPosition = lines.size();
        long now = MonotonicClock.millis();
        addSleep(now);
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
        // the press of a click that closed the menu goes too, and its release is not written
        pressed = null;
        if (position >= 0 && position < lines.size()) {
            lines.subList(position, lines.size()).clear();
            lastEventTime = MonotonicClock.millis();
        }
        if (scrollLine >= lines.size()) {
            scrollLine = -1;
        }
    }

    /**
     * Records a mouse event of the user, in the tape commands of TamboUI: a press and its release are a {@code Click},
     * {@code RightClick} or {@code MiddleClick}, or a {@code Drag} when the mouse moved in between, and the wheel is
     * {@code ScrollUp}, {@code ScrollDown}, {@code ScrollLeft} or {@code ScrollRight} with a count. Moving the mouse
     * with no button held only hovers, and is not recorded. The width and height are those of the screen, which the
     * positions are cells of.
     */
    void recordMouse(MouseEvent me, int width, int height) {
        if (!active || me == null) {
            return;
        }
        long now = MonotonicClock.millis();
        switch (me.kind()) {
            case PRESS -> {
                flushPress();
                scrollLine = -1;
                preKeyPosition = lines.size();
                addSleep(now);
                pressed = me;
                pressedAt = now;
                dragged = false;
                lastEventTime = now;
            }
            case DRAG -> dragged |= pressed != null;
            case RELEASE -> release(me, now);
            case SCROLL_UP, SCROLL_DOWN, SCROLL_LEFT, SCROLL_RIGHT -> recordScroll(me, now);
            default -> {
                return;
            }
        }
        if (me.kind() != MouseEventKind.DRAG) {
            screenWidth = width;
            screenHeight = height;
        }
    }

    /** Whether a press was recorded and its release not yet. */
    boolean isPressPending() {
        return pressed != null;
    }

    /**
     * Drops the press being recorded, as it pressed a key (a footer hint) that is recorded instead: the key plays the
     * same at any screen size. Its release is not written either.
     */
    void dropPress() {
        pressed = null;
    }

    private void release(MouseEvent me, long now) {
        if (pressed == null) {
            if (buttonHeld) {
                lines.add("MouseUp " + me.x() + " " + me.y());
                buttonHeld = false;
                lastEventTime = now;
            }
            // else the release of a press before the recording started, or of a dropped one
            return;
        }
        MouseEvent press = pressed;
        pressed = null;
        boolean moved = dragged || me.x() != press.x() || me.y() != press.y();
        if (press.button() == MouseButton.LEFT && moved) {
            // the pause between the steps of the drag, so it plays as long as it took
            int steps = dragSteps(press.x(), press.y(), me.x(), me.y());
            long delay = Math.max(10, (now - pressedAt) / steps / 10 * 10);
            lines.add("Drag" + (delay != DRAG_STEP_MILLIS ? "@" + delay + "ms" : "") + " " + press.x() + " " + press.y()
                      + " " + me.x() + " " + me.y());
            lastEventTime = now;
        } else {
            // the time the button was held is in the next Sleep
            lines.add(clickCommand(press.button()) + " " + press.x() + " " + press.y());
        }
    }

    private void recordScroll(MouseEvent me, long now) {
        flushPress();
        if (scrollLine >= 0 && scrollLine == lines.size() - 1 && now - lastEventTime <= 200
                && scroll.kind() == me.kind() && scroll.x() == me.x() && scroll.y() == me.y()) {
            scrollCount++;
        } else {
            preKeyPosition = lines.size();
            addSleep(now);
            lines.add("");
            scrollLine = lines.size() - 1;
            scroll = me;
            scrollCount = 1;
        }
        String command = switch (me.kind()) {
            case SCROLL_UP -> "ScrollUp";
            case SCROLL_DOWN -> "ScrollDown";
            case SCROLL_LEFT -> "ScrollLeft";
            default -> "ScrollRight";
        };
        lines.set(scrollLine, command + (scrollCount > 1 ? " " + scrollCount : "") + " " + me.x() + " " + me.y());
        lastEventTime = now;
    }

    /**
     * Writes the press being recorded before something else comes: the left button as a {@code MouseDown}, whose
     * release is then a {@code MouseUp}, and the other buttons as a click.
     */
    private void flushPress() {
        if (pressed == null) {
            return;
        }
        if (pressed.button() == MouseButton.LEFT) {
            lines.add("MouseDown " + pressed.x() + " " + pressed.y());
            buttonHeld = true;
        } else {
            lines.add(clickCommand(pressed.button()) + " " + pressed.x() + " " + pressed.y());
        }
        pressed = null;
    }

    private static String clickCommand(MouseButton button) {
        return switch (button) {
            case RIGHT -> "RightClick";
            case MIDDLE -> "MiddleClick";
            default -> "Click";
        };
    }

    /** The motion steps of a drag between two cells, as TamboUI plays it: one per cell, at most 60. */
    static int dragSteps(int x1, int y1, int x2, int y2) {
        return Math.max(1, Math.min(60, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
    }

    private void addSleep(long now) {
        long elapsed = now - lastEventTime;
        if (elapsed > 200) {
            lines.add("Sleep " + formatSleep(elapsed));
        }
    }

    void recordKeys(List<String> keys, int delay) {
        if (!active) {
            return;
        }
        long now = MonotonicClock.millis();
        addSleep(now);

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
        lastEventTime = MonotonicClock.millis();
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
        lastEventTime = MonotonicClock.millis();
    }

    void resetClock() {
        if (!active) {
            return;
        }
        lastEventTime = MonotonicClock.millis();
    }

    String stop() {
        flushPress();
        active = false;
        if (lines.stream().anyMatch(TapeRecorder::isMouseCommand)) {
            lines.add(headerEnd, "# Mouse positions are cells of a " + screenWidth + "x" + screenHeight
                                 + " screen: play the tape with --record-size=" + screenWidth + "x" + screenHeight);
        }
        return String.join("\n", lines) + "\n";
    }

    private static boolean isMouseCommand(String line) {
        String command = line.split("[\\s@]", 2)[0];
        return command.endsWith("Click") || command.startsWith("Mouse") || command.startsWith("Scroll")
                || "Drag".equals(command);
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
