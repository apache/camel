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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (panel.isAgentThreadRunningForTesting() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(panel.isAgentThreadRunningForTesting(), "agent thread should finish within 5 seconds");
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

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Current model: test-model")
                        && entry.text().contains("model-a")));

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

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Current model: test-model")
                        && entry.text().contains("model-a")
                        && entry.text().contains("model-b")));
    }

    @Test
    void modelCommandShowsOnlyCurrentModelWhenClientReturnsNoModels() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(new ModelListingLlmClient(List.of()));
        panel.open();

        type(panel, "/model");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().equals("Current model: test-model")));
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

        assertFalse(panel.isThinkingForTesting());
        assertFalse(panel.isAgentThreadRunningForTesting(), "Esc should wait for the agent thread to stop");
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
        assertFalse(panel.isAgentThreadRunningForTesting());

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

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
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
