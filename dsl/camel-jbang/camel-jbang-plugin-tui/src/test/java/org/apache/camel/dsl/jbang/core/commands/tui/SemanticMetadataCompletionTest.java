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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticMetadataCompletionTest {
    @Test
    void readsOverridesAndDefaultsFromTheUnsavedBuffer() {
        String yaml = "- semantic:\n    expert: decisions\n    evaluation:\n      support/team:\n"
                      + "        type:\n        expert: \"{{selected.expert}}\"\n      next:\n        operation:";
        assertThat(at(yaml, 4)).isEqualTo(new SemanticCompletionContext("{{selected.expert}}", true));
        assertThat(at(yaml, 7)).isEqualTo(new SemanticCompletionContext("decisions", false));
        assertThat(at(yaml.replace("    expert: decisions\n", ""), 6))
                .isEqualTo(new SemanticCompletionContext(null, false));
        assertThat(at(yaml.replace("operation:", "operation: \"{{unfinished"), 7))
                .isEqualTo(new SemanticCompletionContext("decisions", false));
        assertThat(at(yaml.replace("expert: decisions", "expert: other"), 7))
                .isEqualTo(new SemanticCompletionContext("other", false));
        assertThat(at(yaml.replace("expert: \"{{selected.expert}}\"", "expert:"), 4)).isNull();
        assertThat(at(yaml.replace("expert: \"{{selected.expert}}\"", "expert: [one, two]"), 4)).isNull();
        assertThat(at("- route:\n    from:\n      type:", 2)).isNull();
        assertThat(at("- semantic:\n    evaluation:\n      check:\n        parameters:\n          operation:", 4)).isNull();
    }

    @Test
    void operationsBelongToTheSelectedExpertAndKeepPlaceholders(@TempDir Path directory) throws Exception {
        try (var runtime = new Runtime()) {
            var assist = new SourceEditAssist(runtime);
            Files.writeString(directory.resolve("application.properties"), "custom.operation=detect");
            assist.setRootDir(directory);
            var security = new SemanticCompletionContext("security", false);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(assist.provideSemanticCompletions(security).getNow(List.of()))
                            .extracting(AutocompletePopup.CompletionItem::key)
                            .containsExactly("injection", "{{custom.operation}}"));
            assertThat(assist.provideSemanticCompletions(security).getNow(List.of()).get(0).description())
                    .contains("returns boolean");
            var decisions = new SemanticCompletionContext("decisions", true);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(assist.provideSemanticCompletions(decisions).getNow(List.of()))
                            .extracting(AutocompletePopup.CompletionItem::key)
                            .containsExactly("boolean", "choice", "score", "{{custom.operation}}"));
            var missing = new SemanticCompletionContext("missing", false);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(assist.provideSemanticCompletions(missing).getNow(List.of()))
                            .extracting(AutocompletePopup.CompletionItem::key).containsExactly("{{custom.operation}}"));
            assertThat(runtime.requests).hasValue(3);
        }
    }

    @Test
    void changingIntegrationDoesNotReuseAnotherContractAndStoppedProjectsDoNotCallIpc() {
        try (var runtime = new Runtime()) {
            var completions = new SemanticCompletions(runtime);
            var context = new SemanticCompletionContext(null, false);
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(completions.provide(context).getNow(List.of()))
                    .extracting(AutocompletePopup.CompletionItem::key).containsExactly("injection"));
            runtime.selectedPid = "second";
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(completions.provide(context).getNow(List.of()))
                    .extracting(AutocompletePopup.CompletionItem::key).containsExactly("classify"));
            runtime.selectedPid = "project";
            assertThat(completions.provide(context).getNow(List.of())).extracting(AutocompletePopup.CompletionItem::key)
                    .containsExactly("classify");
            runtime.selectedPid = "stopped";
            assertThat(completions.provide(context).getNow(List.of())).isEmpty();
            assertThat(runtime.requests).hasValue(2);
        }
    }

    @Test
    void caseSensitiveOperationsAreOnlySuggestedForOperation() {
        try (var runtime = new Runtime()) {
            var completions = new SemanticCompletions(runtime);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(completions.provide(new SemanticCompletionContext("mixed", false))
                            .getNow(List.of())).extracting(AutocompletePopup.CompletionItem::key).containsExactly("Detect"));
            assertThat(completions.provide(new SemanticCompletionContext("mixed", true)).getNow(List.of())).isEmpty();
            assertThat(runtime.requests).hasValue(1);
        }
    }

    @Test
    void slowMetadataDoesNotBlockTheEditorOrOverwriteNewerExpertSelection() {
        try (var runtime = new Runtime()) {
            runtime.release = new CountDownLatch(1);
            var completions = new SemanticCompletions(runtime);
            assertThat(completions.provide(new SemanticCompletionContext("security", false))).isNotDone();
            assertThat(runtime.release.getCount()).isEqualTo(1);
            assertThat(completions.provide(new SemanticCompletionContext("decisions", false))).isNotDone();
            runtime.release.countDown();
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(
                            completions.provide(new SemanticCompletionContext("decisions", false)).getNow(List.of()))
                            .extracting(AutocompletePopup.CompletionItem::key).containsExactly("boolean", "choice", "score"));
        }
    }

    @Test
    void editorUsesAnUnsavedExpertOverride(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("route.yaml");
        Files.writeString(file, "- semantic:\n    evaluation:\n      check:\n        operation:\n        expert: security\n");
        var viewer = new SourceViewer();
        var assist
                = new SourceEditAssist(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        viewer.setAutocompleteProvider(assist::provideYamlKeyCompletions);
        viewer.setAutocompleteValueProvider(assist::provideYamlValueCompletions);
        var requested = new AtomicReference<SemanticCompletionContext>();
        viewer.setSemanticCompletion(context -> {
            requested.set(context);
            return CompletableFuture.completedFuture(injection());
        });
        viewer.loadFile(file);
        viewer.enterEditMode();
        for (int i = 0; i < 4; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofChar('2', KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        assertThat(requested.get()).isEqualTo(new SemanticCompletionContext("security2", false));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(viewer.editText()).contains("operation: injection");
        assertThat(Files.readString(file)).doesNotContain("security2");
    }

    @Test
    void delayedResponseOpensCompletionWithoutAnotherTab(@TempDir Path directory) throws Exception {
        var response = new CompletableFuture<List<AutocompletePopup.CompletionItem>>();
        var viewer = waitingForMetadata(directory, response);
        response.complete(injection());
        viewer.refreshSemanticCompletion();
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(viewer.editText()).contains("operation: injection");
    }

    @Test
    void enterDoesNotAcceptAnUnseenResponse(@TempDir Path directory) throws Exception {
        var response = new CompletableFuture<List<AutocompletePopup.CompletionItem>>();
        var viewer = waitingForMetadata(directory, response);
        response.complete(injection());
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        viewer.refreshSemanticCompletion();
        assertThat(viewer.editText()).doesNotContain("injection");
    }

    @Test
    void discardingEditsCancelsPendingMetadata(@TempDir Path directory) throws Exception {
        var response = new CompletableFuture<List<AutocompletePopup.CompletionItem>>();
        var viewer = waitingForMetadata(directory, response);
        viewer.handleKeyEvent(KeyEvent.ofChar(' '));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB));
        viewer.cancelEdit();
        response.complete(injection());
        viewer.refreshSemanticCompletion();
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(viewer.editText()).doesNotContain("injection");
    }

    @Test
    void cancelledRequestCannotReopenCompletion(@TempDir Path directory) throws Exception {
        var response = new CompletableFuture<List<AutocompletePopup.CompletionItem>>();
        var viewer = waitingForMetadata(directory, response);
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT));
        response.complete(injection());
        viewer.refreshSemanticCompletion();
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(viewer.editText()).doesNotContain("injection");
    }

    private static SourceViewer waitingForMetadata(
            Path directory, CompletableFuture<List<AutocompletePopup.CompletionItem>> response)
            throws Exception {
        Path file = directory.resolve("route.yaml");
        Files.writeString(file, "- semantic:\n    evaluation:\n      check:\n        operation:");
        var viewer = new SourceViewer();
        viewer.setAutocompleteProvider(context -> List.of());
        viewer.setAutocompleteValueProvider(context -> List.of());
        viewer.setSemanticCompletion(context -> response);
        viewer.loadFile(file);
        viewer.enterEditMode();
        for (int i = 0; i < 3; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB));
        return viewer;
    }

    private static List<AutocompletePopup.CompletionItem> injection() {
        return List
                .of(new AutocompletePopup.CompletionItem("injection", "Detect injection", "string", null, false, null, null));
    }

    private static SemanticCompletionContext at(String yaml, int row) {
        return SemanticCompletionContext.at(List.of(yaml.split("\n", -1)), row);
    }

    private static IntegrationInfo integration(String pid) {
        var info = new IntegrationInfo();
        info.pid = pid;
        return info;
    }

    private static IntegrationInfo project(String pid, String linkedPid) {
        IntegrationInfo info = integration(pid);
        info.phantom = true;
        info.linkedPid = linkedPid;
        return info;
    }

    private static class Runtime extends MonitorContext implements AutoCloseable {
        final AtomicInteger requests = new AtomicInteger();
        volatile CountDownLatch release;

        Runtime() {
            super(new AtomicReference<>(
                    List.of(integration("first"), integration("second"), project("project", "second"),
                            project("stopped", null))),
                  new AtomicReference<>(List.of()));
            selectedPid = "first";
        }

        @Override
        JsonObject executeIndependentAction(String pid, JsonObject request, long timeoutMs) {
            requests.incrementAndGet();
            assertThat(request.getString("action")).isEqualTo("semantic-metadata");
            if (release != null) {
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            String expert = request.getString("expert");
            List<String> names = "mixed".equals(expert) ? List.of("Detect")
                    : "decisions".equals(expert) ? List.of("boolean", "choice", "score")
                    : "missing".equals(expert) ? List.of() : List.of("second".equals(pid) ? "classify" : "injection");
            JsonArray operations = new JsonArray();
            for (String name : names) {
                JsonObject operation = new JsonObject();
                operation.put("name", name);
                operation.put("description", "Operation " + name);
                operation.put("resultType", "boolean");
                operations.add(operation);
            }
            JsonObject answer = new JsonObject();
            answer.put("operations", operations);
            return answer;
        }

        @Override
        public void close() {
            if (release != null) {
                release.countDown();
            }
            backgroundExecutor.shutdownNow();
        }
    }
}
