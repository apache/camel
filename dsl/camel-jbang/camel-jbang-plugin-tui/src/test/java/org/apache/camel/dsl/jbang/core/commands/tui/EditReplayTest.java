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

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditReplayTest {

    /** A plain list of lines with a cursor row, enough to replay into. */
    static final class MemoryEditor implements EditReplay.Editor {
        final List<String> lines = new ArrayList<>();
        int row;
        boolean editing = true;

        MemoryEditor(String text) {
            lines.addAll(text.lines().toList());
        }

        @Override
        public List<String> lines() {
            return new ArrayList<>(lines);
        }

        @Override
        public void moveToRow(int targetRow) {
            row = Math.min(targetRow, lines.size());
            if (row == lines.size()) {
                lines.add("");
            }
        }

        @Override
        public void insertAtCursor(String text) {
            // the replay keeps the cursor at a line start; a marker separates the typed part from the rest
            String current = lines.get(row);
            if (text.equals("\n")) {
                int split = current.indexOf('\u0000');
                String typed = split >= 0 ? current.substring(0, split) : "";
                String rest = split >= 0 ? current.substring(split + 1) : current;
                lines.set(row, typed);
                lines.add(row + 1, rest);
                row++;
            } else {
                if (!current.contains("\u0000")) {
                    current = "\u0000" + current;
                }
                int split = current.indexOf('\u0000');
                lines.set(row, current.substring(0, split) + text + current.substring(split));
            }
        }

        @Override
        public void deleteCurrentLine() {
            lines.remove(row);
        }

        @Override
        public boolean isEditing() {
            return editing;
        }

        String text() {
            return String.join("\n", lines).replace("\u0000", "");
        }
    }

    private static final String ORIGINAL = """
            - route:
                id: timer-log
                from:
                  uri: timer:tick
                  steps:
                    - setBody:
                        simple: hello
                    - log:
                        message: "${body}"
                    - setHeader:
                        name: foo
                        simple: bar
                    - marshal:
                        json: {}
                    - to:
                        uri: mock:done
            """;

    private static final String TARGET = """
            - route:
                id: timer-log
                from:
                  uri: timer:tick
                  steps:
                    - delay:
                        simple: "3000"
                    - setBody:
                        simple: hello
                    - log:
                        message: "${body}"
                        loggingLevel: WARN
                    - setHeader:
                        name: foo
                        simple: bar
                    - marshal:
                        json: {}
                    - to:
                        uri: mock:done
                    - to:
                        uri: log:end
            """;

    /** A clock the tests advance by hand. */
    private static final class FakeClock {
        long now;
    }

    private static EditReplay replay(FakeClock clock) {
        return new EditReplay(() -> clock.now);
    }

    private static void runUntilNotTyping(EditReplay replay, FakeClock clock) {
        for (int i = 0; i < 100_000 && replay.phase() == EditReplay.Phase.TYPING; i++) {
            clock.now += EditReplay.LINE_DELAY_MS;
            replay.tick(clock.now);
        }
    }

    @Test
    void replaysAllHunksWithEnterBetweenThem() {
        MemoryEditor editor = new MemoryEditor(ORIGINAL);
        List<EditDiff.Hunk> hunks = EditDiff.hunks(ORIGINAL.lines().toList(), TARGET.lines().toList(), 1);
        assertEquals(3, hunks.size(), "three separate changes");

        FakeClock clock = new FakeClock();
        EditReplay replay = replay(clock);
        replay.start(editor, hunks);
        assertEquals(EditReplay.Phase.TYPING, replay.phase());
        assertTrue(replay.capturesKeys());

        runUntilNotTyping(replay, clock);
        assertEquals(EditReplay.Phase.PAUSED, replay.phase());
        assertEquals(1, replay.applied());
        replay.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        runUntilNotTyping(replay, clock);
        assertEquals(EditReplay.Phase.PAUSED, replay.phase());
        replay.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        runUntilNotTyping(replay, clock);

        assertEquals(EditReplay.Phase.FINISHED, replay.phase());
        assertEquals(3, replay.applied());
        assertTrue(replay.skippedHunks().isEmpty());
        assertEquals(TARGET.strip(), editor.text().strip());
    }

    @Test
    void escapeStopsAfterTheCurrentHunkAndAnyKeyFinishesTyping() {
        MemoryEditor editor = new MemoryEditor(ORIGINAL);
        FakeClock clock = new FakeClock();
        EditReplay replay = replay(clock);
        replay.start(editor, EditDiff.hunks(ORIGINAL.lines().toList(), TARGET.lines().toList(), 1));

        // a key while typing applies the rest of the hunk at once
        clock.now += EditReplay.LINE_DELAY_MS;
        replay.tick(clock.now);
        replay.handleKeyEvent(KeyEvent.ofChar('x'));
        assertEquals(EditReplay.Phase.PAUSED, replay.phase());
        assertTrue(editor.text().contains("- delay:"));

        // Esc at the pause keeps what was typed and drops the rest
        replay.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertEquals(EditReplay.Phase.FINISHED, replay.phase());
        assertEquals(1, replay.applied());
        assertFalse(editor.text().contains("loggingLevel"));
    }

    @Test
    void handOverLetsTheUserEditAndSkipsAHunkWhoseContextIsGone() {
        MemoryEditor editor = new MemoryEditor(ORIGINAL);
        FakeClock clock = new FakeClock();
        EditReplay replay = replay(clock);
        replay.start(editor, EditDiff.hunks(ORIGINAL.lines().toList(), TARGET.lines().toList(), 1));
        runUntilNotTyping(replay, clock);
        assertEquals(EditReplay.Phase.PAUSED, replay.phase());

        replay.handleKeyEvent(KeyEvent.ofKey(KeyCode.F4, KeyModifiers.NONE));
        assertEquals(EditReplay.Phase.HANDED_OVER, replay.phase());
        assertFalse(replay.capturesKeys());
        assertFalse(replay.handleKeyEvent(KeyEvent.ofChar('q')), "the editor gets ordinary keys now");

        // the user edits the log step (hunk 2's context) and adds a line above everything (shifting hunk 3)
        int logRow = editor.lines.indexOf("        - log:");
        editor.lines.set(logRow + 1, "            message: \"changed by me\"");
        editor.lines.add(0, "# edited by the user");

        assertTrue(replay.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9, KeyModifiers.NONE)));
        runUntilNotTyping(replay, clock);

        assertEquals(EditReplay.Phase.FINISHED, replay.phase());
        assertEquals(List.of(2), replay.skippedHunks(), "hunk 2's context was changed by the user");
        assertEquals(2, replay.applied(), "hunks 1 and 3 applied, 3 at its shifted position");
        assertTrue(editor.text().contains("changed by me"));
        assertFalse(editor.text().contains("loggingLevel"));
        assertTrue(editor.text().strip().endsWith("uri: log:end"));
    }

    @Test
    void hunksAreLocatedByContext() {
        List<String> original = ORIGINAL.lines().toList();
        List<EditDiff.Hunk> hunks = EditDiff.hunks(original, TARGET.lines().toList(), 1);
        EditDiff.Hunk third = hunks.get(2);
        assertEquals(2, third.added());
        assertEquals(0, third.removed());
        List<String> shifted = new ArrayList<>(original);
        shifted.add(0, "# comment");
        shifted.add(0, "# another");
        assertEquals(third.locate(original, 0) + 2, third.locate(shifted, 0));
        assertEquals(-1, third.locate(List.of("nothing", "here"), 0));
    }
}
