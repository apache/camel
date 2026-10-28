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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A live edit between the tool thread, which waits in replayFileWrite, and the UI thread, which replays the write in
 * the editor on its ticks and completes the wait once the user saved or discarded it.
 */
class LiveEditTest {

    @TempDir
    Path dir;

    private final AtomicLong clock = new AtomicLong(1000);
    private final FakeHost host = new FakeHost();
    private final LiveEdit liveEdit = new LiveEdit(host, new EditReplay(clock::get));

    @Test
    void theToolWaitsUntilTheUserSavesTheChange() throws Exception {
        Path file = Files.writeString(dir.resolve("route.yaml"), "a\nb\n");
        host.aiPanelOpen = true;
        CompletableFuture<McpFacade.ReplayOutcome> tool = CompletableFuture.supplyAsync(
                () -> liveEdit.replayFileWrite(write("a\nc\n")));

        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            liveEdit.tick(clock.addAndGet(100));
            return host.editor != null && EditReplay.contentLines(host.editor.lines()).equals(List.of("a", "c"));
        });
        assertThat(host.aiPanelOpen).as("the AI panel is hidden while the edit is replayed").isFalse();
        assertThat(host.notifications).hasSize(1);
        assertThat(liveEdit.isShown()).isTrue();
        assertThat(tool).isNotDone();

        // the user saves
        Files.writeString(file, "a\nc\n");
        liveEdit.tick(clock.addAndGet(100));
        McpFacade.ReplayOutcome outcome = tool.get(10, TimeUnit.SECONDS);
        assertThat(outcome.saved()).isTrue();
        assertThat(outcome.applied()).isEqualTo(1);
        assertThat(outcome.content()).isEqualTo("a\nc\n");
        assertThat(host.aiPanelOpen).as("the AI panel is back").isTrue();
        assertThat(liveEdit.isShown()).isFalse();
    }

    @Test
    void aSecondWriteIsNotReplayedWhileTheFirstIsInTheEditor() throws Exception {
        Files.writeString(dir.resolve("route.yaml"), "a\nb\n");
        CompletableFuture<McpFacade.ReplayOutcome> tool = CompletableFuture.supplyAsync(
                () -> liveEdit.replayFileWrite(write("a\nc\n")));
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            liveEdit.tick(clock.addAndGet(100));
            return liveEdit.isShown();
        });

        // null: the tool asks with the confirm dialog instead
        assertThat(liveEdit.replayFileWrite(write("a\nd\n"))).isNull();

        // the user discards the edit: the editor leaves edit mode and the file is as it was
        host.editor.editing = false;
        liveEdit.tick(clock.addAndGet(100));
        McpFacade.ReplayOutcome outcome = tool.get(10, TimeUnit.SECONDS);
        assertThat(outcome.saved()).isFalse();
        assertThat(outcome.content()).isEqualTo("a\nb\n");
    }

    @Test
    void aFileTheEditorCannotOpenGoesToTheConfirmDialog() throws Exception {
        Files.writeString(dir.resolve("route.yaml"), "a\nb\n");
        host.refuse = true;
        CompletableFuture<McpFacade.ReplayOutcome> tool = CompletableFuture.supplyAsync(
                () -> liveEdit.replayFileWrite(write("a\nc\n")));

        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            liveEdit.tick(clock.addAndGet(100));
            return tool.isDone();
        });
        assertThat(tool.get()).isNull();
        assertThat(liveEdit.isShown()).isFalse();
    }

    private McpFacade.FileWrite write(String newContent) {
        return new McpFacade.FileWrite("route.yaml", dir, "a\nb\n", newContent, false, false);
    }

    private static final class FakeHost implements LiveEdit.Host {
        volatile EditReplayTest.MemoryEditor editor;
        volatile boolean aiPanelOpen;
        volatile boolean refuse;
        final List<String> notifications = new CopyOnWriteArrayList<>();

        @Override
        public EditReplay.Editor openFileForReplay(Path file) {
            if (refuse) {
                return null;
            }
            try {
                editor = new EditReplayTest.MemoryEditor(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return editor;
        }

        @Override
        public boolean isEditing() {
            return editor != null && editor.editing;
        }

        @Override
        public void notification(String message, boolean error) {
            notifications.add(message);
        }

        @Override
        public boolean isAiPanelOpen() {
            return aiPanelOpen;
        }

        @Override
        public void openAiPanel() {
            aiPanelOpen = true;
        }

        @Override
        public void closeAiPanel() {
            aiPanelOpen = false;
        }

        @Override
        public void askAboutEdit(String prefill, Predicate<String> onQuestion) {
            aiPanelOpen = true;
        }

        @Override
        public void addPendingNote(String note) {
        }
    }
}
