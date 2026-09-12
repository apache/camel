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
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Isolated
class AiPanelAcpTest {

    private static final Duration T = Duration.ofSeconds(5);

    private String originalHome;
    private FakeAcpAgent agent;

    @BeforeEach
    void isolateHome(@TempDir Path tempDir) {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(tempDir.toString());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(originalHome);
    }

    @AfterEach
    void closeAgent() {
        if (agent != null) {
            agent.close();
        }
    }

    private static void type(AiPanel panel, String text) {
        for (char ch : text.toCharArray()) {
            panel.handleKeyEvent(KeyEvent.ofChar(ch));
        }
    }

    private static void enter(AiPanel panel) {
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
    }

    private static boolean hasEntry(AiPanel panel, AiRole role, String fragment) {
        return panel.conversationForTesting().stream()
                .anyMatch(e -> e.role() == role && e.text().contains(fragment));
    }

    /** Text of the last conversation entry with that role. */
    private static String lastEntry(AiPanel panel, AiRole role) {
        return panel.conversationForTesting().stream()
                .filter(e -> e.role() == role)
                .reduce((first, last) -> last)
                .map(AiPanel.ConversationEntry::text)
                .orElseGet(() -> fail("no " + role + " entry in the conversation"));
    }

    /** Panel wired to a fresh fake agent, with the Claude preset selected. */
    private AiPanel acpPanel() throws IOException {
        agent = new FakeAcpAgent();
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setAcpClientFactoryForTesting((preset, cwd) -> {
            AcpAgentClient client = new AcpAgentClient(agent.clientInput(), agent.clientOutput(), s -> {
            });
            client.start();
            return client;
        });
        panel.setMcpUrlSupplierForTestingOrRuntime(() -> "http://127.0.0.1:4242/mcp");
        panel.open();
        panel.selectProviderForTesting("acp:claude");
        return panel;
    }

    private static void ask(AiPanel panel, String text) {
        type(panel, text);
        enter(panel);
    }

    private static void awaitIdle(AiPanel panel) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(panel.isThinkingForTesting()));
    }

    /** Wraps an update for the session the prompt request carried, like a real agent does. */
    private static JsonObject update(JsonObject promptParams, JsonObject update) {
        JsonObject params = new JsonObject();
        params.put("sessionId", promptParams.getString("sessionId"));
        params.put("update", update);
        return params;
    }

    private static JsonObject chunk(String text) {
        JsonObject content = new JsonObject();
        content.put("type", "text");
        content.put("text", text);
        JsonObject u = new JsonObject();
        u.put("sessionUpdate", "agent_message_chunk");
        u.put("content", content);
        return u;
    }

    private static JsonObject stop(String reason) {
        JsonObject r = new JsonObject();
        r.put("stopReason", reason);
        return r;
    }

    private static String promptText(JsonObject promptRequest) {
        JsonArray prompt = promptRequest.getJsonObject("params").getJsonArray("prompt");
        return ((JsonObject) prompt.get(0)).getString("text");
    }

    private static JsonObject permissionParams(String name, String title) {
        return permissionParams(name, title,
                AcpAgentClientTest.option("opt-allow", "Allow once", "allow_once"),
                AcpAgentClientTest.option("opt-always", "Always allow", "allow_always"),
                AcpAgentClientTest.option("opt-reject", "Reject", "reject_once"));
    }

    private static JsonObject permissionParams(String name, String title, JsonObject... offered) {
        JsonObject toolCall = new JsonObject();
        toolCall.put("toolCallId", "t9");
        if (name != null) {
            toolCall.put("name", name);
        }
        toolCall.put("title", title);
        toolCall.put("kind", "other");
        JsonArray options = new JsonArray();
        for (JsonObject option : offered) {
            options.add(option);
        }
        JsonObject params = new JsonObject();
        params.put("sessionId", "sess");
        params.put("toolCall", toolCall);
        params.put("options", options);
        return params;
    }

    /** Makes the fake agent advertise a "review" command via available_commands_update from its session/new handler. */
    private void advertiseReviewCommand() {
        JsonObject review = new JsonObject();
        review.put("name", "review");
        review.put("description", "Review the current changes");
        JsonObject input = new JsonObject();
        input.put("hint", "focus area");
        review.put("input", input);
        JsonArray commands = new JsonArray();
        commands.add(review);
        advertise(commands);
    }

    /** Same, for commands that only carry a name and a description. */
    private void advertiseCommands(String... names) {
        JsonArray commands = new JsonArray();
        for (String name : names) {
            JsonObject command = new JsonObject();
            command.put("name", name);
            command.put("description", "The agent's own /" + name);
            commands.add(command);
        }
        advertise(commands);
    }

    private void advertise(JsonArray commands) {
        agent.onRequest("session/new", params -> {
            JsonObject update = new JsonObject();
            update.put("sessionUpdate", "available_commands_update");
            update.put("availableCommands", commands);
            JsonObject notifyParams = new JsonObject();
            notifyParams.put("sessionId", "sess-cmd");
            notifyParams.put("update", update);
            agent.sendNotification("session/update", notifyParams);
            JsonObject r = new JsonObject();
            r.put("sessionId", "sess-cmd");
            return r;
        });
    }

    /** Prompt handler that asks permission once and reports the client's answer as the stop reason. */
    private void askPermissionDuringPrompt(JsonObject permission) {
        agent.onRequest("session/prompt", params -> {
            JsonObject answer = agent.sendRequest("session/request_permission", permission);
            JsonObject outcome = answer.getJsonObject("result").getJsonObject("outcome");
            String chosen = outcome.getString("optionId");
            return stop(chosen != null ? chosen : outcome.getString("outcome"));
        });
    }

    @Test
    void selectingAnAcpProviderNeedsNoLlmClient() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:claude");
        assertTrue(panel.isAcpProviderForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Selected Claude Code (ACP)"));
    }

    @Test
    void customProviderWithoutCommandIsRejected() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:custom");
        assertFalse(panel.isAcpProviderForTesting());
        assertTrue(hasEntry(panel, AiRole.ERROR, "camel.tui.ai.acp.command"));
    }

    @Test
    void modelCommandExplainsThatTheAgentOwnsTheModel() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:codex");
        type(panel, "/model gpt-5");
        enter(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "configured in the agent"));
    }

    @Test
    void titleShowsTheAgentLabel() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:opencode");
        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        assertTrue(TuiTestHelper.bufferToString(buffer).contains("OpenCode (ACP)"));
    }

    @Test
    void firstPromptStartsTheAgentPassesTheMcpServerAndStreamsTheAnswer() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("session/prompt", params -> {
            agent.sendNotification("session/update", update(params, chunk("Hel")));
            agent.sendNotification("session/update", update(params, chunk("lo")));
            return stop("end_turn");
        });
        ask(panel, "what is wrong?");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Starting Claude Code (ACP)"));
        assertTrue(hasEntry(panel, AiRole.ASSISTANT, "Hello"));
        assertEquals(1, agent.awaitReceived("initialize", T).getJsonObject("params").getInteger("protocolVersion"));
        JsonObject session = agent.awaitReceived("session/new", T).getJsonObject("params");
        JsonObject server = (JsonObject) session.getJsonArray("mcpServers").get(0);
        assertEquals("http://127.0.0.1:4242/mcp", server.getString("url"));
        String text = promptText(agent.awaitReceived("session/prompt", T));
        assertTrue(text.contains("Apache Camel assistant"), "first prompt carries the TUI preamble");
        assertTrue(text.endsWith("what is wrong?"));
        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        assertTrue(TuiTestHelper.bufferToString(buffer).contains("fake-agent 1.2.3"), "title shows the agent");
    }

    @Test
    void laterPromptsSkipThePreamble() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "first");
        awaitIdle(panel);
        ask(panel, "again");
        awaitIdle(panel);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, agent.receivedCount("session/prompt")));
        assertEquals("again", promptText(agent.received("session/prompt").get(1)));
        assertEquals(1, agent.receivedCount("session/new"), "one session for both prompts");
    }

    @Test
    void toolCallsBecomeStatusLinesThatAreUpdatedInPlace() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("session/prompt", params -> {
            JsonObject call = new JsonObject();
            call.put("sessionUpdate", "tool_call");
            call.put("toolCallId", "t1");
            call.put("title", "Read file");
            call.put("kind", "read");
            agent.sendNotification("session/update", update(params, call));
            JsonObject done = new JsonObject();
            done.put("sessionUpdate", "tool_call_update");
            done.put("toolCallId", "t1");
            done.put("status", "completed");
            agent.sendNotification("session/update", update(params, done));
            agent.sendNotification("session/update", update(params, chunk("done")));
            return stop("end_turn");
        });
        ask(panel, "read it");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, TuiIcons.CHECK + " Read file"));
        assertFalse(hasEntry(panel, AiRole.SYSTEM, TuiIcons.GEAR + " Read file"), "running marker replaced");
        assertTrue(hasEntry(panel, AiRole.ASSISTANT, "done"));
    }

    @Test
    void escapeCancelsTheTurn() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("session/prompt", params -> {
            agent.awaitReceived("session/cancel", Duration.ofSeconds(30));
            return stop("cancelled");
        });
        ask(panel, "slow one");
        agent.awaitReceived("session/prompt", T);
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(cancelled)"));
        assertTrue(agent.receivedCount("session/cancel") >= 1);
    }

    @Test
    void clearOpensANewSessionOnTheNextPrompt() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "one");
        awaitIdle(panel);
        String first = panel.acpSessionIdForTesting();
        ask(panel, "/clear");
        ask(panel, "two");
        awaitIdle(panel);
        assertEquals(2, agent.receivedCount("session/new"));
        assertNotEquals(first, panel.acpSessionIdForTesting());
        assertTrue(promptText(agent.awaitReceived("session/prompt", T)).contains("Apache Camel assistant"));
    }

    @Test
    void aJsonRpcErrorKeepsTheSessionAlive() throws Exception {
        AiPanel panel = acpPanel();
        agent.failRequest("session/prompt", -32603, "boom");
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "boom"));
        ask(panel, "again");
        awaitIdle(panel);
        assertEquals(2, agent.receivedCount("session/prompt"), "the second question reaches the same agent");
        assertEquals(1, agent.receivedCount("session/new"), "the session survives a JSON-RPC error");
        assertEquals(1, agent.receivedCount("initialize"), "the agent process survives a JSON-RPC error");
    }

    @Test
    void authRequiredTriggersOneAuthenticateAndARetry() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("initialize", params -> {
            JsonObject result = FakeAcpAgent.defaultInitializeResult();
            JsonObject method = new JsonObject();
            method.put("id", "agent-login");
            method.put("name", "Agent login");
            JsonArray methods = new JsonArray();
            methods.add(method);
            result.put("authMethods", methods);
            return result;
        });
        agent.failRequest("session/new", AcpAgentClient.AUTH_REQUIRED, "Authentication required");
        ask(panel, "hi");
        awaitIdle(panel);
        assertEquals(1, agent.receivedCount("authenticate"));
        assertEquals(2, agent.receivedCount("session/new"));
        assertEquals("agent-login", agent.awaitReceived("authenticate", T).getJsonObject("params").getString("methodId"));
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Authenticating with fake-agent"));
        assertFalse(hasEntry(panel, AiRole.ERROR, "Authentication"));
    }

    @Test
    void authRequiredWithOnlyTerminalMethodsShowsTheLoginHint() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("initialize", params -> {
            JsonObject result = FakeAcpAgent.defaultInitializeResult();
            JsonObject meta = new JsonObject();
            meta.put("type", "terminal");
            JsonObject method = new JsonObject();
            method.put("id", "claude-login");
            method.put("name", "Log in with Claude");
            method.put("_meta", meta);
            JsonArray methods = new JsonArray();
            methods.add(method);
            result.put("authMethods", methods);
            return result;
        });
        agent.failRequest("session/new", AcpAgentClient.AUTH_REQUIRED, "Authentication required");
        ask(panel, "hi");
        awaitIdle(panel);
        assertEquals(0, agent.receivedCount("authenticate"));
        assertTrue(hasEntry(panel, AiRole.ERROR, "ANTHROPIC_API_KEY"));
    }

    @Test
    void protocolVersionMismatchIsReported() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("initialize", params -> {
            JsonObject result = FakeAcpAgent.defaultInitializeResult();
            result.put("protocolVersion", 2);
            return result;
        });
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "protocol version"));
        assertEquals(0, agent.receivedCount("session/new"));
    }

    @Test
    void missingHttpMcpCapabilityIsReported() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("initialize", params -> {
            JsonObject result = FakeAcpAgent.defaultInitializeResult();
            result.getJsonObject("agentCapabilities").getJsonObject("mcpCapabilities").put("http", false);
            return result;
        });
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "HTTP MCP"));
    }

    @Test
    void refusalStopReasonIsAnError() throws Exception {
        AiPanel panel = acpPanel();
        agent.onRequest("session/prompt", params -> stop("refusal"));
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "refused"));
    }

    /** Makes the fake agent report a context usage of that size for the next turn. */
    private void reportUsage(long used, long size) {
        agent.onRequest("session/prompt", params -> {
            JsonObject usage = new JsonObject();
            usage.put("sessionUpdate", "usage_update");
            usage.put("used", used);
            usage.put("size", size);
            agent.sendNotification("session/update", update(params, usage));
            agent.sendNotification("session/update", update(params, chunk("ok")));
            return stop("end_turn");
        });
    }

    @Test
    void usageUpdateFeedsTheTokenCounter() throws Exception {
        AiPanel panel = acpPanel();
        reportUsage(1234, 200000);
        ask(panel, "hi");
        awaitIdle(panel);
        assertEquals(1234, panel.sessionTotalTokensForTesting());
        assertArrayEquals(new long[] { 1234, 200000 }, panel.acpContextForTesting());

        // the agent reports what is in its context now, so the second turn only adds its own 66 tokens
        reportUsage(1300, 200000);
        ask(panel, "and now?");
        awaitIdle(panel);
        assertEquals(1300, panel.sessionTotalTokensForTesting(), "1234 + the 66 this turn added");
        assertArrayEquals(new long[] { 1300, 200000 }, panel.acpContextForTesting());
        ask(panel, "/context");
        String text = lastEntry(panel, AiRole.SYSTEM);
        assertTrue(text.contains("Context: " + LlmClient.formatTokens(1300) + " of " + LlmClient.formatTokens(200000)),
                text);

        // the agent compacted: the context shrank, which is not a negative token spend
        reportUsage(900, 200000);
        ask(panel, "still there?");
        awaitIdle(panel);
        assertEquals(1300, panel.sessionTotalTokensForTesting(), "a smaller context spends nothing");
        assertArrayEquals(new long[] { 900, 200000 }, panel.acpContextForTesting());
    }

    @Test
    void missingMcpServerIsReportedWithoutSpawning() throws Exception {
        AiPanel panel = acpPanel();
        panel.setMcpUrlSupplierForTestingOrRuntime(() -> {
            throw new IOException("bind failed");
        });
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "MCP server"));
        assertEquals(0, agent.receivedCount("initialize"));
    }

    @Test
    void tuiToolCallsAreAutoApprovedByName() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__tui_get_state", "tui_get_state"));
        ask(panel, "hi");
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-always)"), "allow_always preferred");
    }

    @Test
    void tuiToolCallsAreAutoApprovedByTitle() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams(null, "tui_get_state (camel-tui MCP Server)"));
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-always)"));
    }

    @Test
    void mutatingTuiToolOpensThePopupEvenByName() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__camel_control", "camel_control"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"), "camel_control is not read-only");
    }

    @Test
    void executeKindIsNotAutoApprovedEvenForARegisteredTuiTool() throws Exception {
        AiPanel panel = acpPanel();
        JsonObject permission = permissionParams("mcp__camel-tui__tui_get_state", "tui_get_state");
        permission.getJsonObject("toolCall").put("kind", "execute");
        askPermissionDuringPrompt(permission);
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"), "a shell call is never a TUI tool call");
    }

    @Test
    void fileEditWithCamelTuiInThePathIsNotAutoApproved() throws Exception {
        AiPanel panel = acpPanel();
        JsonObject permission = permissionParams(null, "Edit /tmp/camel-tui/route.yaml");
        permission.getJsonObject("toolCall").put("kind", "edit");
        askPermissionDuringPrompt(permission);
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"), "a path is not a tool identity");
    }

    @Test
    void writeFileIsAutoApprovedWhenTheTuiConfirmsTheWriteItself() throws Exception {
        AiPanel panel = acpPanel();
        assertTrue(panel.describeWriteModeForTesting().startsWith("confirm"));
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__camel_write_file", "camel_write_file"));
        ask(panel, "hi");
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-always)"),
                "the confirm dialog asks, a second question here would make the user answer twice");
    }

    @Test
    void writeFileOpensThePopupWhenWritesAreAutomatic() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/write auto");
        assertTrue(panel.describeWriteModeForTesting().startsWith("auto"));
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__camel_write_file", "camel_write_file"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"), "nothing else asks in auto mode");
    }

    @Test
    void evalExpressionIsReadOnly() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__camel_eval_expression", "camel_eval_expression"));
        ask(panel, "hi");
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-always)"));
    }

    @Test
    void preambleTellsTheAgentToEditThroughTheTui() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "hi");
        awaitIdle(panel);
        String prompt = promptText(agent.received("session/prompt").get(0));
        assertTrue(prompt.contains("only with camel_write_file"), prompt);
        assertTrue(prompt.contains("/write live"), prompt);
    }

    @Test
    void unregisteredToolWithCamelTuiTitleIsNotAutoApproved() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams(null, "rm -rf / (camel-tui MCP Server)"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"), "the TUI registers no such tool");
    }

    @Test
    void tuiToolWithoutAllowAlwaysFallsBackToAllowOnce() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__tui_get_state", "tui_get_state",
                AcpAgentClientTest.option("opt-allow", "Allow once", "allow_once"),
                AcpAgentClientTest.option("opt-reject", "Reject", "reject_once")));
        ask(panel, "hi");
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-allow)"));
    }

    @Test
    void tuiToolWithNoAllowOptionsPicksTheFirst() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("mcp__camel-tui__tui_get_state", "tui_get_state",
                AcpAgentClientTest.option("opt-reject", "Reject", "reject_once")));
        ask(panel, "hi");
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"));
    }

    @Test
    void otherToolCallsOpenThePopupAndEnterAnswers() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("Bash", "rm -rf build"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        assertTrue(panel.isThinkingForTesting(), "turn is still in progress while the popup waits");
        enter(panel);
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-allow)"));
    }

    @Test
    void escapeOnThePopupRejects() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("Bash", "rm -rf build"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(stopped: opt-reject)"));
    }

    @Test
    void popupRendersWhileWaiting() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("Bash", "rm -rf build"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        Rect area = new Rect(0, 0, 100, 30);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        assertTrue(TuiTestHelper.bufferToString(buffer).contains("rm -rf build"));
        enter(panel);
        awaitIdle(panel);
    }

    @Test
    void providerSwitchClosesTheAgent() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "hi");
        awaitIdle(panel);
        panel.selectProviderForTesting("anthropic");
        assertFalse(panel.isAcpProviderForTesting());
        assertNull(panel.acpSessionIdForTesting());
        assertTrue(agent.awaitClientClosed(Duration.ofSeconds(5)), "the agent process is closed on a provider switch");
    }

    @Test
    void missingExecutableShowsTheInstallHint() {
        AiPanel panel = new AiPanel();
        panel.setMcpUrlSupplierForTestingOrRuntime(() -> "http://127.0.0.1:4242/mcp");
        TuiSettings settings = TuiSettings.load();
        settings.setAiAcpCommand("definitely-not-a-real-binary-42 --acp");
        settings.save();
        panel.open();
        panel.selectProviderForTesting("acp:custom");
        ask(panel, "hi");
        awaitIdle(panel);
        assertTrue(hasEntry(panel, AiRole.ERROR, "definitely-not-a-real-binary-42 not found"));
    }

    @Test
    void ctrlCWhileThePopupWaitsCancelsTheTurn() throws Exception {
        AiPanel panel = acpPanel();
        askPermissionDuringPrompt(permissionParams("Bash", "rm -rf build"));
        ask(panel, "hi");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(panel.isPermissionPopupVisibleForTesting()));
        panel.handleKeyEvent(KeyEvent.ofChar('c', KeyModifiers.CTRL));
        awaitIdle(panel);
        assertFalse(panel.isPermissionPopupVisibleForTesting());
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "(cancelled)"));
    }

    @Test
    void unknownSlashCommandIsForwardedToTheAgentWithoutThePreamble() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/review src");
        awaitIdle(panel);
        assertEquals("/review src", promptText(agent.received("session/prompt").get(0)));
        ask(panel, "hello");
        awaitIdle(panel);
        assertTrue(promptText(agent.received("session/prompt").get(1)).contains("Apache Camel assistant"),
                "the first regular prompt still carries the preamble");
    }

    @Test
    void panelCommandsStillWinOverTheAgent() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/help");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "/provider"));
        assertEquals(0, agent.receivedCount("session/prompt"));
    }

    @Test
    void headerStripAppearsOnceTheSessionIsOpen() throws Exception {
        AiPanel panel = acpPanel();
        // wide enough that the meta line is never clipped by a deep checkout path
        Rect area = new Rect(0, 0, 200, 20);
        Buffer before = Buffer.empty(area);
        panel.render(Frame.forTesting(before), area);
        assertFalse(TuiTestHelper.bufferToString(before).contains("session "), "no header before the session opens");
        advertiseReviewCommand();
        ask(panel, "hi");
        awaitIdle(panel);
        Buffer after = Buffer.empty(area);
        panel.render(Frame.forTesting(after), area);
        String rendered = TuiTestHelper.bufferToString(after);
        assertTrue(rendered.contains("Claude Code (ACP) · fake-agent 1.2.3"), rendered);
        assertTrue(rendered.contains("session " + panel.acpSessionIdForTesting()), rendered);
        assertTrue(rendered.contains(AcpHeaderStrip.homeRelative(Path.of("").toAbsolutePath())), rendered);
        assertTrue(rendered.contains("1 command"), rendered);
        assertFalse(hasEntry(panel, AiRole.SYSTEM, " connected"), "no banner entry in the conversation any more");
    }

    @Test
    void headerStripIsHiddenOnAShortPanel() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "hi");
        awaitIdle(panel);
        Rect area = new Rect(0, 0, 120, 6);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        assertFalse(TuiTestHelper.bufferToString(buffer).contains("session "));
    }

    @Test
    void agentCommandsAppearInHintsAndTabCompletion() throws Exception {
        AiPanel panel = acpPanel();
        advertiseReviewCommand();
        ask(panel, "hi");
        awaitIdle(panel);
        type(panel, "/rev");
        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        String rendered = TuiTestHelper.bufferToString(buffer);
        assertTrue(rendered.contains("/agent:review focus area"));
        assertTrue(rendered.contains("Review the current changes"));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        assertEquals("/agent:review ", panel.inputBufferForTesting());
    }

    @Test
    void agentPrefixForwardsEvenAPanelCommandName() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/agent:clear");
        awaitIdle(panel);
        assertEquals("/clear", promptText(agent.received("session/prompt").get(0)));
        assertTrue(hasEntry(panel, AiRole.USER, "/agent:clear"), "the panel conversation was not cleared");
    }

    @Test
    void agentPrefixAloneListsTheAgentCommands() throws Exception {
        AiPanel panel = acpPanel();
        advertiseReviewCommand();
        ask(panel, "hi");
        awaitIdle(panel);
        ask(panel, "/agent:");
        Rect area = new Rect(0, 0, 120, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        String listing = TuiTestHelper.bufferToString(buffer);
        assertTrue(listing.lines().anyMatch(
                line -> line.contains("/agent:review") && line.contains("Review the current changes")),
                listing);
        assertEquals(1, agent.receivedCount("session/prompt"), "nothing was sent to the agent");
    }

    @Test
    void agentCommandsAreShownAndCompletedWithThePrefix() throws Exception {
        AiPanel panel = acpPanel();
        advertiseReviewCommand();
        ask(panel, "hi");
        awaitIdle(panel);
        type(panel, "/agent:rev");
        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        panel.render(Frame.forTesting(buffer), area);
        assertTrue(TuiTestHelper.bufferToString(buffer).contains("/agent:review"));
        panel.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        assertEquals("/agent:review ", panel.inputBufferForTesting());
    }

    @Test
    void retryResendsTheLastQuestionToTheAgent() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/retry");
        assertTrue(hasEntry(panel, AiRole.ERROR, "No question to retry"));
        ask(panel, "hello");
        awaitIdle(panel);
        ask(panel, "/retry");
        awaitIdle(panel);
        assertEquals(2, agent.receivedCount("session/prompt"));
        assertEquals("hello", promptText(agent.received("session/prompt").get(1)), "the preamble is not repeated");
        assertEquals(2, panel.conversationForTesting().stream().filter(e -> e.role() == AiRole.USER).count());
    }

    @Test
    void retryReplaysAnAgentCommandWithoutThePrefix() throws Exception {
        AiPanel panel = acpPanel();
        ask(panel, "/agent:clear");
        awaitIdle(panel);
        ask(panel, "/retry");
        awaitIdle(panel);
        assertEquals("/clear", promptText(agent.received("session/prompt").get(1)));
    }

    @Test
    void contextDescribesTheAgentSession() throws Exception {
        AiPanel panel = acpPanel();
        advertiseReviewCommand();
        ask(panel, "/context");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "not started yet"));
        assertEquals(0, agent.receivedCount("session/prompt"));
        ask(panel, "hello");
        awaitIdle(panel);
        ask(panel, "/context");
        String text = lastEntry(panel, AiRole.SYSTEM);
        assertTrue(text.contains("Session: sess-cmd"), text);
        assertTrue(text.contains("MCP: http://127.0.0.1:4242/mcp"), text);
        assertTrue(text.contains("Agent commands: 1"), text);
        assertTrue(text.contains("Preamble: ~"), text);
        assertTrue(text.contains("/compact and /tools do not apply"), text);
        assertEquals(1, agent.receivedCount("session/prompt"), "/context is answered by the panel");
    }

    @Test
    void compactAndToolsExplainThatTheAgentOwnsThem() throws Exception {
        AiPanel panel = acpPanel();
        advertiseCommands("compact");
        ask(panel, "hello");
        awaitIdle(panel);
        ask(panel, "/compact");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "manages its own conversation history"));
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Use /agent:compact"));
        ask(panel, "/tools");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "through the MCP server directly"));
        ask(panel, "/tools core");
        assertTrue(hasEntry(panel, AiRole.ERROR, "only applies to LLM providers"));
        assertNull(TuiSettings.load().getAiTools(), "the LLM tool set was not changed");
        assertEquals(1, agent.receivedCount("session/prompt"), "nothing was forwarded to the agent");
    }

    @Test
    void compactWithoutAnAgentCommandGivesNoHint() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:codex");
        ask(panel, "/compact");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Codex (ACP) manages its own conversation history"));
        assertFalse(hasEntry(panel, AiRole.SYSTEM, "/agent:compact"));
    }

    @Test
    void promptShowsThePreambleSentToTheAgent() {
        AiPanel panel = new AiPanel();
        panel.open();
        panel.selectProviderForTesting("acp:codex");
        ask(panel, "/prompt");
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Sent to Codex (ACP) ahead of the first prompt"));
        assertTrue(hasEntry(panel, AiRole.SYSTEM, "Apache Camel assistant"));
    }
}
