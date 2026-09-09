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
import java.util.function.LongSupplier;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Replays a file change requested by the AI in the source editor, hunk by hunk, so the user sees the edit happen:
 * removed lines disappear, added lines are typed at a readable pace, and the replay pauses between hunks. Enter
 * continues with the next hunk, Esc stops (what was typed stays in the editor), any other key finishes the current hunk
 * at once, and F4 (the editor's edit key) hands the keyboard to the editor; F9 resumes the remaining hunks afterwards.
 * Hunks are located by their context lines, so edits the user made in between shift them rather than break them, and a
 * hunk whose context is gone is skipped and reported.
 *
 * The replay drives an {@link Editor}; the source viewer implements it, tests use an in-memory one. It advances on UI
 * ticks and never blocks the UI thread.
 */
final class EditReplay {

    /** The editor operations a replay needs. All rows are zero based. */
    interface Editor {
        List<String> lines();

        void moveToRow(int row);

        /** Inserts text at the cursor (which the replay keeps at a line start). */
        void insertAtCursor(String text);

        /** Deletes the line the cursor is on. */
        void deleteCurrentLine();

        /** Whether the editor is still in edit mode (false once the user saved and left, or discarded). */
        boolean isEditing();
    }

    enum Phase {
        IDLE,
        TYPING,
        PAUSED,
        HANDED_OVER,
        FINISHED
    }

    /** Milliseconds per typed character, and per line removal. */
    static final long CHAR_DELAY_MS = 12;
    static final long LINE_DELAY_MS = 90;

    private final LongSupplier clock;
    private Editor editor;
    private List<EditDiff.Hunk> hunks = List.of();
    private int hunkIndex;
    private Phase phase = Phase.IDLE;
    private final List<Integer> applied = new ArrayList<>();
    private final List<Integer> skipped = new ArrayList<>();

    // progress inside the current hunk
    private int row;
    private int bodyIndex;
    private int charIndex;
    private long nextActionAt;
    private int minRow;

    EditReplay() {
        this(System::currentTimeMillis);
    }

    /** With a clock, for tests that drive the ticks. */
    EditReplay(LongSupplier clock) {
        this.clock = clock;
    }

    void start(Editor editor, List<EditDiff.Hunk> hunks) {
        this.editor = editor;
        this.hunks = hunks;
        this.hunkIndex = -1;
        this.applied.clear();
        this.skipped.clear();
        this.minRow = 0;
        this.phase = Phase.IDLE;
        beginNextHunk(clock.getAsLong());
    }

    Phase phase() {
        return phase;
    }

    boolean isActive() {
        return phase != Phase.IDLE && phase != Phase.FINISHED;
    }

    /** Whether the replay owns the keyboard (typing and pauses); after a hand-over the editor has it. */
    boolean capturesKeys() {
        return phase == Phase.TYPING || phase == Phase.PAUSED;
    }

    int applied() {
        return applied.size();
    }

    List<Integer> skippedHunks() {
        return List.copyOf(skipped);
    }

    int total() {
        return hunks.size();
    }

    int current() {
        return hunkIndex + 1;
    }

    void abort() {
        phase = Phase.FINISHED;
    }

    private void beginNextHunk(long now) {
        hunkIndex++;
        while (hunkIndex < hunks.size()) {
            EditDiff.Hunk hunk = hunks.get(hunkIndex);
            int start = hunk.locate(editor.lines(), minRow);
            if (start < 0) {
                // the user changed this part in the meantime
                skipped.add(hunkIndex + 1);
                hunkIndex++;
                continue;
            }
            row = start + hunk.before().size();
            bodyIndex = 0;
            charIndex = 0;
            editor.moveToRow(row);
            phase = Phase.TYPING;
            nextActionAt = now + LINE_DELAY_MS;
            return;
        }
        phase = Phase.FINISHED;
    }

    /** Advances the replay; call on every UI tick. */
    void tick(long now) {
        if (phase != Phase.TYPING) {
            return;
        }
        if (!editor.isEditing()) {
            phase = Phase.FINISHED;
            return;
        }
        while (phase == Phase.TYPING && now >= nextActionAt) {
            step(now);
        }
    }

    /** One typing step: a character, a line removal, or a context line. */
    private void step(long now) {
        EditDiff.Hunk hunk = hunks.get(hunkIndex);
        if (bodyIndex >= hunk.body().size()) {
            finishHunk();
            return;
        }
        EditDiff.DiffEntry entry = hunk.body().get(bodyIndex);
        if (entry.type() == ' ') {
            row++;
            bodyIndex++;
            editor.moveToRow(row);
            nextActionAt = now + LINE_DELAY_MS / 3;
        } else if (entry.type() == '-') {
            editor.moveToRow(row);
            editor.deleteCurrentLine();
            bodyIndex++;
            nextActionAt = now + LINE_DELAY_MS;
        } else {
            String text = entry.text();
            if (charIndex == 0) {
                editor.moveToRow(row);
            }
            if (charIndex < text.length()) {
                editor.insertAtCursor(String.valueOf(text.charAt(charIndex)));
                charIndex++;
                nextActionAt = now + CHAR_DELAY_MS;
            } else {
                editor.insertAtCursor("\n");
                row++;
                bodyIndex++;
                charIndex = 0;
                nextActionAt = now + LINE_DELAY_MS;
            }
        }
    }

    /** Applies the rest of the current hunk at once. */
    private void finishHunkNow() {
        EditDiff.Hunk hunk = hunks.get(hunkIndex);
        while (bodyIndex < hunk.body().size()) {
            EditDiff.DiffEntry entry = hunk.body().get(bodyIndex);
            if (entry.type() == ' ') {
                row++;
            } else if (entry.type() == '-') {
                editor.moveToRow(row);
                editor.deleteCurrentLine();
            } else {
                editor.moveToRow(row);
                editor.insertAtCursor(entry.text().substring(charIndex) + "\n");
                row++;
            }
            bodyIndex++;
            charIndex = 0;
        }
        finishHunk();
    }

    private void finishHunk() {
        applied.add(hunkIndex + 1);
        EditDiff.Hunk hunk = hunks.get(hunkIndex);
        minRow = row + hunk.after().size();
        editor.moveToRow(Math.max(0, row - 1));
        if (hunkIndex + 1 < hunks.size()) {
            phase = Phase.PAUSED;
        } else {
            phase = Phase.FINISHED;
        }
    }

    /** Keys while the replay owns the keyboard, or F9 after a hand-over. Returns true when consumed. */
    boolean handleKeyEvent(KeyEvent ke) {
        long now = clock.getAsLong();
        switch (phase) {
            case TYPING -> {
                if (ke.isCancel()) {
                    finishHunkNow();
                    phase = Phase.FINISHED;
                } else {
                    finishHunkNow();
                }
                return true;
            }
            case PAUSED -> {
                if (ke.isConfirm()) {
                    beginNextHunk(now);
                } else if (ke.isCancel()) {
                    phase = Phase.FINISHED;
                } else if (ke.isKey(KeyCode.F4)) {
                    // F4 is the Source tab's edit key
                    phase = Phase.HANDED_OVER;
                }
                return true;
            }
            case HANDED_OVER -> {
                if (ke.isKey(KeyCode.F9)) {
                    beginNextHunk(now);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    void renderFooter(List<Span> spans) {
        switch (phase) {
            case TYPING -> {
                hint(spans, "AI edit " + current() + " of " + total(), "typing");
                hintLast(spans, "any key", "finish edit");
            }
            case PAUSED -> {
                hint(spans, "Enter", "next AI edit (" + (current() + 1) + " of " + total() + ")");
                hint(spans, "F4", "edit yourself");
                hintLast(spans, "Esc", "stop");
            }
            case HANDED_OVER -> {
                hint(spans, "F9", "continue AI edit (" + (total() - current()) + " left)");
            }
            default -> {
            }
        }
    }
}
