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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.awaitility.Awaitility.await;

/**
 * In-memory ACP agent for tests. Talks newline-delimited JSON-RPC over pipes. Request handlers run on their own thread
 * so a handler may itself send notifications and agent-initiated requests (e.g. permission prompts) and wait for the
 * client's answers.
 */
final class FakeAcpAgent implements AutoCloseable {

    private final PipedOutputStream agentOut = new PipedOutputStream();
    private final PipedOutputStream clientOut = new PipedOutputStream();
    private final PipedInputStream clientIn;
    private final PipedInputStream agentIn;
    private final BufferedReader reader;
    private final Writer writer;
    private final Map<String, Function<JsonObject, JsonObject>> handlers = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> errors = new ConcurrentHashMap<>();
    private final List<JsonObject> received = new CopyOnWriteArrayList<>();
    private final Map<Long, CompletableFuture<JsonObject>> pendingAgentRequests = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private final CountDownLatch clientClosed = new CountDownLatch(1);
    private final ExecutorService handlerExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fake-acp-handler");
        t.setDaemon(true);
        return t;
    });

    FakeAcpAgent() throws IOException {
        clientIn = new PipedInputStream(agentOut, 1 << 16);
        agentIn = new PipedInputStream(clientOut, 1 << 16);
        reader = new BufferedReader(new InputStreamReader(agentIn, StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(agentOut, StandardCharsets.UTF_8));
        onRequest("initialize", params -> defaultInitializeResult());
        onRequest("session/new", params -> {
            JsonObject r = new JsonObject();
            r.put("sessionId", "sess-" + ids.incrementAndGet());
            return r;
        });
        onRequest("session/prompt", params -> {
            JsonObject r = new JsonObject();
            r.put("stopReason", "end_turn");
            return r;
        });
        onRequest("authenticate", params -> new JsonObject());
        Thread t = new Thread(this::loop, "fake-acp-reader");
        t.setDaemon(true);
        t.start();
    }

    static JsonObject defaultInitializeResult() {
        JsonObject mcp = new JsonObject();
        mcp.put("http", true);
        mcp.put("sse", false);
        JsonObject caps = new JsonObject();
        caps.put("mcpCapabilities", mcp);
        JsonObject info = new JsonObject();
        info.put("name", "fake-agent");
        info.put("version", "1.2.3");
        JsonObject r = new JsonObject();
        r.put("protocolVersion", 1);
        r.put("agentCapabilities", caps);
        r.put("authMethods", new JsonArray());
        r.put("agentInfo", info);
        return r;
    }

    InputStream clientInput() {
        return clientIn;
    }

    OutputStream clientOutput() {
        return clientOut;
    }

    void onRequest(String method, Function<JsonObject, JsonObject> resultBuilder) {
        errors.remove(method);
        handlers.put(method, resultBuilder);
    }

    /** The next request for {@code method} is answered with this JSON-RPC error once, then the handler applies. */
    void failRequest(String method, int code, String message) {
        JsonObject error = new JsonObject();
        error.put("code", code);
        error.put("message", message);
        errors.put(method, error);
    }

    void sendNotification(String method, JsonObject params) {
        JsonObject msg = new JsonObject();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        msg.put("params", params);
        write(msg);
    }

    /** Agent-initiated request; blocks up to 5 seconds for the client's response and returns it. */
    JsonObject sendRequest(String method, JsonObject params) {
        long id = ids.incrementAndGet();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingAgentRequests.put(id, future);
        JsonObject msg = new JsonObject();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        msg.put("params", params);
        write(msg);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No response to " + method, e);
        }
    }

    void sendRaw(String line) {
        synchronized (writer) {
            try {
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    JsonObject awaitReceived(String method, Duration timeout) {
        await("client never sent " + method).atMost(timeout).until(() -> find(method) != null);
        return find(method);
    }

    /** True when the client closed its end of the pipe within {@code timeout}. */
    boolean awaitClientClosed(Duration timeout) throws InterruptedException {
        return clientClosed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private JsonObject find(String method) {
        return received.stream().filter(m -> method.equals(m.getString("method"))).findFirst().orElse(null);
    }

    long receivedCount(String method) {
        return received.stream().filter(m -> method.equals(m.getString("method"))).count();
    }

    /** Every message the client sent with this method, in arrival order. */
    List<JsonObject> received(String method) {
        return received.stream().filter(m -> method.equals(m.getString("method"))).toList();
    }

    /** Closes the agent side of both pipes: the client sees EOF, like a crashed process. */
    @Override
    public void close() {
        try {
            agentOut.close();
        } catch (IOException ignored) {
        }
        try {
            agentIn.close();
        } catch (IOException ignored) {
        }
        handlerExecutor.shutdownNow();
    }

    private void loop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject msg = Jsoner.deserialize(line, (JsonObject) null);
                if (msg == null) {
                    continue;
                }
                received.add(msg);
                String method = msg.getString("method");
                Object id = msg.get("id");
                if (method != null && id != null) {
                    if (handlerExecutor.isShutdown()) {
                        continue; // closed while a line was still buffered
                    }
                    handlerExecutor.execute(() -> respond(id, method, msg.getJsonObject("params")));
                } else if (method == null && id != null) {
                    CompletableFuture<JsonObject> f = pendingAgentRequests.remove(((Number) id).longValue());
                    if (f != null) {
                        f.complete(msg);
                    }
                }
            }
        } catch (IOException ignored) {
            // pipe closed
        } finally {
            clientClosed.countDown();
        }
    }

    private void respond(Object id, String method, JsonObject params) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        JsonObject error = errors.remove(method);
        if (error != null) {
            response.put("error", error);
        } else {
            Function<JsonObject, JsonObject> handler = handlers.get(method);
            if (handler == null) {
                JsonObject notFound = new JsonObject();
                notFound.put("code", -32601);
                notFound.put("message", "Method not found: " + method);
                response.put("error", notFound);
            } else {
                response.put("result", handler.apply(params != null ? params : new JsonObject()));
            }
        }
        try {
            write(response);
        } catch (IllegalStateException e) {
            // the client closed the pipe mid-flight; nothing to answer
        }
    }

    private void write(JsonObject msg) {
        sendRaw(Jsoner.serialize(msg));
    }
}
