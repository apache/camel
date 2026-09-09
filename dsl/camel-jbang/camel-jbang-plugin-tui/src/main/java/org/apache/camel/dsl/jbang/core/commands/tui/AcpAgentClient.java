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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Minimal Agent Client Protocol (ACP) version 1 client: newline-delimited JSON-RPC 2.0 over an agent process's stdin
 * and stdout. Implements only what the AI panel needs: initialize, authenticate, session/new, session/prompt,
 * session/cancel, the session/update notification and the session/request_permission callback. Every other
 * agent-initiated request is answered with "method not found".
 */
final class AcpAgentClient implements AutoCloseable {

    static final int PROTOCOL_VERSION = 1;
    static final int AUTH_REQUIRED = -32000;
    static final int METHOD_NOT_FOUND = -32601;
    /** Local error codes (never sent on the wire). */
    static final int CONNECTION = -1;
    static final int TIMEOUT = -2;
    static final int INTERRUPTED = -3;
    private static final int STDERR_TAIL_LINES = 50;

    /** Receives session/update notifications during a prompt turn. Called on the reader thread. */
    interface Listener {
        void onTextChunk(String text);

        void onToolCall(String toolCallId, String title, String kind, JsonObject rawInput);

        void onToolCallUpdate(String toolCallId, String status, String contentText);

        void onUsage(long used, long size);
    }

    /** Answers session/request_permission. Returns the chosen optionId, or null to answer "cancelled". */
    interface PermissionHandler {
        String decide(JsonObject toolCall, List<JsonObject> options);
    }

    record AuthMethod(String id, String name, String description, String type) {
    }

    record AgentInfo(int protocolVersion, String name, String version, boolean httpMcp, List<AuthMethod> authMethods) {
        String label() {
            if (name == null) {
                return "ACP agent";
            }
            return version == null ? name : name + " " + version;
        }
    }

    /** A slash command advertised by the agent through {@code available_commands_update}. */
    record AgentCommand(String name, String description, String hint) {
    }

    static final class AcpException extends RuntimeException {
        private final int code;

        AcpException(int code, String message) {
            super(message);
            this.code = code;
        }

        int code() {
            return code;
        }
    }

    private final BufferedReader reader;
    private final Writer writer;
    private final Process process;
    private final Consumer<String> diagnostics;
    private final Deque<String> stderrTail = new ArrayDeque<>();
    private final Map<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final ExecutorService requestExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "tui-acp-requests"));
    private final CountDownLatch streamClosed = new CountDownLatch(1);
    private volatile Listener listener;
    private volatile String listenerSession;
    private volatile PermissionHandler permissionHandler = (toolCall, options) -> null;
    private volatile boolean closed;
    private volatile Thread stderrThread;
    private volatile AcpException exitFailure;
    private volatile List<AgentCommand> availableCommands = List.of();
    private volatile String currentSession;

    AcpAgentClient(InputStream fromAgent, OutputStream toAgent, Consumer<String> diagnostics) {
        this(fromAgent, toAgent, null, diagnostics);
    }

    private AcpAgentClient(InputStream fromAgent, OutputStream toAgent, Process process, Consumer<String> diagnostics) {
        this.reader = new BufferedReader(new InputStreamReader(fromAgent, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(toAgent, StandardCharsets.UTF_8));
        this.process = process;
        this.diagnostics = diagnostics != null ? diagnostics : s -> {
        };
    }

    /** Starts {@code command} in {@code cwd}, wires its stdin/stdout to a client and starts reading. */
    static AcpAgentClient spawn(List<String> command, Path cwd, Consumer<String> diagnostics) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        Process process = builder.start();
        AcpAgentClient client = new AcpAgentClient(process.getInputStream(), process.getOutputStream(), process, diagnostics);
        client.drainStderr(process.getErrorStream());
        client.start();
        return client;
    }

    private void drainStderr(InputStream err) {
        stderrThread = daemon(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (stderrTail) {
                        stderrTail.addLast(line);
                        if (stderrTail.size() > STDERR_TAIL_LINES) {
                            stderrTail.removeFirst();
                        }
                    }
                }
            } catch (IOException ignored) {
                // process gone
            }
        }, "tui-acp-stderr");
        stderrThread.start();
    }

    void start() {
        daemon(this::readLoop, "tui-acp-reader").start();
    }

    void setPermissionHandler(PermissionHandler handler) {
        this.permissionHandler = handler != null ? handler : (toolCall, options) -> null;
    }

    boolean isAlive() {
        return !closed && (process == null || process.isAlive());
    }

    String stderrTail() {
        synchronized (stderrTail) {
            return String.join("\n", stderrTail);
        }
    }

    /** The agent's current slash commands (Claude Code's include its skills); empty until the agent advertises them. */
    List<AgentCommand> availableCommands() {
        return availableCommands;
    }

    // ---- ACP calls ----

    AgentInfo initialize(Duration timeout) {
        JsonObject fs = new JsonObject();
        fs.put("readTextFile", false);
        fs.put("writeTextFile", false);
        JsonObject capabilities = new JsonObject();
        capabilities.put("fs", fs);
        capabilities.put("terminal", false);
        JsonObject clientInfo = new JsonObject();
        clientInfo.put("name", "camel-tui");
        clientInfo.put("title", "Apache Camel TUI");
        clientInfo.put("version", VersionHelper.getJBangVersion());
        JsonObject params = new JsonObject();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("clientCapabilities", capabilities);
        params.put("clientInfo", clientInfo);
        JsonObject result = request("initialize", params, timeout);

        int version = result.getIntegerOrDefault("protocolVersion", -1);
        JsonObject agentCapabilities = result.getJsonObject("agentCapabilities");
        JsonObject mcp = agentCapabilities != null ? agentCapabilities.getJsonObject("mcpCapabilities") : null;
        boolean http = mcp != null && mcp.getBooleanOrDefault("http", false);
        JsonObject agentInfo = result.getJsonObject("agentInfo");
        List<AuthMethod> methods = new ArrayList<>();
        JsonArray authMethods = result.getJsonArray("authMethods");
        if (authMethods != null) {
            for (Object o : authMethods) {
                if (o instanceof JsonObject m) {
                    JsonObject meta = m.get("_meta") instanceof JsonObject mo ? mo : null;
                    String type = meta != null && meta.getString("type") != null ? meta.getString("type") : m.getString("type");
                    methods.add(new AuthMethod(m.getString("id"), m.getString("name"), m.getString("description"), type));
                }
            }
        }
        return new AgentInfo(
                version,
                agentInfo != null ? agentInfo.getString("name") : null,
                agentInfo != null ? agentInfo.getString("version") : null,
                http, List.copyOf(methods));
    }

    // ---- ACP calls (continued) ----

    void authenticate(String methodId, Duration timeout) {
        JsonObject params = new JsonObject();
        params.put("methodId", methodId);
        request("authenticate", params, timeout);
    }

    String newSession(Path cwd, String mcpUrl, Duration timeout) {
        JsonObject server = new JsonObject();
        server.put("type", "http");
        server.put("name", "camel-tui");
        server.put("url", mcpUrl);
        server.put("headers", new JsonArray());
        JsonArray servers = new JsonArray();
        servers.add(server);
        JsonObject params = new JsonObject();
        params.put("cwd", cwd.toAbsolutePath().toString());
        params.put("mcpServers", servers);
        JsonObject result = request("session/new", params, timeout);
        String sessionId = result.getString("sessionId");
        if (sessionId == null) {
            throw new AcpException(CONNECTION, "session/new returned no sessionId");
        }
        currentSession = sessionId;
        return sessionId;
    }

    /**
     * Sends one user message and blocks until the agent ends the turn. Updates are delivered to {@code listener} on the
     * reader thread while this call blocks. Interrupting the calling thread sends session/cancel and returns
     * "cancelled" without waiting for the agent; the calling thread's interrupt flag stays set in that case. Only one
     * prompt may be in flight per client, because the client keeps a single listener for the turn.
     */
    String prompt(String sessionId, String text, Listener listener) {
        this.listenerSession = sessionId;
        this.listener = listener;
        try {
            JsonObject block = new JsonObject();
            block.put("type", "text");
            block.put("text", text);
            JsonArray prompt = new JsonArray();
            prompt.add(block);
            JsonObject params = new JsonObject();
            params.put("sessionId", sessionId);
            params.put("prompt", prompt);
            JsonObject result = request("session/prompt", params, null);
            return result.getStringOrDefault("stopReason", "end_turn");
        } catch (AcpException e) {
            if (e.code() == INTERRUPTED) {
                cancel(sessionId);
                return "cancelled";
            }
            throw e;
        } finally {
            this.listener = null;
            this.listenerSession = null;
        }
    }

    void cancel(String sessionId) {
        JsonObject params = new JsonObject();
        params.put("sessionId", sessionId);
        try {
            notify("session/cancel", params);
        } catch (AcpException e) {
            diagnostics.accept("Cannot send session/cancel: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        closed = true;
        currentSession = null;
        requestExecutor.shutdownNow();
        synchronized (writer) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- JSON-RPC plumbing ----

    private void awaitStreamClosed() {
        try {
            streamClosed.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    JsonObject request(String method, JsonObject params, Duration timeout) {
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);
        JsonObject msg = new JsonObject();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        if (params != null) {
            msg.put("params", params);
        }
        if (closed) {
            pending.remove(id);
            throw exitFailure != null ? exitFailure : new AcpException(CONNECTION, "agent exited");
        }
        try {
            send(msg);
        } catch (AcpException e) {
            pending.remove(id);
            if (process != null) {
                awaitStreamClosed();
                if (exitFailure != null) {
                    throw exitFailure;
                }
            }
            throw e;
        }
        try {
            return timeout == null ? future.get() : future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof AcpException acp ? acp : new AcpException(CONNECTION, String.valueOf(cause));
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new AcpException(TIMEOUT, method + " timed out after " + timeout.toSeconds() + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            throw new AcpException(INTERRUPTED, method + " interrupted");
        }
    }

    void notify(String method, JsonObject params) {
        JsonObject msg = new JsonObject();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        if (params != null) {
            msg.put("params", params);
        }
        send(msg);
    }

    private void send(JsonObject msg) {
        String line = Jsoner.serialize(msg);
        synchronized (writer) {
            try {
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                throw new AcpException(CONNECTION, "Cannot write to agent: " + e.getMessage());
            }
        }
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonObject msg = Jsoner.deserialize(line, (JsonObject) null);
                if (msg == null || !msg.containsKey("jsonrpc")) {
                    diagnostics.accept("Ignoring malformed line from agent: " + abbreviate(line));
                    continue;
                }
                try {
                    dispatch(msg);
                } catch (RuntimeException e) {
                    diagnostics.accept("Cannot handle message from agent: " + e);
                }
            }
        } catch (IOException e) {
            if (!closed) {
                diagnostics.accept("Agent stream error: " + e.getMessage());
            }
        } finally {
            onStreamClosed();
        }
    }

    private void dispatch(JsonObject msg) {
        Object id = msg.get("id");
        String method = msg.getString("method");
        if (method == null && id != null) {
            if (!(id instanceof Number number)) {
                return;
            }
            CompletableFuture<JsonObject> future = pending.remove(number.longValue());
            if (future == null) {
                return;
            }
            JsonObject error = msg.getJsonObject("error");
            if (error != null) {
                future.completeExceptionally(new AcpException(
                        error.getIntegerOrDefault("code", CONNECTION), error.getStringOrDefault("message", "error")));
            } else {
                JsonObject result = msg.getJsonObject("result");
                future.complete(result != null ? result : new JsonObject());
            }
        } else if (method != null && id != null) {
            JsonObject params = msg.getJsonObject("params");
            requestExecutor.execute(() -> handleRequest(id, method, params));
        } else if (method != null) {
            handleNotification(method, msg.getJsonObject("params"));
        }
    }

    private void handleRequest(Object id, String method, JsonObject params) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        if ("session/request_permission".equals(method) && params != null) {
            JsonObject toolCall = params.getJsonObject("toolCall");
            List<JsonObject> options = new ArrayList<>();
            JsonArray array = params.getJsonArray("options");
            if (array != null) {
                for (Object o : array) {
                    if (o instanceof JsonObject option) {
                        options.add(option);
                    }
                }
            }
            String optionId;
            try {
                optionId = permissionHandler.decide(toolCall != null ? toolCall : new JsonObject(), options);
            } catch (RuntimeException e) {
                diagnostics.accept("Permission handler failed: " + e.getMessage());
                optionId = null;
            }
            JsonObject outcome = new JsonObject();
            if (optionId == null) {
                outcome.put("outcome", "cancelled");
            } else {
                outcome.put("outcome", "selected");
                outcome.put("optionId", optionId);
            }
            JsonObject result = new JsonObject();
            result.put("outcome", outcome);
            response.put("result", result);
        } else {
            JsonObject error = new JsonObject();
            error.put("code", METHOD_NOT_FOUND);
            error.put("message", "Method not found: " + method);
            response.put("error", error);
        }
        try {
            send(response);
        } catch (AcpException e) {
            diagnostics.accept("Cannot answer " + method + ": " + e.getMessage());
        }
    }

    private void handleNotification(String method, JsonObject params) {
        if (!"session/update".equals(method) || params == null) {
            return;
        }
        JsonObject update = params.getJsonObject("update");
        if (update == null) {
            return;
        }
        String kind = update.getStringOrDefault("sessionUpdate", "");
        if ("available_commands_update".equals(kind)) {
            String current = currentSession;
            String updateSession = params.getString("sessionId");
            if (current != null && updateSession != null && !current.equals(updateSession)) {
                // an update from a session abandoned (for example after /clear) must not reach the new session
                return;
            }
            List<AgentCommand> commands = new ArrayList<>();
            if (update.get("availableCommands") instanceof JsonArray array) {
                for (Object o : array) {
                    if (o instanceof JsonObject c && c.getString("name") != null) {
                        JsonObject input = c.get("input") instanceof JsonObject in ? in : null;
                        commands.add(new AgentCommand(
                                c.getString("name"), c.getStringOrDefault("description", ""),
                                input != null ? input.getString("hint") : null));
                    }
                }
            }
            availableCommands = List.copyOf(commands);
            return;
        }
        Listener target = listener;
        String session = listenerSession;
        if (target == null) {
            return;
        }
        String updateSession = params.getString("sessionId");
        if (session != null && updateSession != null && !session.equals(updateSession)) {
            // an update from an abandoned session (for example after /clear) must not reach the current turn
            return;
        }
        switch (kind) {
            case "agent_message_chunk" -> {
                JsonObject content = update.getJsonObject("content");
                if (content != null && "text".equals(content.getString("type"))) {
                    target.onTextChunk(content.getStringOrDefault("text", ""));
                }
            }
            case "tool_call" -> {
                Object raw = update.get("rawInput");
                target.onToolCall(update.getString("toolCallId"), update.getStringOrDefault("title", "tool"),
                        update.getStringOrDefault("kind", "other"), raw instanceof JsonObject jo ? jo : null);
            }
            case "tool_call_update" -> target.onToolCallUpdate(update.getString("toolCallId"),
                    update.getStringOrDefault("status", ""), textOf(update.get("content")));
            case "usage_update" -> target.onUsage(update.getLongOrDefault("used", 0), update.getLongOrDefault("size", 0));
            default -> {
                // thoughts, plans, mode/command/config updates and unknown kinds are ignored on purpose
            }
        }
    }

    /** Concatenates the text of {@code {type:"content", content:{type:"text", text}}} blocks; null when none. */
    private static String textOf(Object content) {
        if (!(content instanceof JsonArray blocks)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : blocks) {
            if (o instanceof JsonObject block && block.get("content") instanceof JsonObject inner
                    && "text".equals(inner.getString("type"))) {
                sb.append(inner.getStringOrDefault("text", ""));
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void onStreamClosed() {
        try {
            Thread drain = stderrThread;
            if (drain != null) {
                try {
                    drain.join(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String reason = "agent exited";
            if (process != null) {
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!process.isAlive()) {
                    reason = "agent exited with code " + process.exitValue();
                }
            }
            String tail = stderrTail();
            if (!tail.isBlank()) {
                reason += "\n" + tail;
            }
            AcpException failure = new AcpException(CONNECTION, reason);
            exitFailure = failure;
            closed = true;
            for (CompletableFuture<JsonObject> future : pending.values()) {
                future.completeExceptionally(failure);
            }
            pending.clear();
        } finally {
            streamClosed.countDown();
        }
    }

    private static String abbreviate(String line) {
        return line.length() <= 120 ? line : line.substring(0, 117) + "...";
    }

    private static Thread daemon(Runnable task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        return t;
    }
}
