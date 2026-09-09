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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpAgentClientTest {

    private static final Duration T = Duration.ofSeconds(5);

    private FakeAcpAgent agent;
    private AcpAgentClient client;
    private final List<String> diagnostics = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        agent = new FakeAcpAgent();
        client = new AcpAgentClient(agent.clientInput(), agent.clientOutput(), diagnostics::add);
        client.start();
    }

    @AfterEach
    void tearDown() {
        client.close();
        agent.close();
    }

    @Test
    void initializeSendsVersion1WithNoFsOrTerminalCapability() {
        AcpAgentClient.AgentInfo info = client.initialize(T);
        assertEquals(1, info.protocolVersion());
        assertTrue(info.httpMcp());
        assertEquals("fake-agent", info.name());
        assertEquals("1.2.3", info.version());
        JsonObject sent = agent.awaitReceived("initialize", T);
        JsonObject params = sent.getJsonObject("params");
        assertEquals(1, params.getInteger("protocolVersion"));
        JsonObject fs = params.getJsonObject("clientCapabilities").getJsonObject("fs");
        assertFalse(fs.getBoolean("readTextFile"));
        assertFalse(fs.getBoolean("writeTextFile"));
        assertFalse(params.getJsonObject("clientCapabilities").getBoolean("terminal"));
        assertEquals("camel-tui", params.getJsonObject("clientInfo").getString("name"));
    }

    @Test
    void unknownIncomingRequestIsAnsweredWithMethodNotFound() {
        JsonObject params = new JsonObject();
        params.put("path", "/etc/passwd");
        JsonObject response = agent.sendRequest("fs/read_text_file", params);
        assertNotNull(response.getJsonObject("error"));
        assertEquals(AcpAgentClient.METHOD_NOT_FOUND, response.getJsonObject("error").getInteger("code"));
    }

    @Test
    void malformedLineIsSkippedAndReported() {
        agent.sendRaw("this is not json");
        agent.sendRaw("[1,2,3]");
        AcpAgentClient.AgentInfo info = client.initialize(T);
        assertEquals(1, info.protocolVersion());
        assertTrue(diagnostics.stream().anyMatch(d -> d.contains("this is not json")), diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> d.contains("[1,2,3]")), diagnostics.toString());
    }

    @Test
    void requestAfterStreamClosedFailsFastWithConnectionError() {
        agent.close();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !client.isAlive());
        AcpAgentClient.AcpException e = assertThrows(AcpAgentClient.AcpException.class, () -> client.initialize(T));
        assertEquals(AcpAgentClient.CONNECTION, e.code());
    }

    @Test
    void readerSurvivesARejectedIncomingRequestAfterClose() {
        client.close();
        agent.sendRaw("{\"jsonrpc\":\"2.0\",\"id\":77,\"method\":\"session/request_permission\",\"params\":{}}");
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> diagnostics.stream().anyMatch(d -> d.contains("Cannot handle message")));
    }

    @Test
    void streamClosedFailsPendingRequests() {
        agent.onRequest("session/prompt", params -> {
            try {
                new CountDownLatch(1).await(); // an agent that never answers: blocks until the pipe is closed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new JsonObject();
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        Thread closer = new Thread(() -> {
            agent.awaitReceived("session/prompt", T);
            agent.close();
        });
        closer.setDaemon(true);
        closer.start();
        AcpAgentClient.AcpException e = assertThrows(AcpAgentClient.AcpException.class,
                () -> client.prompt(session, "hi", new NoopListener()));
        assertEquals(AcpAgentClient.CONNECTION, e.code());
        assertTrue(e.getMessage().contains("agent exited"), e.getMessage());
        assertFalse(client.isAlive());
    }

    @Test
    void newSessionPassesCwdAndTheTuiMcpServer() {
        client.initialize(T);
        String id = client.newSession(Path.of("/tmp/proj"), "http://127.0.0.1:8123/mcp", T);
        assertTrue(id.startsWith("sess-"));
        JsonObject params = agent.awaitReceived("session/new", T).getJsonObject("params");
        assertEquals("/tmp/proj", params.getString("cwd"));
        JsonObject server = (JsonObject) params.getJsonArray("mcpServers").get(0);
        assertEquals("http", server.getString("type"));
        assertEquals("camel-tui", server.getString("name"));
        assertEquals("http://127.0.0.1:8123/mcp", server.getString("url"));
    }

    @Test
    void authRequiredSurfacesAsAcpExceptionWithCode() {
        client.initialize(T);
        agent.failRequest("session/new", AcpAgentClient.AUTH_REQUIRED, "Authentication required");
        AcpAgentClient.AcpException e = assertThrows(AcpAgentClient.AcpException.class,
                () -> client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T));
        assertEquals(AcpAgentClient.AUTH_REQUIRED, e.code());
        client.authenticate("agent-login", T);
        assertEquals("agent-login", agent.awaitReceived("authenticate", T).getJsonObject("params").getString("methodId"));
    }

    @Test
    void promptStreamsUpdatesInOrderAndReturnsStopReason() {
        agent.onRequest("session/prompt", params -> {
            agent.sendNotification("session/update", update(params.getString("sessionId"), chunk("Hel")));
            agent.sendNotification("session/update", update(params.getString("sessionId"), chunk("lo")));
            JsonObject call = new JsonObject();
            call.put("sessionUpdate", "tool_call");
            call.put("toolCallId", "t1");
            call.put("title", "Read file");
            call.put("kind", "read");
            agent.sendNotification("session/update", update(params.getString("sessionId"), call));
            JsonObject done = new JsonObject();
            done.put("sessionUpdate", "tool_call_update");
            done.put("toolCallId", "t1");
            done.put("status", "completed");
            agent.sendNotification("session/update", update(params.getString("sessionId"), done));
            JsonObject usage = new JsonObject();
            usage.put("sessionUpdate", "usage_update");
            usage.put("used", 1234);
            usage.put("size", 200000);
            agent.sendNotification("session/update", update(params.getString("sessionId"), usage));
            JsonObject unknown = new JsonObject();
            unknown.put("sessionUpdate", "agent_thought_chunk");
            agent.sendNotification("session/update", update(params.getString("sessionId"), unknown));
            JsonObject r = new JsonObject();
            r.put("stopReason", "end_turn");
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        RecordingListener listener = new RecordingListener();
        assertEquals("end_turn", client.prompt(session, "hi", listener));
        assertEquals(List.of("text:Hel", "text:lo", "call:t1:Read file:read", "update:t1:completed", "usage:1234"),
                listener.events);
        JsonObject prompt = agent.awaitReceived("session/prompt", T).getJsonObject("params");
        assertEquals(session, prompt.getString("sessionId"));
        assertEquals("hi", ((JsonObject) prompt.getJsonArray("prompt").get(0)).getString("text"));
    }

    @Test
    void updatesForAnotherSessionAreIgnored() {
        agent.onRequest("session/prompt", params -> {
            agent.sendNotification("session/update", update("another-session", chunk("stale")));
            agent.sendNotification("session/update", update(params.getString("sessionId"), chunk("mine")));
            JsonObject r = new JsonObject();
            r.put("stopReason", "end_turn");
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        RecordingListener listener = new RecordingListener();
        assertEquals("end_turn", client.prompt(session, "hi", listener));
        assertEquals(List.of("text:mine"), listener.events);
    }

    @Test
    void permissionRequestIsAnsweredWithTheHandlerChoice() {
        client.setPermissionHandler((toolCall, options) -> "opt-allow");
        agent.onRequest("session/prompt", params -> {
            JsonObject answer = agent.sendRequest("session/request_permission", permission("Write file", "edit"));
            JsonObject r = new JsonObject();
            r.put("stopReason", answer.getJsonObject("result").getJsonObject("outcome").getString("optionId"));
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        assertEquals("opt-allow", client.prompt(session, "hi", new NoopListener()));
    }

    @Test
    void permissionHandlerReturningNullAnswersCancelled() {
        client.setPermissionHandler((toolCall, options) -> null);
        agent.onRequest("session/prompt", params -> {
            JsonObject answer = agent.sendRequest("session/request_permission", permission("Write file", "edit"));
            JsonObject r = new JsonObject();
            r.put("stopReason", answer.getJsonObject("result").getJsonObject("outcome").getString("outcome"));
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        assertEquals("cancelled", client.prompt(session, "hi", new NoopListener()));
    }

    @Test
    void cancelSendsNotificationAndPromptReturnsCancelled() {
        agent.onRequest("session/prompt", params -> {
            agent.awaitReceived("session/cancel", T);
            JsonObject r = new JsonObject();
            r.put("stopReason", "cancelled");
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        Thread canceller = new Thread(() -> {
            agent.awaitReceived("session/prompt", T);
            client.cancel(session);
        });
        canceller.setDaemon(true);
        canceller.start();
        assertEquals("cancelled", client.prompt(session, "hi", new NoopListener()));
        assertEquals(session, agent.awaitReceived("session/cancel", T).getJsonObject("params").getString("sessionId"));
    }

    @Test
    void interruptedPromptSendsCancelAndReturnsCancelled() throws Exception {
        agent.onRequest("session/prompt", params -> {
            agent.awaitReceived("session/cancel", Duration.ofSeconds(30));
            JsonObject r = new JsonObject();
            r.put("stopReason", "cancelled");
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        String[] result = new String[1];
        Thread caller = new Thread(() -> result[0] = client.prompt(session, "hi", new NoopListener()));
        caller.start();
        agent.awaitReceived("session/prompt", T);
        caller.interrupt();
        caller.join(5_000);
        assertEquals("cancelled", result[0]);
        agent.awaitReceived("session/cancel", T);
        assertEquals(1, agent.receivedCount("session/cancel"));
    }

    @Test
    void nextPromptWaitsForTheCancelledTurnToFinish() throws Exception {
        CountDownLatch lateSent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        agent.onRequest("session/prompt", params -> {
            String sessionId = params.getString("sessionId");
            JsonObject r = new JsonObject();
            if (calls.incrementAndGet() == 1) {
                agent.awaitReceived("session/cancel", Duration.ofSeconds(30));
                agent.sendNotification("session/update", update(sessionId, chunk("late-old")));
                lateSent.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                r.put("stopReason", "cancelled");
                return r;
            }
            agent.sendNotification("session/update", update(sessionId, chunk("new")));
            r.put("stopReason", "end_turn");
            return r;
        });
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        // the first turn runs on the test thread, like cancelSendsNotificationAndPromptReturnsCancelled: a piped
        // stream breaks as soon as the thread that last wrote to it dies, so the writer has to outlive the test
        Thread caller = Thread.currentThread();
        Thread esc = new Thread(() -> {
            agent.awaitReceived("session/prompt", T);
            caller.interrupt();
        });
        esc.setDaemon(true);
        esc.start();
        assertEquals("cancelled", client.prompt(session, "one", new NoopListener()));
        assertTrue(Thread.interrupted(), "prompt leaves the caller interrupted");
        RecordingListener second = new RecordingListener();
        String[] result = new String[1];
        Thread next = new Thread(() -> result[0] = client.prompt(session, "two", second));
        next.start();
        assertTrue(lateSent.await(5, TimeUnit.SECONDS));
        assertEquals(1, agent.receivedCount("session/prompt"), "the second prompt waits for the cancelled turn");
        release.countDown();
        next.join(10_000);
        assertEquals("end_turn", result[0]);
        assertEquals(List.of("text:new"), second.events);
        assertEquals(2, agent.receivedCount("session/prompt"));
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void spawnedProcessExitReportsCodeAndStderr() throws Exception {
        AcpAgentClient spawned = AcpAgentClient.spawn(
                List.of("sh", "-c", "echo boom >&2; exit 3"), Path.of("."), diagnostics::add);
        try {
            AcpAgentClient.AcpException e = assertThrows(AcpAgentClient.AcpException.class,
                    () -> spawned.initialize(T));
            assertTrue(e.getMessage().contains("code 3"), e.getMessage());
            assertTrue(e.getMessage().contains("boom"), e.getMessage());
            assertFalse(spawned.isAlive());
        } finally {
            spawned.close();
        }
    }

    @Test
    void availableCommandsUpdateIsStoredEvenWithoutAPromptInFlight() {
        agent.onRequest("session/new", params -> {
            JsonObject review = new JsonObject();
            review.put("name", "review");
            review.put("description", "Review the current changes");
            JsonObject input = new JsonObject();
            input.put("hint", "focus area");
            review.put("input", input);
            JsonObject commit = new JsonObject();
            commit.put("name", "commit");
            commit.put("description", "Commit staged changes");
            JsonArray commands = new JsonArray();
            commands.add(review);
            commands.add(commit);
            JsonObject update = new JsonObject();
            update.put("sessionUpdate", "available_commands_update");
            update.put("availableCommands", commands);
            JsonObject params2 = new JsonObject();
            params2.put("sessionId", "sess-1");
            params2.put("update", update);
            agent.sendNotification("session/update", params2);
            JsonObject r = new JsonObject();
            r.put("sessionId", "sess-1");
            return r;
        });
        client.initialize(T);
        client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        await().atMost(5, TimeUnit.SECONDS).until(() -> client.availableCommands().size() == 2);
        AcpAgentClient.AgentCommand review = client.availableCommands().get(0);
        assertEquals("review", review.name());
        assertEquals("Review the current changes", review.description());
        assertEquals("focus area", review.hint());
        assertNull(client.availableCommands().get(1).hint());
    }

    @Test
    void commandsFromAnotherSessionAreIgnored() {
        client.initialize(T);
        String session = client.newSession(Path.of("."), "http://127.0.0.1:1/mcp", T);
        JsonObject review = new JsonObject();
        review.put("name", "review");
        JsonArray reviewCommands = new JsonArray();
        reviewCommands.add(review);
        JsonObject staleUpdate = new JsonObject();
        staleUpdate.put("sessionUpdate", "available_commands_update");
        staleUpdate.put("availableCommands", reviewCommands);
        agent.sendNotification("session/update", update("sess-OLD", staleUpdate));
        JsonObject commit = new JsonObject();
        commit.put("name", "commit");
        JsonArray commitCommands = new JsonArray();
        commitCommands.add(commit);
        JsonObject currentUpdate = new JsonObject();
        currentUpdate.put("sessionUpdate", "available_commands_update");
        currentUpdate.put("availableCommands", commitCommands);
        agent.sendNotification("session/update", update(session, currentUpdate));
        await().atMost(5, TimeUnit.SECONDS).until(() -> client.availableCommands().size() == 1);
        assertEquals("commit", client.availableCommands().get(0).name());
    }

    private static JsonObject update(String sessionId, JsonObject update) {
        JsonObject params = new JsonObject();
        params.put("sessionId", sessionId);
        params.put("update", update);
        return params;
    }

    private static JsonObject chunk(String text) {
        JsonObject content = new JsonObject();
        content.put("type", "text");
        content.put("text", text);
        JsonObject update = new JsonObject();
        update.put("sessionUpdate", "agent_message_chunk");
        update.put("content", content);
        return update;
    }

    static JsonObject permission(String title, String kind) {
        JsonObject toolCall = new JsonObject();
        toolCall.put("toolCallId", "t9");
        toolCall.put("title", title);
        toolCall.put("kind", kind);
        JsonArray options = new JsonArray();
        options.add(option("opt-allow", "Allow", "allow_once"));
        options.add(option("opt-always", "Always allow", "allow_always"));
        options.add(option("opt-reject", "Reject", "reject_once"));
        JsonObject params = new JsonObject();
        params.put("sessionId", "sess");
        params.put("toolCall", toolCall);
        params.put("options", options);
        return params;
    }

    static JsonObject option(String id, String name, String kind) {
        JsonObject o = new JsonObject();
        o.put("optionId", id);
        o.put("name", name);
        o.put("kind", kind);
        return o;
    }

    static final class RecordingListener implements AcpAgentClient.Listener {
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void onTextChunk(String text) {
            events.add("text:" + text);
        }

        @Override
        public void onToolCall(String toolCallId, String title, String kind, JsonObject rawInput) {
            events.add("call:" + toolCallId + ":" + title + ":" + kind);
        }

        @Override
        public void onToolCallUpdate(String toolCallId, String status, String contentText) {
            events.add("update:" + toolCallId + ":" + status);
        }

        @Override
        public void onUsage(long used, long size) {
            events.add("usage:" + used);
        }
    }

    static final class NoopListener implements AcpAgentClient.Listener {
        @Override
        public void onTextChunk(String text) {
        }

        @Override
        public void onToolCall(String toolCallId, String title, String kind, JsonObject rawInput) {
        }

        @Override
        public void onToolCallUpdate(String toolCallId, String status, String contentText) {
        }

        @Override
        public void onUsage(long used, long size) {
        }
    }
}
