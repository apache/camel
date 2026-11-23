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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkus.websockets.next.WebSocketConnection;
import org.apache.camel.util.json.JsonObject;
import org.jboss.logging.Logger;

/** One WebSocket session to an integration. The entry survives reconnects; this session does not. */
final class RemoteRuntimeConnection {

    private static final Logger LOG = Logger.getLogger(RemoteRuntimeConnection.class);
    private static final int MAX_PENDING_ACTIONS = 64;
    private static final int MAX_HELLO_CHARS = 64 * 1024;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Set<String> SNAPSHOT_KINDS
            = Set.of("status", "trace", "receive", "debug", "history", "error", "timeline");

    private final WebSocketConnection socket;
    private final RemoteRuntimeConfig config;
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final Semaphore actionSlots = new Semaphore(MAX_PENDING_ACTIONS);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile RemoteRuntimeEntry entry;
    private volatile boolean helloReceived;

    RemoteRuntimeConnection(WebSocketConnection socket, RemoteRuntimeConfig config) {
        this.socket = socket;
        this.config = config;
    }

    void attach(RemoteRuntimeEntry entry) {
        this.entry = entry;
    }

    boolean isOpen() {
        return !closed.get() && socket.isOpen();
    }

    boolean isReady() {
        return isOpen() && helloReceived;
    }

    void touch() {
        RemoteRuntimeEntry current = entry;
        if (current != null) {
            current.touch();
        }
    }

    void acceptText(String text) {
        if (text == null || text.length() > config.maxFrameChars()) {
            throw new IllegalArgumentException("WebSocket frame exceeds the configured size limit");
        }
        JsonObject frame = CliConnectorProtocol.parseFrame(text);
        String type = frame.getString("type");
        RemoteRuntimeEntry current = entry;
        if (current == null) {
            throw new IllegalStateException("WebSocket session is not registered");
        }
        if (!current.isCurrent(this)) {
            throw new IllegalStateException("This WebSocket session has been replaced");
        }
        switch (type) {
            case "hello" -> {
                if (text.length() > MAX_HELLO_CHARS) {
                    throw new IllegalArgumentException("The hello frame exceeds the configured size limit");
                }
                if (helloReceived) {
                    throw new IllegalArgumentException("Duplicate hello frame");
                }
                requireText(frame, "camelVersion");
                requireText(frame, "name");
                requireText(frame, "transport");
                if (!(frame.get("runtime") instanceof JsonObject)) {
                    throw new IllegalArgumentException("The hello frame requires a runtime object");
                }
                current.updateHello(frame);
                helloReceived = true;
            }
            case "snapshot" -> {
                requireHello();
                String kind = frame.getString("kind");
                if (!SNAPSHOT_KINDS.contains(kind)) {
                    throw new IllegalArgumentException("Unsupported snapshot kind: " + kind);
                }
                Object value = frame.get("data");
                if (!(value instanceof JsonObject data)) {
                    throw new IllegalArgumentException("Snapshot data must be a JSON object");
                }
                current.updateSnapshot(kind, data);
            }
            case "result" -> {
                requireHello();
                String requestId = frame.getString("requestId");
                if (requestId == null || requestId.isBlank()) {
                    throw new IllegalArgumentException("A result frame requires requestId");
                }
                CompletableFuture<JsonObject> future = pending.remove(requestId);
                if (future != null) {
                    future.complete(new JsonObject(frame));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported integration frame type: " + type);
        }
        current.touch();
    }

    private static void requireText(JsonObject frame, String field) {
        String value = frame.getString(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The hello frame requires " + field);
        }
    }

    private void requireHello() {
        if (!helloReceived) {
            throw new IllegalArgumentException("The hello frame must be the first protocol message");
        }
    }

    /** Sends one action and waits for its correlated result. A timeout is never retried automatically. */
    JsonObject executeAction(JsonObject action, long timeoutMillis) throws IOException {
        if (!isOpen()) {
            throw new IOException("Remote Camel integration is disconnected");
        }
        if (!helloReceived) {
            throw new IOException("Remote Camel integration has not completed its hello handshake");
        }
        if (!actionSlots.tryAcquire()) {
            throw new IOException("Remote Camel integration is busy: too many pending actions");
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        pending.put(requestId, response);
        long timeout = timeoutMillis > 0 ? timeoutMillis : 30_000L;
        boolean requestSent = false;
        try {
            JsonObject request = CliConnectorProtocol.actionFrame(requestId, action);
            String text = request.toJson();
            if (text.length() > config.maxFrameChars()) {
                throw new IOException("Remote action exceeds the configured WebSocket message size limit");
            }
            socket.sendText(text).await().atMost(Duration.ofSeconds(SEND_TIMEOUT_SECONDS));
            requestSent = true;
            return response.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancelPending(requestId, response, requestSent);
            throw new SocketTimeoutException("Timed out waiting for remote action result (outcome may be unknown)");
        } catch (InterruptedException e) {
            cancelPending(requestId, response, requestSent);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for remote action result", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Remote action connection was lost: " + cause.getMessage(), cause);
        } catch (RuntimeException e) {
            cancelPending(requestId, response, requestSent);
            throw new IOException("Could not send action to remote Camel integration", e);
        } finally {
            pending.remove(requestId);
            actionSlots.release();
        }
    }

    /** Removes the pending result and asks the integration to interrupt the matching action. */
    private void cancelPending(String requestId, CompletableFuture<JsonObject> response, boolean requestSent) {
        if (pending.remove(requestId, response)) {
            response.cancel(true);
            // Do not let a cancel overtake an action frame whose send operation itself timed out.
            if (requestSent) {
                sendCancel(requestId);
            }
        }
    }

    private void sendCancel(String requestId) {
        if (!isOpen()) {
            return;
        }
        JsonObject frame = CliConnectorProtocol.envelope("cancel");
        frame.put("requestId", requestId);
        try {
            socket.sendText(frame.toJson()).subscribe().with(
                    ignored -> {
                    },
                    failure -> LOG.debug("Could not send remote action cancellation", failure));
        } catch (RuntimeException failure) {
            LOG.debug("Could not send remote action cancellation", failure);
        }
    }

    void markClosed(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = new IOException(reason != null ? reason : "Remote WebSocket connection closed");
        pending.forEach((id, future) -> future.completeExceptionally(failure));
        pending.clear();
    }

    void closeConnection(String reason) {
        markClosed(reason);
        if (socket.isOpen()) {
            socket.close().subscribe().with(ignored -> {
            }, failure -> LOG.debug("Error closing remote connection", failure));
        }
    }

    void closeReplaced() {
        closeConnection("Remote integration reconnected on a newer WebSocket session");
    }
}
