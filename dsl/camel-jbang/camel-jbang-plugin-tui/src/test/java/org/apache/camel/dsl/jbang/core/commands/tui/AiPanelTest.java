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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(panel.conversationForTesting().stream().anyMatch(entry -> "user".equals(entry.role())));
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
                .anyMatch(entry -> "system".equals(entry.role()) && entry.text().contains("/run <camel run args>")));
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> "system".equals(entry.role()) && entry.text().contains("/provider")
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
    void unknownCommandRendersHelpHint() {
        AiPanel panel = new AiPanel();
        panel.setClientForTesting(LlmClient.create());
        panel.open();

        type(panel, "/nope");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> "error".equals(entry.role())
                        && entry.text().contains("Type /help for available commands.")));
    }

    @Test
    void providerAndModelWaitWhileBusy() throws Exception {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/run route.yaml");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        panel.executeSlashCommandForTesting("/provider");

        assertFalse(context.providerSwitchRequested);
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> entry.text().contains("Wait for the current operation to finish")));
    }

    @Test
    void runCommandExecutesThroughContextAndRendersOutput() throws Exception {
        AiPanel panel = new AiPanel();
        FakeSlashContext context = new FakeSlashContext();
        context.cliResult = new AiCliCommandExecutor.Result("camel run route.yaml", 0, "Started\n", 12, false);
        panel.setSlashCommandContextForTesting(context);
        panel.open();

        type(panel, "/run route.yaml");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        context.completeCli();

        assertEquals(List.of("run", "route.yaml"), context.cliRequest.argv());
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> "system".equals(entry.role()) && entry.text().contains("Started")));
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
                .anyMatch(entry -> "error".equals(entry.role()) && entry.text().contains("exit code 2")));
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

        type(panel, "/run route.yaml --dev");
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(panel.isThinkingForTesting());

        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));

        assertTrue(context.cancelRequested);
        assertTrue(panel.conversationForTesting().stream()
                .anyMatch(entry -> "system".equals(entry.role()) && entry.text().contains("cancelled")));
    }

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
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

    static final class FakeSlashContext implements AiSlashCommandContext {

        boolean exitRequested;
        boolean cancelRequested;
        boolean providerSwitchRequested;
        String switchedModel;
        AiCliCommandExecutor.Request cliRequest;
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
        public void switchModel(String model) {
            switchedModel = model;
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
    }
}
