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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * A live edit ({@code /write live} in the AI panel): a file an AI tool writes is replayed in the Source tab's editor
 * change by change, for the user to watch, pause, ask the AI about (F8), and then save or discard. The tool thread
 * waits in {@link #replayFileWrite} for the outcome; the UI thread starts the replay and drives it in {@link #tick},
 * and completes the outcome once the user saved or discarded the buffer. {@link EditReplay} does the typing.
 */
class LiveEdit {

    /** What a live edit needs of the TUI around it. */
    interface Host {
        /** Shows the file in the editor of the Source tab, in edit mode; null when it cannot be edited now. */
        EditReplay.Editor openFileForReplay(Path file);

        /** Whether the editor is still in edit mode: the replay ends when the user leaves it. */
        boolean isEditing();

        void notification(String message, boolean error);

        boolean isAiPanelOpen();

        void openAiPanel();

        void closeAiPanel();

        /** Opens the AI panel with the question started; the handler takes the question (true) or lets it go out. */
        void askAboutEdit(String prefill, Predicate<String> onQuestion);

        /** A note the AI gets with the next question: what became of a parked edit. */
        void addPendingNote(String note);
    }

    /**
     * How long a live edit's tool call waits for the user to save or discard; after that the AI's turn goes on and the
     * edit stays parked in the editor.
     */
    static final long REPLAY_WAIT_MINUTES = 5;

    private final Host host;
    private final EditReplay editReplay;
    // a live write waiting to start on the UI thread, and the promise its tool thread waits on
    private volatile McpFacade.FileWrite pendingReplay;
    private volatile CompletableFuture<McpFacade.ReplayOutcome> pendingReplayOutcome;
    // both are read by the tool thread to decide whether a write can be replayed
    private volatile McpFacade.FileWrite activeReplay;
    private EditReplay.Editor activeReplayEditor;
    // null while the replay is parked: the tool call returned early with the user's question about the edit
    private volatile CompletableFuture<McpFacade.ReplayOutcome> activeReplayOutcome;
    private volatile String pendingEditQuestion;
    // the AI panel overlays the editor, so it is hidden while an edit is replayed and shown again afterwards
    private boolean replayHidAiPanel;

    LiveEdit(Host host) {
        this(host, new EditReplay());
    }

    LiveEdit(Host host, EditReplay editReplay) {
        this.host = host;
        this.editReplay = editReplay;
    }

    /**
     * Replays the write of an AI tool in the editor and waits, on the tool thread, until the user saved or discarded
     * it, at most {@link #REPLAY_WAIT_MINUTES}; null when it cannot be replayed now (another one is going on), so the
     * tool asks with the confirm dialog instead.
     */
    McpFacade.ReplayOutcome replayFileWrite(McpFacade.FileWrite request) {
        if (pendingReplay != null || (activeReplay != null && activeReplayOutcome != null)) {
            return null;
        }
        if (activeReplay != null && !activeReplay.file().equals(request.file())) {
            // a parked replay of another file is still in the editor
            return null;
        }
        CompletableFuture<McpFacade.ReplayOutcome> outcome = new CompletableFuture<>();
        pendingReplayOutcome = outcome;
        pendingReplay = request;
        try {
            // the user decides when this ends (save or discard)
            return outcome.get(REPLAY_WAIT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            if (pendingReplay == request) {
                // never started in the editor: nothing was replayed, nothing is pending
                pendingReplay = null;
                pendingReplayOutcome = null;
                return new McpFacade.ReplayOutcome(false, 0, List.of(), null);
            }
            // the user has not decided for a long while: the AI's turn goes on without it; the edit
            // stays parked in the editor and the AI is told what became of it with the next question.
            // complete() is atomic: a save at the same moment wins
            McpFacade.ReplayOutcome undecided = McpFacade.ReplayOutcome.undecidedOutcome();
            return outcome.complete(undecided) ? undecided : outcome.join();
        } catch (Exception e) {
            pendingReplay = null;
            editReplay.abort();
            activeReplay = null;
            activeReplayEditor = null;
            activeReplayOutcome = null;
            return new McpFacade.ReplayOutcome(false, 0, List.of(), null);
        }
    }

    /** The file of the replay parked in the editor, which the AI asked about, or null. */
    String parkedReplayFile() {
        McpFacade.FileWrite parked = activeReplay;
        return parked != null && activeReplayOutcome == null ? parked.file() : null;
    }

    /** The keys of the replay while it types or pauses, and F8 to ask about it once it is typed: true when taken. */
    boolean handleKeyEvent(KeyEvent ke) {
        if (editReplay.isActive() && editReplay.handleKeyEvent(ke)) {
            if (editReplay.isAsking() && !host.isAiPanelOpen()) {
                openEditQuestion();
            }
            return true;
        }
        if (ke.isKey(KeyCode.F8) && activeReplay != null && activeReplayOutcome != null && !host.isAiPanelOpen()
                && editReplay.askAfterFinish()) {
            // the replay is over (a single change never pauses) but not yet saved or discarded: ask about it
            openEditQuestion();
            return true;
        }
        return false;
    }

    /** Whether the user asks the AI about the paused edit: the keys the AI panel does not take stay away from it. */
    boolean isAsking() {
        return editReplay.isAsking();
    }

    /** Whether the replay takes the keys (it types or pauses), so its footer goes before that of the AI panel. */
    boolean capturesKeys() {
        return editReplay.isActive() && editReplay.capturesKeys();
    }

    /** Whether a live edit is in the editor: being typed, or typed and not yet saved or discarded. */
    boolean isShown() {
        return editReplay.isActive() || activeReplay != null;
    }

    void renderFooter(List<Span> spans) {
        if (capturesKeys()) {
            editReplay.renderFooter(spans);
        } else if (editReplay.isActive()) {
            editReplay.renderFooter(spans);
            hint(spans, "Ctrl+S", "save");
            hint(spans, "F7", "diff");
            hintLast(spans, "Esc", "discard");
        } else if (activeReplay != null) {
            hint(spans, "AI edit done", "");
            hint(spans, "Ctrl+S/F5", "save (apply)");
            hint(spans, "F7", "diff");
            if (activeReplayOutcome != null) {
                hint(spans, "F8", "ask the AI about it");
            }
            hintLast(spans, "Esc", "discard");
        }
    }

    /**
     * Drives a live AI edit: starts a pending replay in the source editor, advances the typing, and once the user has
     * saved or discarded the buffer completes the outcome the tool thread is waiting for. A question asked at a pause
     * (F8) completes the outcome early and parks the replay; a later write of the same file continues from the editor's
     * content, and if none comes the AI is told what became of the parked edit with the next question.
     */
    void tick(long now) {
        McpFacade.FileWrite request = pendingReplay;
        if (request != null) {
            pendingReplay = null;
            CompletableFuture<McpFacade.ReplayOutcome> outcome = pendingReplayOutcome;
            pendingReplayOutcome = null;
            if (!startEditReplay(request, outcome)) {
                return;
            }
        }
        if (activeReplay == null) {
            return;
        }
        if (activeReplayOutcome != null && activeReplayOutcome.isDone()) {
            // the tool call stopped waiting (the user took long to decide): the replay is parked
            activeReplayOutcome = null;
        }
        String question = pendingEditQuestion;
        if (question != null) {
            pendingEditQuestion = null;
            if (activeReplayOutcome != null) {
                // the tool call returns with the question; the replay stays parked in the editor
                activeReplayOutcome.complete(new McpFacade.ReplayOutcome(
                        false, editReplay.applied(), editReplay.skippedHunks(), replayBuffer(), question,
                        editReplay.remaining()));
                activeReplayOutcome = null;
                replayHidAiPanel = false;
            }
        }
        if (editReplay.isAsking() && !host.isAiPanelOpen()) {
            editReplay.endAsking();
        }
        editReplay.tick(now);
        // the replay is over (finished, stopped or handed over) once the user leaves edit mode or the file changes
        Path file = activeReplay.directory().resolve(activeReplay.file());
        String onDisk;
        try {
            onDisk = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            onDisk = null;
        }
        boolean editing = host.isEditing();
        boolean saved = onDisk != null && !onDisk.equals(activeReplay.oldContent());
        if (saved || !editing) {
            McpFacade.ReplayOutcome outcome = new McpFacade.ReplayOutcome(
                    saved, editReplay.applied(), editReplay.skippedHunks(), onDisk);
            editReplay.abort();
            if (activeReplayOutcome != null) {
                activeReplayOutcome.complete(outcome);
            } else {
                noteParkedReplayOutcome(activeReplay.file(), outcome);
            }
            activeReplay = null;
            activeReplayEditor = null;
            activeReplayOutcome = null;
            if (replayHidAiPanel) {
                replayHidAiPanel = false;
                host.openAiPanel();
            }
        }
    }

    /**
     * Opens the file in the editor and starts replaying the change, or continues a parked replay of the same file from
     * the editor's current content. Returns false when the replay could not start (the outcome is completed with null
     * so the tool falls back to the confirm dialog).
     */
    private boolean startEditReplay(McpFacade.FileWrite request, CompletableFuture<McpFacade.ReplayOutcome> outcome) {
        List<String> after = request.newContent().isEmpty() ? List.of() : request.newContent().lines().toList();
        if (activeReplay != null && activeReplayEditor != null && activeReplayOutcome == null) {
            List<String> current = EditReplay.contentLines(activeReplayEditor.lines());
            editReplay.start(activeReplayEditor, EditDiff.hunks(current, after, 3));
            activeReplay = new McpFacade.FileWrite(
                    request.file(), request.directory(), activeReplay.oldContent(), request.newContent(),
                    request.temporary(), request.devMode());
            activeReplayOutcome = outcome;
            host.notification("AI edit: the AI revised the change; watch it continue in the editor", false);
        } else {
            Path file = request.directory().resolve(request.file());
            EditReplay.Editor editor = host.openFileForReplay(file);
            if (editor == null) {
                outcome.complete(null);
                return false;
            }
            List<String> before = request.oldContent() == null ? List.of() : request.oldContent().lines().toList();
            editReplay.start(editor, EditDiff.hunks(before, after, 3));
            activeReplay = request;
            activeReplayEditor = editor;
            activeReplayOutcome = outcome;
            host.notification("AI edit: watch the change in the editor, then save (Ctrl+S) or discard (Esc)", false);
        }
        if (host.isAiPanelOpen()) {
            // hiding the panel only hides it; the request keeps waiting for the outcome
            replayHidAiPanel = true;
            host.closeAiPanel();
        }
        return true;
    }

    /** The editor buffer of the replayed file as text (the editor keeps a final newline as an empty last line). */
    private String replayBuffer() {
        String text = String.join("\n", activeReplayEditor.lines());
        if (activeReplay.oldContent() != null && activeReplay.oldContent().endsWith("\n") && !text.endsWith("\n")) {
            text += "\n";
        }
        return text;
    }

    /** F8 at a pause: open the AI panel to ask about the current edit; the question returns the waiting tool call. */
    private void openEditQuestion() {
        String prefill = "About edit " + editReplay.current() + " of " + editReplay.total() + ": ";
        host.askAboutEdit(prefill, question -> {
            if (activeReplay != null && activeReplayOutcome != null) {
                pendingEditQuestion = question;
                return true;
            }
            // the tool call already returned (asked before): a normal question, the AI knows the edit is parked
            return false;
        });
    }

    /** A parked replay ended without the AI being involved: it learns what happened with the next question. */
    private void noteParkedReplayOutcome(String file, McpFacade.ReplayOutcome outcome) {
        String note;
        if (outcome.saved()) {
            note = "The user saved " + file + " after the paused edit (" + outcome.applied() + " edit(s) applied"
                   + (outcome.skipped().isEmpty() ? "" : ", skipped " + outcome.skipped())
                   + "); read it with camel_get_files if you need the current content. Continue with any file"
                   + " write that was deferred meanwhile.";
        } else {
            note = "The user discarded the paused edit of " + file + "; the file is unchanged. Ask before"
                   + " continuing with any file write that was deferred meanwhile.";
        }
        host.addPendingNote(note);
    }
}
