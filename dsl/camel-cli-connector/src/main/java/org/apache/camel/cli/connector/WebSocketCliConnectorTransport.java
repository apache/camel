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
package org.apache.camel.cli.connector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport that dials out to a developer tool over a WebSocket (JDK client), for tools that do not share the
 * filesystem of the integration or need events pushed instead of polled.
 * <p/>
 * Every frame is a JSON envelope <tt>{"v":1,"type":...}</tt>. The tool sends <tt>action</tt> frames holding the same
 * action JSON the Camel CLI writes to its action file, and gets <tt>result</tt> frames back correlated by
 * <tt>requestId</tt>. The transport sends a <tt>hello</tt> frame once the integration is started, and <tt>snapshot</tt>
 * frames (the content of the status, trace, ... files) while connected.
 * <p/>
 * The connection is kept alive with pings, and re-established with an exponential backoff when it is lost.
 * <p/>
 * Threads: actions run one at a time on their own thread (the dispatcher is not thread-safe, and an action can block
 * for a long time); connecting, snapshots, heartbeats and every write to the socket run on a second thread, so the JDK
 * WebSocket never has two sends in flight.
 */
public class WebSocketCliConnectorTransport extends ServiceSupport implements CliConnectorTransport {

    static final int VERSION = 1;

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketCliConnectorTransport.class);
    private static final long SEND_TIMEOUT = 10000;
    private static final long STABLE_CONNECTION = 10000;
    private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;
    private static final int MAX_PENDING_ACTIONS = 64;
    // WebSocket servers commonly refuse messages over 256 KB (Vert.x, Quarkus): trace and receive snapshots can be
    // much larger (after a reconnect they hold every retained message), so they are split into frames of this size
    private static final int MAX_SNAPSHOT_SIZE = 128 * 1024;

    private CamelContext camelContext;
    private CliActionDispatcher dispatcher;
    private CliSnapshotProducer snapshots;
    private Runnable shutdown;

    private URI url;
    private String token;
    private long reconnectDelay;
    private long reconnectMaxDelay;
    private long snapshotInterval;
    private long heartbeatInterval;

    private String threadNamePattern;
    private HttpClient client;
    private ThreadPoolExecutor actions;
    private ScheduledExecutorService scheduler;
    // fields below are only used from the scheduler thread, except the volatile ones
    private volatile Connection connection;
    private volatile boolean ready;
    private volatile boolean stopping;
    private boolean listenerAdded;
    // the snapshot task, only (re)scheduled from the scheduler thread or before it runs anything
    private ScheduledFuture<?> snapshotFuture;
    private long debugInterval;
    private ScheduledFuture<?> debugFuture;
    private int failures;
    private long ticks;

    @Override
    public void configure(
            CamelContext camelContext, CliActionDispatcher dispatcher, CliSnapshotProducer snapshots, Runnable shutdown) {
        this.camelContext = camelContext;
        this.dispatcher = dispatcher;
        this.snapshots = snapshots;
        this.shutdown = shutdown;
    }

    @Override
    protected void doStart() throws Exception {
        if ("prod".equals(camelContext.getCamelContextExtension().getProfile())) {
            throw new IllegalStateException(
                    "The Camel CLI connector websocket transport gives the connected tool full control of this"
                                            + " application and cannot be used with the prod profile."
                                            + " Remove camel.cli.transport=websocket.");
        }
        String value = property("camel.cli.websocket.url", null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("camel.cli.websocket.url must be set when camel.cli.transport=websocket");
        }
        url = URI.create(value);
        String scheme = url.getScheme() != null ? url.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
            throw new IllegalArgumentException("camel.cli.websocket.url must be a ws:// or wss:// url: " + value);
        }
        boolean loopback = isLoopback(url.getHost());
        token = property("camel.cli.websocket.token", null);
        if ((token == null || token.isBlank()) && !loopback) {
            throw new IllegalArgumentException(
                    "camel.cli.websocket.token must be set when camel.cli.websocket.url is not a loopback address");
        }
        reconnectDelay = Long.parseLong(property("camel.cli.websocket.reconnect-delay", "1000"));
        reconnectMaxDelay = Long.parseLong(property("camel.cli.websocket.reconnect-max-delay", "30000"));
        snapshotInterval = Long.parseLong(property("camel.cli.websocket.snapshot-interval", "1000"));
        // when debugging, the debug snapshot (breakpoints) is sent faster than the others, as the file transport does
        debugInterval = camelContext.isDebugging() ? 100 : 0;
        heartbeatInterval = Long.parseLong(property("camel.cli.websocket.heartbeat-interval", "10000"));

        LOG.warn("Camel CLI connector connects to {} which gets full control of this application (development use only)",
                where());
        if ("ws".equals(scheme) && !loopback) {
            if (!"true".equalsIgnoreCase(property("camel.cli.websocket.allow-insecure", "false"))) {
                throw new IllegalArgumentException(
                        "camel.cli.websocket.url must use wss:// for a remote host (or a tunnel to a loopback address),"
                                                   + " or set camel.cli.websocket.allow-insecure=true");
            }
            LOG.warn("Camel CLI connector uses an unencrypted connection to a remote host; use wss:// or a tunnel");
        }

        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        // Camel's thread factory (naming, virtual threads when enabled) but not Camel's thread pools: these threads must
        // keep running while Camel is stopping, to report it and to send the close frame
        threadNamePattern = camelContext.getExecutorServiceManager().getThreadNamePattern();
        actions = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(MAX_PENDING_ACTIONS),
                new CamelThreadFactory(threadNamePattern, "CliConnectorActions", true));
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new CamelThreadFactory(threadNamePattern, "CliConnectorWebSocket", true));

        stopping = false;
        ready = camelContext.isStarted();
        if (!listenerAdded) {
            // Camel cannot remove a startup listener, so it is added once even if this transport is restarted
            listenerAdded = true;
            camelContext.addStartupListener(new ExtendedStartupListener() {
                @Override
                public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) {
                    // wait until fully started
                }

                @Override
                public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) {
                    ready = true;
                    execute(() -> sayHello(connection));
                }
            });
        }

        // a periodic task that throws is never run again, hence safely()
        scheduleSnapshots();
        scheduler.scheduleWithFixedDelay(() -> safely(this::heartbeatTask), heartbeatInterval, heartbeatInterval,
                TimeUnit.MILLISECONDS);
        scheduler.execute(this::connect);
    }

    @Override
    public void updateDelay(int delay) {
        // camel-cli-debug makes it faster so breakpoints show up quickly: only the debug snapshot needs that
        debugInterval = delay;
        execute(this::scheduleSnapshots);
    }

    private void scheduleSnapshots() {
        if (snapshotFuture != null) {
            snapshotFuture.cancel(false);
        }
        snapshotFuture = scheduler.scheduleWithFixedDelay(() -> safely(this::snapshotTask), snapshotInterval,
                snapshotInterval, TimeUnit.MILLISECONDS);
        if (debugFuture != null) {
            debugFuture.cancel(false);
            debugFuture = null;
        }
        if (debugInterval > 0) {
            debugFuture = scheduler.scheduleWithFixedDelay(() -> safely(this::debugTask), debugInterval,
                    debugInterval, TimeUnit.MILLISECONDS);
        }
    }

    private void debugTask() {
        Connection c = connection;
        if (c != null && c.helloSent) {
            // only sent when it changed, so the regular snapshot task does not send it again
            snapshot(c, "debug", true);
        }
    }

    @Override
    protected void doStop() throws Exception {
        stopping = true;
        if (scheduler != null) {
            try {
                scheduler.submit(() -> {
                    Connection c = connection;
                    if (c != null) {
                        connection = null;
                        c.close(WebSocket.NORMAL_CLOSURE, "stopping");
                    }
                }).get(SEND_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOG.debug("Error closing websocket due to: {}. This exception is ignored.", e.getMessage(), e);
            }
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (actions != null) {
            actions.shutdownNow();
            actions = null;
        }
        client = null;
    }

    // ---- connection lifecycle (scheduler thread) ----

    private void connect() {
        if (stopping) {
            return;
        }
        try {
            WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            builder.buildAsync(url, new Connection()).whenComplete((ws, e) -> {
                if (e != null) {
                    execute(() -> connectFailed(e));
                }
            });
        } catch (Exception e) {
            connectFailed(e);
        }
    }

    private void connectFailed(Throwable e) {
        failures++;
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof WebSocketHandshakeException he) {
            int code = he.getResponse().statusCode();
            if (code == 401 || code == 403) {
                LOG.warn("Camel CLI connector was rejected by {} (HTTP {}): check camel.cli.websocket.token", where(), code);
            } else {
                LOG.warn("Camel CLI connector was rejected by {} (HTTP {})", where(), code);
            }
        } else if (failures == 1) {
            LOG.warn("Camel CLI connector cannot connect to {} due to: {}. Will keep retrying.", where(), describe(cause));
        } else {
            LOG.debug("Camel CLI connector cannot connect to {} due to: {}", where(), describe(cause));
        }
        reconnect();
    }

    private void opened(Connection c) {
        if (stopping) {
            c.ws.abort();
            return;
        }
        connection = c;
        LOG.info("Camel CLI connector connected to {}", where());
        sayHello(c);
    }

    private void closed(Connection c, String reason) {
        if (connection != c) {
            // an old connection, or already handled
            return;
        }
        connection = null;
        c.ws.abort();
        // a connection that drops right away counts as a failure, so a tool that keeps closing us is not hammered
        if (System.currentTimeMillis() - c.openedAt < STABLE_CONNECTION) {
            failures++;
        } else {
            failures = 0;
        }
        if (!stopping) {
            LOG.info("Camel CLI connector disconnected from {} ({})", where(), reason);
        }
        reconnect();
    }

    private void reconnect() {
        if (stopping) {
            return;
        }
        long delay = reconnectDelay;
        for (int i = 1; i < failures && delay < reconnectMaxDelay; i++) {
            delay *= 2;
        }
        delay = Math.min(delay, reconnectMaxDelay);
        // jitter, so many applications restarted together do not reconnect in lockstep
        delay += ThreadLocalRandom.current().nextLong(delay / 5 + 1);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void heartbeatTask() {
        Connection c = connection;
        if (c == null) {
            return;
        }
        if (System.currentTimeMillis() - c.lastSeen > 3 * heartbeatInterval) {
            // the tool went away without closing the connection (sleep, network change, crash)
            closed(c, "no heartbeat");
        } else {
            c.ws.sendPing(ByteBuffer.allocate(0));
        }
    }

    // ---- outgoing frames (scheduler thread) ----

    private void sayHello(Connection c) {
        if (c == null || c.helloSent || !ready || c != connection) {
            return;
        }
        JsonObject frame = envelope("hello");
        frame.put("camelVersion", camelContext.getVersion());
        frame.put("name", camelContext.getName());
        frame.put("transport", "jdk");
        try {
            JsonObject status = snapshots.status();
            frame.put("runtime", status.get("runtime"));
            c.helloSent = true;
            send(c, frame);
            sendSnapshot(c, "status", status);
        } catch (Exception e) {
            LOG.debug("Error sending hello due to: {}. Will retry.", e.getMessage(), e);
        }
    }

    private void snapshotTask() {
        Connection c = connection;
        if (c == null) {
            return;
        }
        if (!c.helloSent) {
            // not started yet, or the hello failed
            sayHello(c);
            return;
        }
        snapshot(c, "status", false);
        // as the file transport: these have more overhead and are only needed when tracing/debugging/receiving
        if (++ticks % 2 == 0) {
            snapshot(c, "trace", false);
            snapshot(c, "receive", false);
            snapshot(c, "debug", true);
            snapshot(c, "history", true);
            snapshot(c, "error", true);
            snapshot(c, "activity", true);
        }
    }

    private void snapshot(Connection c, String kind, boolean onlyIfChanged) {
        try {
            JsonObject data = switch (kind) {
                case "status" -> snapshots.status();
                case "trace" -> c.newMessages(snapshots.trace(), "traces");
                case "receive" -> c.newMessages(snapshots.receive(), "messages");
                case "debug" -> snapshots.debug();
                case "history" -> snapshots.messageHistory();
                case "error" -> snapshots.errors();
                default -> snapshots.activity();
            };
            if (data == null || data.isEmpty()) {
                return;
            }
            if (onlyIfChanged && !c.changed(kind, data)) {
                return;
            }
            if ("trace".equals(kind)) {
                sendInBatches(c, kind, data, "traces");
            } else if ("receive".equals(kind)) {
                sendInBatches(c, kind, data, "messages");
            } else {
                sendSnapshot(c, kind, data);
            }
        } catch (Exception e) {
            LOG.trace("Error sending {} snapshot due to: {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    /**
     * Sends the messages in as many snapshots as needed to keep each under {@link #MAX_SNAPSHOT_SIZE} (a single message
     * larger than that is sent on its own).
     */
    private void sendInBatches(Connection c, String kind, JsonObject data, String key) {
        List<JsonObject> messages = data.getCollection(key);
        List<JsonObject> batch = new ArrayList<>();
        int size = 0;
        for (JsonObject m : messages) {
            // servers limit the size in bytes: non-ASCII characters are not escaped and take up to 3 bytes
            int length = m.toJson().getBytes(StandardCharsets.UTF_8).length;
            if (length > MAX_SNAPSHOT_SIZE) {
                // the tool would refuse it, and every reconnect would send it again
                m = new JsonObject(Map.of("uid", m.get("uid"), "truncated", true, "size", length));
                length = 64;
            }
            if (!batch.isEmpty() && size + length > MAX_SNAPSHOT_SIZE) {
                sendSnapshot(c, kind, withMessages(data, key, batch));
                batch = new ArrayList<>();
                size = 0;
            }
            batch.add(m);
            size += length;
        }
        if (!batch.isEmpty()) {
            sendSnapshot(c, kind, withMessages(data, key, batch));
        }
    }

    private static JsonObject withMessages(JsonObject data, String key, List<JsonObject> messages) {
        JsonObject copy = new JsonObject(data);
        copy.put(key, new JsonArray(messages));
        return copy;
    }

    private void sendSnapshot(Connection c, String kind, JsonObject data) {
        JsonObject frame = envelope("snapshot");
        frame.put("kind", kind);
        frame.put("data", data);
        send(c, frame);
    }

    private void send(Connection c, JsonObject frame) {
        if (c != connection) {
            // the connection this frame was meant for is gone
            return;
        }
        try {
            c.ws.sendText(wellFormed(frame.toJson()), true).get(SEND_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // a send that fails or hangs leaves the socket unusable
            closed(c, "send failed: " + describe(e));
        }
    }

    // ---- incoming frames ----

    private void onFrame(Connection c, String text) {
        String requestId = null;
        try {
            JsonObject frame = (JsonObject) Jsoner.deserialize(text);
            requestId = frame.getString("requestId");
            if (!Integer.valueOf(VERSION).equals(frame.getInteger("v"))) {
                reply(c, error(requestId, "Unsupported protocol version: " + frame.get("v")));
            } else if (!"action".equals(frame.getString("type"))) {
                reply(c, error(requestId, "Unsupported frame type: " + frame.getString("type")));
            } else if (!ready) {
                reply(c, error(requestId, "Not ready: Camel is starting"));
            } else {
                JsonObject action = frame.getMap("action");
                String name = action != null ? action.getString("action") : null;
                if (name == null) {
                    reply(c, error(requestId, "Missing action"));
                } else if ("stop".equals(name)) {
                    stop(c, requestId);
                } else {
                    runAction(c, requestId, action);
                }
            }
        } catch (Exception e) {
            reply(c, error(requestId, "Invalid frame: " + e.getMessage()));
        }
    }

    private void runAction(Connection c, String requestId, JsonObject action) {
        try {
            actions.execute(() -> reply(c, result(requestId, action)));
        } catch (RejectedExecutionException e) {
            reply(c, error(requestId, "Busy: too many pending actions"));
        }
    }

    private JsonObject result(String requestId, JsonObject action) {
        JsonObject[] output = new JsonObject[1];
        String[] error = new String[1];
        try {
            boolean known = dispatcher.dispatch(action, new CliActionOutput() {
                @Override
                public void write(JsonObject result) {
                    output[0] = result;
                }

                @Override
                public void error(String message) {
                    error[0] = message;
                }
            });
            if (!known) {
                error[0] = "Unknown action: " + action.getString("action");
            }
        } catch (Exception e) {
            error[0] = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        }
        if (error[0] == null && output[0] != null) {
            // many actions report failures inside their result, as the file transport has no other way to do it
            String status = output[0].getString("status");
            if ("error".equals(status) || "failed".equals(status)) {
                JsonObject exception = output[0].getMap("exception");
                error[0] = exception != null && exception.getString("message") != null
                        ? exception.getString("message") : "Action failed";
            }
        }
        return result(requestId, error[0], output[0] != null ? output[0] : new JsonObject());
    }

    private void stop(Connection c, String requestId) {
        JsonObject frame = result(requestId, null, new JsonObject(Map.of("status", "stopping")));
        execute(() -> {
            send(c, frame);
            LOG.info("Camel CLI connector stopping the application as requested by {}", where());
            stopping = true;
            // shutting down stops this transport, which must not happen on its own thread
            new CamelThreadFactory(threadNamePattern, "CliConnectorShutdown", false).newThread(shutdown).start();
        });
    }

    private void reply(Connection c, JsonObject frame) {
        execute(() -> send(c, frame));
    }

    // ---- helpers ----

    private static void safely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            LOG.debug("Error in Camel CLI connector task due to: {}. This exception is ignored.", e.getMessage(), e);
        }
    }

    private void execute(Runnable task) {
        ScheduledExecutorService s = scheduler;
        if (s != null) {
            try {
                s.execute(task);
            } catch (RejectedExecutionException e) {
                // stopping
            }
        }
    }

    /**
     * Replaces lone surrogates (half of an emoji, as left by truncating a message body) with U+FFFD: the JDK WebSocket
     * refuses to send text that is not well-formed UTF-16, and fails the connection.
     */
    static String wellFormed(String text) {
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean pair = Character.isHighSurrogate(ch) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1));
            if (pair) {
                if (sb != null) {
                    sb.append(ch).append(text.charAt(i + 1));
                }
                i++;
            } else if (Character.isSurrogate(ch)) {
                if (sb == null) {
                    sb = new StringBuilder(text.length()).append(text, 0, i);
                }
                sb.append('\uFFFD');
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        return sb != null ? sb.toString() : text;
    }

    private static String describe(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    private static JsonObject envelope(String type) {
        JsonObject frame = new JsonObject();
        frame.put("v", VERSION);
        frame.put("type", type);
        return frame;
    }

    private static JsonObject error(String requestId, String message) {
        return result(requestId, message, null);
    }

    private static JsonObject result(String requestId, String error, JsonObject result) {
        JsonObject frame = envelope("result");
        if (requestId != null) {
            frame.put("requestId", requestId);
        }
        frame.put("ok", error == null);
        if (error != null) {
            frame.put("error", error);
        }
        if (result != null) {
            frame.put("result", result);
        }
        return frame;
    }

    /**
     * The key in kebab-case, or in camelCase, as camel-main accepts both (camel.cli.websocket.snapshot-interval or
     * camel.cli.websocket.snapshotInterval).
     */
    private String property(String key, String defaultValue) {
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        return pc.resolveProperty(key).or(() -> pc.resolveProperty(StringHelper.dashToCamelCase(key))).orElse(defaultValue);
    }

    private String where() {
        // never log the query string, it may hold credentials
        return url.getScheme() + "://" + url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "") + url.getPath();
    }

    /**
     * Only literal loopback hosts: a DNS lookup could resolve differently later, and can block the startup.
     */
    private static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || h.startsWith("127.") && h.matches("[0-9.]+") || "[::1]".equals(h)
                || "::1".equals(h);
    }

    /**
     * One WebSocket connection, and what has been sent on it.
     */
    private final class Connection implements WebSocket.Listener {

        private WebSocket ws;
        private final StringBuilder partial = new StringBuilder();
        private final long openedAt = System.currentTimeMillis();
        private volatile long lastSeen = openedAt;
        private boolean helloSent;
        private long lastTraceUid;
        private long lastReceiveUid;
        private final Map<String, String> lastSent = new HashMap<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            this.ws = webSocket;
            webSocket.request(1);
            execute(() -> opened(this));
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            lastSeen = System.currentTimeMillis();
            partial.append(data);
            if (partial.length() > MAX_FRAME_SIZE) {
                partial.setLength(0);
                execute(() -> closed(this, "frame larger than " + MAX_FRAME_SIZE + " chars"));
                return null;
            }
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                onFrame(this, text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.currentTimeMillis();
            // the JDK replies with a pong
            return WebSocket.Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            execute(() -> closed(this, "closed by the tool: " + statusCode + (reason.isEmpty() ? "" : " " + reason)));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.debug("Camel CLI connector websocket error: {}", error.getMessage(), error);
            execute(() -> closed(this, "error: " + describe(error)));
        }

        void close(int code, String reason) {
            try {
                ws.sendClose(code, reason).get(SEND_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                ws.abort();
            }
        }

        /**
         * Keeps only the messages not sent yet on this connection, or returns null if there are none.
         */
        JsonObject newMessages(JsonObject json, String key) {
            if (json == null) {
                return null;
            }
            List<JsonObject> list = json.getCollection(key);
            if (list == null) {
                return null;
            }
            boolean trace = "traces".equals(key);
            long after = trace ? lastTraceUid : lastReceiveUid;
            list.removeIf(m -> m.getLong("uid") <= after);
            if (list.isEmpty()) {
                return null;
            }
            long last = list.get(list.size() - 1).getLong("uid");
            if (trace) {
                lastTraceUid = last;
            } else {
                lastReceiveUid = last;
            }
            return json;
        }

        boolean changed(String kind, JsonObject data) {
            String text = data.toJson();
            return !text.equals(lastSent.put(kind, text));
        }
    }
}
