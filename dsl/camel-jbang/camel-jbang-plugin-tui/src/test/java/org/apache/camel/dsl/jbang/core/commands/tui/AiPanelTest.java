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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPanelTest {

    @Test
    void normalTextStillGoesToLlm() throws Exception {
        AiPanel panel = new AiPanel();
        RecordingLlmClient client = new RecordingLlmClient("ok");
        panel.setClientForTesting(client);
        panel.open();

        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(client.awaitAnswer(5, TimeUnit.SECONDS));
        assertEquals("what routes are running?", client.lastQuestion());
        assertTrue(panel.conversationForTesting().stream().anyMatch(entry -> entry.role() == AiRole.USER));
    }

    @Test
    void slashInputDoesNotGoToLlm() {
        AiPanel panel = new AiPanel();
        RecordingLlmClient client = new RecordingLlmClient("ok");
        panel.setClientForTesting(client);
        panel.open();

        type(panel, "/help");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertNull(client.lastQuestion());
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.SYSTEM && entry.text().contains("/run <camel run args>")));
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.SYSTEM && entry.text().contains("/provider")
                        && entry.text().contains("Switch the AI provider")));
    }

    @Test
    void slashCommandHintsRenderWhileTyping() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/");

        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("/provider"));
        assertTrue(rendered.contains("Switch the AI provider"));
    }

    @Test
    void clearResetsConversationAndUsageButKeepsProvider() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        type(panel, "/help");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        type(panel, "/clear");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.conversationForTesting().isEmpty());
        assertEquals(0, panel.sessionTotalTokensForTesting());
    }

    @Test
    void clearAlsoResetsLlmMessageContext() throws Exception {
        AiPanel panel = new AiPanel();
        RecordingLlmClient client = new RecordingLlmClient("ok");
        panel.setClientForTesting(client);
        panel.open();

        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(client.awaitAnswer(5, TimeUnit.SECONDS));
        // awaitAnswer() only signals that chatWithTools() was called; wait for the whole agent thread to
        // finish (including appending the assistant message) before asserting on message history.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> assertFalse(panel.isAgentThreadRunningForTesting(), "agent thread should finish within 5 seconds"));
        assertTrue(panel.messageCountForTesting() > 0, "asking a question should populate the LLM message history");

        type(panel, "/clear");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertEquals(0, panel.messageCountForTesting(),
                "/clear should reset the LLM message context, not just the visible conversation");
    }

    @Test
    void closeCommandClosesPanel() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();

        type(panel, "/close");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertFalse(panel.isOpen());
    }

    @Test
    void exitAndQuitRequestFullTuiExit() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/quit");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(context.exitRequested);
    }

    @Test
    void providerCommandOpensProviderSwitch() {
        AiPanel panel = new AiPanel();
        panel.setProviderChoicesForTesting(List.of(
                new AiProviderSwitchPopup.ProviderChoice("auto", "", "", true)));
        panel.open();

        type(panel, "/provider");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.isProviderSwitchVisibleForTesting());
    }

    @Test
    void modelCommandListsModelsAndSwitches() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/model");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Current model: test-model")
                        && entry.text().contains("model-a"))));

        type(panel, "/model model-b");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertEquals("model-b", context.switchedModel);
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Switched model to model-b")));
    }

    @Test
    void modelCommandReportsErrorWhenNoClientAvailable() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        context.switchModelResult = false;
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/model model-b");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertNull(context.switchedModel, "the model must not be reported as switched when it wasn't");
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.ERROR
                        && entry.text().contains("No LLM client available")));
    }

    @Test
    void modelCommandListsModelsFromRealClientWhenNoArgsGiven() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of("model-a", "model-b")));
        panel.open();

        type(panel, "/model");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Current model: test-model")
                        && entry.text().contains("model-a")
                        && entry.text().contains("model-b"))));
    }

    @Test
    void modelCommandShowsOnlyCurrentModelWhenClientReturnsNoModels() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of()));
        panel.open();

        type(panel, "/model");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().equals("Current model: test-model"))));
    }

    @Test
    void modelListingRunsOffTheEventThread() {
        AtomicReference<String> listThread = new AtomicReference<>();
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new LlmClient() {
            @Override
            public List<String> listModels() {
                listThread.set(Thread.currentThread().getName());
                return List.of("model-a");
            }
        });
        panel.open();
        String eventThread = Thread.currentThread().getName();

        type(panel, "/model");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(listThread.get()));
        assertNotEquals(eventThread, listThread.get(),
                "model discovery must not run on the TUI event thread, which reaches a blocking HTTP call");
        assertTrue(listThread.get().contains("model-list"));
    }

    @Test
    void modelCommandPersistsSelectionAcrossRestarts(@TempDir Path tempDir) {
        String originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(tempDir.toString());
        try {
            AiPanel panel = new AiPanel();
            panel.setClientForTesting(new ModelListingLlmClient(List.of("model-a", "model-b")));
            panel.open();

            type(panel, "/model model-b");
            panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

            assertEquals("model-b", TuiSettings.load().getAiModel(),
                    "the /model selection must be persisted so it survives a TUI restart");
            assertTrue(panel.conversationForTesting().stream()
                    .anyMatch(entry -> entry.text().contains("Switched model to model-b")));
        } finally {
            CommandLineHelper.useHomeDir(originalHome);
        }
    }

    @Test
    void unknownCommandRendersHelpHint() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();

        type(panel, "/nope");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.ERROR
                        && entry.text().contains("Type /help for available commands.")));
    }

    @Test
    void providerAndModelWaitWhileBusy() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();

        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        panel.executeSlashCommandForTesting("/provider");

        assertFalse(context.providerSwitchRequested);
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Wait for the current operation to finish")));

        // Stop the blocking agent thread so it does not spin past the test.
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
    }

    @Test
    void providerAndModelWaitWhileCliCommandRuns() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/send direct:foo hello");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        panel.executeSlashCommandForTesting("/provider");
        panel.executeSlashCommandForTesting("/model model-b");

        assertFalse(context.providerSwitchRequested);
        assertNull(context.switchedModel);
        assertTrue(panel.conversationForTesting().stream()
                .filter(entry -> entry.text().contains("Wait for the current operation to finish"))
                .count() >= 2);
        context.completeCli();
    }

    @Test
    void runCommandLaunchesDetachedAndRendersStatus() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        context.launchResult = "Started: route.yaml";
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/run route.yaml");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        // /run detaches through the launch context (not the in-process CLI executor) and adds --logging-color.
        assertEquals(List.of("run", "route.yaml", "--logging-color=true"), context.launchSpec.camelArgs());
        assertEquals("route.yaml", context.launchSpec.displayName());
        assertFalse(panel.isThinkingForTesting(), "detached launch must not lock the panel into thinking");
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.SYSTEM && entry.text().contains("Started: route.yaml")));
    }

    @Test
    void nonZeroCliExitRendersError() throws Exception {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        context.cliResult = new AiCliCommandExecutor.Result("camel infra nope", 2, "Unknown infra\n", 9, false);
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/infra nope");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        context.completeCli();

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.ERROR && entry.text().contains("exit code 2")));
    }

    @Test
    void commandPlaceholderRendersAfterTrailingSpace() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/send ");

        Rect area = new Rect(0, 0, 80, 6);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("/send"));
        assertTrue(rendered.contains("<endpoint> <message text | @file>"));
        // The placeholder hint must render exactly once (guards against duplicate placeholder blocks).
        assertEquals(1, countOccurrences(rendered, "<endpoint> <message text | @file>"));
    }

    @Test
    void commandPlaceholderDisappearsWhenParametersStart() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/send direct:foo");

        Rect area = new Rect(0, 0, 80, 6);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        assertFalse(TuiTestHelper.bufferToString(buffer).contains("<endpoint> <message text | @file>"));
    }

    @Test
    void escapeCancelsRunningCliCommand() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        // /send runs in-process without locking the panel into thinking; Esc still cancels it.
        type(panel, "/send direct:foo hello");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertFalse(panel.isThinkingForTesting());

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        assertTrue(context.cancelRequested);
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.SYSTEM && entry.text().contains("cancelled")));
    }

    @Test
    void cliCompletionDoesNotClearLlmThinkingState() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();

        type(panel, "/send direct:foo hello");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        context.completeCli();

        assertTrue(panel.isThinkingForTesting(), "a CLI completion must not clear an active LLM request");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(panel.isAgentThreadRunningForTesting()));
    }

    @Test
    void escapeDuringOverlapCancelsBothCliAndLlm() {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();

        // A background CLI command and an LLM request run concurrently: /send does not lock the panel into
        // thinking, so a question submitted afterwards starts the agent thread while the CLI is still in flight.
        type(panel, "/send direct:foo hello");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());
        assertTrue(panel.isAgentThreadRunningForTesting());

        // Esc while both are active must cancel the CLI *and* stop the LLM agent, not silently orphan the agent
        // thread while clearing the thinking indicator.
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        assertTrue(context.cancelRequested, "the background CLI command must be cancelled");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(panel.isAgentThreadRunningForTesting(),
                "the LLM agent thread must also stop, leaving no orphan mutating the conversation"));
    }

    @Test
    void functionKeysPassThroughToGlobalHandlers() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();

        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F1, KeyModifiers.NONE)));
        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F2, KeyModifiers.NONE)));
        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F3, KeyModifiers.NONE)));
        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F6, KeyModifiers.NONE)));
        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F12, KeyModifiers.NONE)));
    }

    @Test
    void f8ClosesPanel() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();
        assertTrue(panel.isOpen());

        assertTrue(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.NONE)));
        assertFalse(panel.isOpen());
    }

    @Test
    void functionKeysPassThroughWhileThinking() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        type(panel, "thinking test");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F1, KeyModifiers.NONE)));
        assertFalse(panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.F2, KeyModifiers.NONE)));

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
    }

    @Test
    void inputPromptUsesAccentChevron() {
        AiPanel panel = new AiPanel();
        assertEquals("❯ ", panel.inputPromptForTesting());
    }

    @Test
    void thinkingVerbStaysStableForOneQuestion() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        String first = panel.thinkingVerbForTesting();
        String second = panel.thinkingVerbForTesting();

        assertNotNull(first);
        assertEquals(first, second);
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
    }

    @Test
    void escapeInterruptsThinkingRequest() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        type(panel, "cancel this");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(panel.isThinkingForTesting(), "thinking should stop after Esc"));
        assertFalse(panel.isAgentThreadRunningForTesting(), "agent thread should stop after Esc");
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.role() == AiRole.SYSTEM && "(cancelled)".equals(entry.text())));
    }

    @Test
    void ctrlPBlockedWhileAgentThreadRunning() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        for (char ch : "still running".toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isAgentThreadRunningForTesting());

        panel.handleKeyEvent(KeyEvent.ofChar('p', KeyModifiers.of(true, false, false)));
        assertFalse(panel.isProviderSwitchVisibleForTesting(), "Ctrl+P must wait until the agent thread stops");

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(panel.isAgentThreadRunningForTesting()));

        panel.handleKeyEvent(KeyEvent.ofChar('p', KeyModifiers.of(true, false, false)));
        assertTrue(panel.isProviderSwitchVisibleForTesting());
    }

    @Test
    void providerSwitchAfterCancelStopsAgentThread() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        for (char ch : "cancel then switch".toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(panel.isAgentThreadRunningForTesting()));

        panel.setProviderChoicesForTesting(List.of(
                new AiProviderSwitchPopup.ProviderChoice("openai", "gpt-4o", "", false)));
        panel.handleKeyEvent(KeyEvent.ofChar('p', KeyModifiers.of(true, false, false)));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertFalse(panel.isAgentThreadRunningForTesting());
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Switched to gpt-4o (openai)")));
    }

    @Test
    void renderShowsThinkingStatusOutsideMarkdown() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new BlockingLlmClient());
        panel.open();
        type(panel, "render thinking");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        Rect area = new Rect(0, 0, 80, 12);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);

        assertTrue(rendered.contains(panel.thinkingVerbForTesting()));
        assertFalse(rendered.contains("thinking..."));

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
    }

    @Test
    void longResponseAutoScrollsToShowLastLine() throws Exception {
        AiPanel panel = new AiPanel();
        // Markdown block elements (headings, lists, fenced code, blockquotes) render with surrounding blank rows that a
        // naive per-source-line count ignores, so the old estimate under-counted the height and the auto-scroll clipped
        // the last couple of lines below the visible area.
        String response = "# Routes overview\n"
                          + "Here are the routes:\n"
                          + "## Details\n"
                          + "- route-one does a thing\n"
                          + "- route-two does another thing\n"
                          + "- route-three does yet another\n"
                          + "\n"
                          + "```java\n"
                          + "from(\"timer:foo\").to(\"log:bar\");\n"
                          + "```\n"
                          + "> a blockquote note about the routes\n"
                          + "FINAL_MARKER_LINE";
        RecordingLlmClient client = new RecordingLlmClient(response);
        panel.setClientForTesting(client);
        panel.open();

        type(panel, "show me the routes");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(client.awaitAnswer(5, TimeUnit.SECONDS));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> assertFalse(panel.isAgentThreadRunningForTesting(), "agent thread should finish within 5 seconds"));

        // A short panel forces overflow; scrollOffset defaults to 0 (auto-scroll to the newest content).
        Rect area = new Rect(0, 0, 48, 16);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);

        assertTrue(TuiTestHelper.bufferToString(buffer).contains("FINAL_MARKER"),
                "auto-scroll must keep the most recent response line visible, not clip it below the input bar");
    }

    @Test
    void tabCompletesSingleMatchAndAppendsSpace() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/ru");

        tab(panel);

        // /ru only matches /run, so it completes fully and adds a trailing space ready for arguments.
        assertEquals("/run ", panel.inputBufferForTesting());
    }

    @Test
    void tabCompletesLongestCommonPrefixThenCyclesForward() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/cle");

        // /cle matches /clear and /clear-history, so the first TAB fills in their common prefix.
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());

        // No further prefix can be added, so subsequent TABs cycle through the matches and wrap around.
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear-history", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
    }

    @Test
    void tabCyclesThroughMatchesWhenNoPrefixCanBeAdded() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/cl");

        // /cl matches /clear, /clear-history and /close and is already their common prefix, so TAB cycles.
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear-history", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/close", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
    }

    @Test
    void shiftTabCyclesBackward() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/cl");

        // Shift+TAB selects the last match, then walks backward through the list.
        shiftTab(panel);
        assertEquals("/close", panel.inputBufferForTesting());
        shiftTab(panel);
        assertEquals("/clear-history", panel.inputBufferForTesting());
        shiftTab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
    }

    @Test
    void tabIsNoOpWithoutSlashPrefix() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "hello");

        tab(panel);

        assertEquals("hello", panel.inputBufferForTesting());
    }

    @Test
    void editingResetsCompletionCycle() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/cl");
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());

        // Backspacing breaks the cycle; the next TAB recomputes from the edited buffer.
        // /clea matches /clear and /clear-history, so TAB fills common prefix /clear.
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE));
        assertEquals("/clea", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
        // First cycle step lands on /clear (index 0, same as prefix), next advances to /clear-history.
        tab(panel);
        assertEquals("/clear", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/clear-history", panel.inputBufferForTesting());
    }

    // ---- argument completion tests ----

    @Test
    void tabCompletesModelNameFromProviderList() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of("qwen3.6:35b-a3b", "llama3.3:70b")));
        panel.open();
        type(panel, "/model qw");

        // The first TAB only starts the background fetch of the model list; once it has arrived TAB completes.
        tab(panel);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            tab(panel);
            assertEquals("/model qwen3.6:35b-a3b ", panel.inputBufferForTesting());
        });
    }

    @Test
    void tabCyclesModelsSharingAPrefixAndHonoursAliases() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of("qwen2.5:14b", "qwen2.5:32b", "hermes3:8b")));
        panel.open();
        type(panel, "/m q");

        tab(panel);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            tab(panel);
            assertEquals("/m qwen2.5:", panel.inputBufferForTesting());
        });

        // No further common prefix, so TAB cycles through the matches and wraps around.
        tab(panel);
        assertEquals("/m qwen2.5:14b", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/m qwen2.5:32b", panel.inputBufferForTesting());
        tab(panel);
        assertEquals("/m qwen2.5:14b", panel.inputBufferForTesting());
    }

    @Test
    void tabCompletesToolModeArgument() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/tools c");

        tab(panel);

        assertEquals("/tools core ", panel.inputBufferForTesting());
    }

    @Test
    void tabDoesNotCompleteArgumentsOfOtherCommands() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/run --exam");

        tab(panel);

        assertEquals("/run --exam", panel.inputBufferForTesting());
    }

    // ---- stuck tool loops ----

    @Test
    void repeatedIdenticalToolCallsEndTheTurnWithAnExplanation() throws Exception {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        LoopingLlmClient client = new LoopingLlmClient();
        panel.setClientForTesting(client);
        panel.open();
        type(panel, "send a message to the mqtt topic");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        await().atMost(10, TimeUnit.SECONDS).until(() -> !panel.isAgentThreadRunningForTesting());

        AiPanel.ConversationEntry last = panel.conversationForTesting().get(panel.conversationForTesting().size() - 1);
        assertEquals(AiRole.ERROR, last.role());
        assertTrue(last.text().contains("Reached maximum iterations"), last.text());
        assertTrue(last.text().contains("tui_send_message"), last.text());
        assertTrue(last.text().contains("AI Log"), last.text());
        // after the third identical call the tool is no longer executed; the model is told to stop instead
        assertTrue(client.sawStopNote, "the model must be told to stop repeating the call");
        assertEquals(AiPanel.MAX_IDENTICAL_TOOL_CALLS, client.executedResults,
                "the tool must not run again once the repeat limit is reached");
    }

    /** Always asks for the same tool call, like a model stuck on a failing send. */
    private static final class LoopingLlmClient extends LlmClient {

        volatile boolean sawStopNote;
        volatile int executedResults;

        LoopingLlmClient() {
            withModel("test-model");
            withApiType(ApiType.openai);
        }

        @Override
        public boolean detectEndpoint() {
            return true;
        }

        @Override
        public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
            Message lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.toolResults() != null) {
                for (ToolResult result : lastMessage.toolResults()) {
                    if (result.content().contains("Stop calling tools now")) {
                        sawStopNote = true;
                    } else {
                        executedResults++;
                    }
                }
            }
            JsonObject args = new JsonObject();
            args.put("endpoint", "direct:mqtt");
            args.put("body", "25");
            return new ChatResponse(
                    null, List.of(new ToolCall("call-1", "tui_send_message", args)), "tool_use", false,
                    TokenUsage.EMPTY);
        }
    }

    // ---- /context and /retry ----

    @Test
    void contextDescribesProviderToolsPrefixAndHistory() {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        RecordingLlmClient client = new RecordingLlmClient("ok");
        client.withApiType(LlmClient.ApiType.ollama);
        panel.setClientForTesting(client);

        String context = panel.describeContext();

        assertTrue(context.contains("Provider: ollama"), context);
        assertTrue(context.contains("(local)"), context);
        assertTrue(context.contains("Tools: core ("), context);
        assertTrue(context.contains("Static prefix: ~"), context);
        assertTrue(context.contains("History: 0 turn(s)"), context);
    }

    @Test
    void retryResendsTheLastQuestionFromACleanTurn() throws Exception {
        AiPanel panel = new AiPanel();
        RecordingLlmClient client = new RecordingLlmClient("first answer");
        panel.setClientForTesting(client);
        panel.open();
        type(panel, "what routes are running?");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(client.awaitAnswer(5, TimeUnit.SECONDS));
        await().atMost(5, TimeUnit.SECONDS).until(() -> !panel.isAgentThreadRunningForTesting());
        int messagesAfterFirst = panel.messageCountForTesting();

        assertTrue(panel.retryLastQuestion());
        await().atMost(5, TimeUnit.SECONDS).until(() -> !panel.isAgentThreadRunningForTesting());

        assertEquals("what routes are running?", client.lastQuestion());
        // the retried turn replaced the earlier one in the model history instead of stacking on top of it
        assertEquals(messagesAfterFirst, panel.messageCountForTesting());
        assertEquals(2, panel.conversationForTesting().stream().filter(e -> e.role() == AiRole.USER).count());
    }

    @Test
    void usageSummaryReportsTotalsPerModelAndLastRequest() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new RecordingLlmClient("ok"));
        assertTrue(panel.usageSummary().startsWith("No AI usage yet"));

        panel.recordUsageForTesting(new AiPanel.AiUsageEntry(
                "qwen3.6:35b-a3b", "ollama", 3000, 100, 3100, 5000, "end_turn", Instant.now()));
        panel.recordUsageForTesting(new AiPanel.AiUsageEntry(
                "qwen3.6:35b-a3b", "ollama", 3200, 200, 3400, 2000, "end_turn", Instant.now()));

        String summary = panel.usageSummary();

        assertTrue(summary.startsWith("Requests: 2, tokens: 6.5k (in 6.2k, out 300), avg latency: 3500 ms"), summary);
        assertTrue(summary.contains("- [tui] qwen3.6:35b-a3b (ollama): 2 request(s), 6.5k tokens"), summary);
        assertTrue(summary.contains("Last request: 3.4k tokens in 2000 ms"), summary);
    }

    @Test
    void retryWithoutAQuestionIsRefused() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new RecordingLlmClient("ok"));

        assertFalse(panel.retryLastQuestion());
    }

    // ---- tool set and system prompt tests ----

    @Test
    void localProviderGetsCoreToolsAndHostedProviderGetsAll() {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        RecordingLlmClient client = new RecordingLlmClient("ok");
        panel.setClientForTesting(client);

        // auto mode: a hosted provider gets every tool
        assertEquals(new TuiToolRegistry(null).getToolDefinitions().size(), panel.toolDefinitionsForTesting().size());
        assertTrue(panel.systemPromptForTesting().contains("tui_draw_shape"));

        // auto mode: a local provider only gets the core set, and the prompt no longer suggests drawing tools
        client.withApiType(LlmClient.ApiType.ollama);
        assertEquals(TuiToolRegistry.CORE_TOOLS.size(), panel.toolDefinitionsForTesting().size());
        assertTrue(panel.toolDefinitionsForTesting().stream()
                .allMatch(def -> TuiToolRegistry.CORE_TOOLS.contains(def.name())));
        assertFalse(panel.systemPromptForTesting().contains("tui_draw_shape"));
        assertTrue(panel.describeToolModeForTesting()
                .startsWith("core (" + TuiToolRegistry.CORE_TOOLS.size() + " of "));
    }

    @Test
    void explicitToolModeOverridesProviderDetection() {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        RecordingLlmClient client = new RecordingLlmClient("ok");
        client.withApiType(LlmClient.ApiType.ollama);
        panel.setClientForTesting(client);

        panel.setToolModeForTesting("full");
        assertEquals(new TuiToolRegistry(null).getToolDefinitions().size(), panel.toolDefinitionsForTesting().size());

        panel.setToolModeForTesting("core");
        client.withApiType(LlmClient.ApiType.openai);
        assertEquals(TuiToolRegistry.CORE_TOOLS.size(), panel.toolDefinitionsForTesting().size());
    }

    @Test
    void systemPromptIsStableAndDoesNotRepeatTheToolList() {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setClientForTesting(new RecordingLlmClient("ok"));

        String prompt = panel.systemPromptForTesting();

        // the tool definitions already describe every tool, so the prompt must not list them again
        assertFalse(prompt.contains("- tui_get_table:"));
        assertFalse(prompt.contains("The user is monitoring"));
        assertEquals(prompt, panel.systemPromptForTesting());
    }

    @Test
    void normalizeToolModeAcceptsKnownValuesOnly() {
        assertEquals("auto", AiPanel.normalizeToolMode(null));
        assertEquals("auto", AiPanel.normalizeToolMode("  "));
        assertEquals("core", AiPanel.normalizeToolMode("Core"));
        assertEquals("full", AiPanel.normalizeToolMode("FULL"));
        assertNull(AiPanel.normalizeToolMode("bogus"));
    }

    // ---- paste tests ----

    @Test
    void pasteInsertsTextAtCursor() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "/model ");

        panel.handlePaste("qwen3.6:35b-a3b");

        assertEquals("/model qwen3.6:35b-a3b", panel.inputBufferForTesting());
    }

    @Test
    void pasteInsertsInTheMiddleOfTheBuffer() {
        AiPanel panel = new AiPanel();
        panel.open();
        type(panel, "ac");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));

        panel.handlePaste("b");
        type(panel, "d");

        // The cursor advances past the pasted text so typing continues right after it.
        assertEquals("abdc", panel.inputBufferForTesting());
    }

    @Test
    void pasteCollapsesLineBreaksToSpaces() {
        AiPanel panel = new AiPanel();
        panel.open();

        panel.handlePaste("why is\nthe route\r\nstopped?");

        // A multi-line paste becomes a single prompt instead of submitting on the first newline.
        assertEquals("why is the route stopped?", panel.inputBufferForTesting());
    }

    @Test
    void pasteIsIgnoredWhileClosed() {
        AiPanel panel = new AiPanel();

        panel.handlePaste("ignored");

        assertEquals("", panel.inputBufferForTesting());
    }

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
    }

    private static void tab(AiPanel panel) {
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
    }

    private static void shiftTab(AiPanel panel) {
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static final class RecordingLlmClient extends LlmClient {

        private final String response;
        private final CountDownLatch answered = new CountDownLatch(1);
        private volatile String lastQuestion;

        RecordingLlmClient(String response) {
            this.response = response;
            withModel("test-model");
            withApiType(ApiType.openai);
        }

        @Override
        public boolean detectEndpoint() {
            return true;
        }

        @Override
        public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if ("user".equals(message.role()) && message.content() != null) {
                    lastQuestion = message.content();
                    break;
                }
            }
            answered.countDown();
            return new ChatResponse(response, null, "end_turn", false, TokenUsage.EMPTY);
        }

        String lastQuestion() {
            return lastQuestion;
        }

        boolean awaitAnswer(long timeout, TimeUnit unit) throws InterruptedException {
            return answered.await(timeout, unit);
        }
    }

    private static final class ModelListingLlmClient extends LlmClient {

        private final List<String> models;

        ModelListingLlmClient(List<String> models) {
            this.models = models;
            withModel("test-model");
        }

        @Override
        public List<String> listModels() {
            return models;
        }
    }

    static final class FakeSlashContext implements AiSlashCommandContext {

        @Override
        public String describeToolMode() {
            return "full (46 of 46 tools), mode auto";
        }

        @Override
        public boolean switchToolMode(String mode) {
            return true;
        }

        @Override
        public String describeContext() {
            return "";
        }

        @Override
        public String compactHistoryNow() {
            return "";
        }

        @Override
        public boolean retryLastQuestion() {
            return false;
        }

        @Override
        public String usageSummary() {
            return "";
        }

        @Override
        public void copyLastResponse() {
        }

        @Override
        public void exportConversation() {
        }

        @Override
        public String systemPrompt() {
            return "";
        }

        boolean exitRequested;
        boolean cancelRequested;
        boolean providerSwitchRequested;
        String switchedModel;
        boolean switchModelResult = true;
        AiCliCommandExecutor.Request cliRequest;
        AiSlashCommandRegistry.LaunchSpec launchSpec;
        String launchResult = "Started: example";
        AiCliCommandExecutor.Result cliResult = new AiCliCommandExecutor.Result("", 0, "", 0, false);
        private CompletableFuture<AiCliCommandExecutor.Result> pendingCli;

        @Override
        public void closePanel() {
        }

        @Override
        public void requestExit() {
            exitRequested = true;
        }

        @Override
        public void openProviderSwitch() {
            providerSwitchRequested = true;
        }

        @Override
        public void clearConversation() {
        }

        @Override
        public void clearHistory() {
        }

        @Override
        public String currentModel() {
            return "test-model";
        }

        @Override
        public List<String> availableModels() {
            return List.of("model-a", "model-b");
        }

        @Override
        public boolean switchModel(String model) {
            if (!switchModelResult) {
                return false;
            }
            switchedModel = model;
            return true;
        }

        @Override
        public String selectedProcessName() {
            return null;
        }

        @Override
        public CompletableFuture<AiCliCommandExecutor.Result> executeCli(AiCliCommandExecutor.Request request) {
            cliRequest = request;
            pendingCli = new CompletableFuture<>();
            return pendingCli;
        }

        void completeCli() {
            pendingCli.complete(cliResult);
        }

        @Override
        public void cancelCli() {
            cancelRequested = true;
            if (pendingCli != null) {
                pendingCli.complete(new AiCliCommandExecutor.Result("", 130, "", 0, true));
            }
        }

        @Override
        public String launchDetached(AiSlashCommandRegistry.LaunchSpec spec) {
            launchSpec = spec;
            return launchResult;
        }
    }

    @Test
    void ctrlPOpensProviderSwitchPopupAndSelectionAddsSystemEntry() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();

        panel.setProviderChoicesForTesting(List.of(
                new AiProviderSwitchPopup.ProviderChoice("auto", "", "", true),
                new AiProviderSwitchPopup.ProviderChoice("gemini", "gemini-3.5-flash", "", false)));
        panel.handleKeyEvent(KeyEvent.ofChar('p', KeyModifiers.of(true, false, false)));
        assertTrue(panel.isProviderSwitchVisibleForTesting());

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Switched to gemini-3.5-flash (gemini)")));
    }

    static class BlockingLlmClient extends LlmClient {
        @Override
        public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
            return new ChatResponse(null, List.of(), "error", false, TokenUsage.EMPTY);
        }
    }
}
