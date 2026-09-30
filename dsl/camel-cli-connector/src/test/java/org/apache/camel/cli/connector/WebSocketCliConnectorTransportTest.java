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

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class WebSocketCliConnectorTransportTest extends CamelTestSupport {

    private final ToolServer tool = new ToolServer();
    private final AtomicInteger sigterms = new AtomicInteger();
    private LocalCliConnector connector;

    @Override
    protected boolean useJmx() {
        // status and route dumps are built from the JMX management layer
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setBacklogTracing(true);
        // do not let the context start the connector found on the classpath, the tests start their own
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:hello").routeId("hello").setBody(simple("Hello ${body}"));
                from("direct:boom").routeId("boom").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    @BeforeEach
    void startTool() throws Exception {
        tool.start(0);
        property("camel.cli.transport", "websocket");
        property("camel.cli.websocket.url", "ws://127.0.0.1:" + tool.port + "/v1/worker/connect?executionId=it-1");
        property("camel.cli.websocket.snapshotInterval", "200");
        property("camel.cli.websocket.reconnectDelay", "100");
        property("camel.cli.websocket.reconnectMaxDelay", "500");
    }

    @AfterEach
    void stopAll() throws Exception {
        if (connector != null) {
            connector.stop();
        }
        tool.close();
        context.getCamelContextExtension().setProfile(null);
    }

    @Test
    void sendsHelloAndSnapshotsOnceConnected() throws Exception {
        // the tool does not check it here (see keepsRetryingWhenTheTokenIsRejected), it must not leak in the status
        property("camel.cli.websocket.token", "t0k3n-4-t3st");
        startConnector();

        JsonObject hello = tool.awaitFrame(f -> "hello".equals(f.getString("type")));
        assertThat(hello.getInteger("v")).isEqualTo(1);
        assertThat(hello.getString("name")).isEqualTo(context.getName());
        assertThat(hello.getString("camelVersion")).isEqualTo(context.getVersion());
        JsonObject runtime = hello.getMap("runtime");
        assertThat(runtime.getLong("pid")).isEqualTo(ProcessHandle.current().pid());

        JsonObject status = tool.awaitFrame(f -> isSnapshot(f, "status")).getMap("data");
        List<JsonObject> routes = status.getCollection("routes");
        assertThat(routes).extracting(r -> r.getString("routeId")).contains("hello", "boom");
        // the properties are part of the status, with the token masked
        assertThat(status.toJson()).contains("camel.cli.websocket.token").doesNotContain("t0k3n-4-t3st");
    }

    @Test
    void executesActions() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        tool.send(action("r1", "route", "command", "stop", "id", "boom"));
        JsonObject result = tool.awaitResult("r1");
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(context.getRouteController().getRouteStatus("boom")).isEqualTo(ServiceStatus.Stopped);

        tool.send(action("r2", "send", "endpoint", "direct:hello", "body", "World", "exchangePattern", "InOut"));
        result = tool.awaitResult("r2");
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(map(result, "result").toJson()).contains("Hello World");
    }

    @Test
    void reportsFailedActions() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        tool.send(action("r1", "route", "command", "start", "id", "does-not-exist"));
        JsonObject result = tool.awaitResult("r1");
        assertThat(result.getBoolean("ok")).isFalse();
        assertThat(result.getString("error")).contains("does-not-exist");

        tool.send(action("r2", "does-not-exist"));
        result = tool.awaitResult("r2");
        assertThat(result.getBoolean("ok")).isFalse();
        assertThat(result.getString("error")).isEqualTo("Unknown action: does-not-exist");

        // the send console reports the exception in its result, which is a failed action for the tool
        tool.send(action("r3", "send", "endpoint", "direct:boom", "body", "x", "exchangePattern", "InOut"));
        result = tool.awaitResult("r3");
        assertThat(result.getBoolean("ok")).isFalse();
        assertThat(result.getString("error")).isEqualTo("Forced");
        assertThat(map(result, "result").getString("status")).isEqualTo("error");
    }

    @Test
    void rejectsInvalidFrames() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        tool.sendText("not json");
        assertThat(tool.awaitFrame(f -> "result".equals(f.getString("type"))).getString("error")).startsWith("Invalid frame");

        JsonObject frame = action("r1", "route", "command", "stop", "id", "hello");
        frame.put("v", 2);
        tool.send(frame);
        assertThat(tool.awaitResult("r1").getString("error")).isEqualTo("Unsupported protocol version: 2");
        assertThat(context.getRouteController().getRouteStatus("hello")).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void sendsOnlyNewTraces() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        template.sendBody("direct:hello", "one");
        List<JsonObject> first = map(tool.awaitFrame(f -> isSnapshot(f, "trace")), "data").getCollection("traces");
        long firstMax = first.stream().mapToLong(t -> t.getLong("uid")).max().orElseThrow();

        template.sendBody("direct:hello", "two");
        List<JsonObject> second = map(tool.awaitFrame(f -> isSnapshot(f, "trace")), "data").getCollection("traces");
        assertThat(second).isNotEmpty().allSatisfy(t -> assertThat(t.getLong("uid")).isGreaterThan(firstMax));
    }

    @Test
    void reconnectsWhenTheToolRestarts() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));
        int port = tool.port;

        tool.close();
        tool.start(port);

        tool.awaitFrame(f -> "hello".equals(f.getString("type")));
        assertThat(tool.handshakes).hasValueGreaterThanOrEqualTo(1);
    }

    @Test
    void keepsRetryingWhenTheTokenIsRejected() throws Exception {
        tool.requiredToken = "right";
        property("camel.cli.websocket.token", "wrong");
        startConnector();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(tool.handshakes).hasValueGreaterThan(2));
        assertThat(tool.frames).isEmpty();

        // the tool accepts the token after all (e.g. it was restarted with it)
        tool.requiredToken = "wrong";
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));
    }

    @Test
    void reconnectsWhenTheToolStopsAnswering() throws Exception {
        property("camel.cli.websocket.heartbeatInterval", "200");
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        // stop reading: pings are no longer answered, but the tcp connection stays open
        tool.sockets.get(0).pause();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(tool.handshakes).hasValue(2));
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));
    }

    @Test
    void stopActionShutsDownTheApplication() throws Exception {
        startConnector();
        tool.awaitFrame(f -> "hello".equals(f.getString("type")));

        tool.send(action("r1", "stop"));
        JsonObject result = tool.awaitResult("r1");
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(map(result, "result").getString("status")).isEqualTo("stopping");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(sigterms).hasValue(1));

        // and it does not reconnect when the tool goes away
        tool.sockets.forEach(ServerWebSocket::close);
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(tool.handshakes).hasValue(1));
    }

    @Test
    void refusesToStartWithTheProdProfile() {
        context.getCamelContextExtension().setProfile("prod");

        assertThatThrownBy(this::startConnector).hasMessage(
                "The Camel CLI connector websocket transport gives the connected tool full control of this application"
                                                            + " and cannot be used with the prod profile."
                                                            + " Remove camel.cli.transport=websocket.");
        assertThat(tool.handshakes).hasValue(0);
    }

    @Test
    void requiresATokenForARemoteTool() {
        // TEST-NET-1 address: not loopback, and never contacted
        property("camel.cli.websocket.url", "ws://192.0.2.1:8080/connect");

        assertThatThrownBy(this::startConnector).hasMessage(
                "camel.cli.websocket.token must be set when camel.cli.websocket.url is not a loopback address");
    }

    private void startConnector() {
        connector = new LocalCliConnector(new DefaultCliConnectorFactory()) {
            @Override
            public void sigterm() {
                // do not stop the test context, only record the call
                sigterms.incrementAndGet();
            }
        };
        connector.setCamelContext(context);
        connector.start();
    }

    private void property(String key, String value) {
        context.getPropertiesComponent().addOverrideProperty(key, value);
    }

    private static JsonObject map(JsonObject json, String key) {
        return json.getMap(key);
    }

    private static boolean isSnapshot(JsonObject frame, String kind) {
        return "snapshot".equals(frame.getString("type")) && kind.equals(frame.getString("kind"));
    }

    private static JsonObject action(String requestId, String name, String... keyValues) {
        JsonObject action = new JsonObject();
        action.put("action", name);
        for (int i = 0; i < keyValues.length; i += 2) {
            action.put(keyValues[i], keyValues[i + 1]);
        }
        return new JsonObject(Map.of("v", 1, "type", "action", "requestId", requestId, "action", action));
    }

    /**
     * Plays the tool: a WebSocket server the connector dials out to.
     */
    private static class ToolServer implements AutoCloseable {

        final BlockingQueue<JsonObject> frames = new LinkedBlockingQueue<>();
        final List<ServerWebSocket> sockets = new CopyOnWriteArrayList<>();
        final AtomicInteger handshakes = new AtomicInteger();
        volatile String requiredToken;
        int port;
        private Vertx vertx;
        private HttpServer server;

        void start(int port) throws Exception {
            vertx = Vertx.vertx();
            server = vertx.createHttpServer()
                    .webSocketHandshakeHandler(handshake -> {
                        handshakes.incrementAndGet();
                        String auth = handshake.headers().get("Authorization");
                        if (requiredToken != null && !("Bearer " + requiredToken).equals(auth)) {
                            handshake.reject(401);
                        } else {
                            handshake.accept();
                        }
                    })
                    .webSocketHandler(ws -> {
                        sockets.add(ws);
                        ws.textMessageHandler(text -> {
                            try {
                                frames.add((JsonObject) Jsoner.deserialize(text));
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        });
                        ws.closeHandler(v -> sockets.remove(ws));
                    })
                    .listen(port, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            this.port = server.actualPort();
        }

        void send(JsonObject frame) {
            sendText(frame.toJson());
        }

        void sendText(String text) {
            await().atMost(10, TimeUnit.SECONDS).until(() -> !sockets.isEmpty());
            sockets.get(sockets.size() - 1).writeTextMessage(text);
        }

        /**
         * Waits for a frame matching the predicate, skipping the others (mostly snapshots).
         */
        JsonObject awaitFrame(Predicate<JsonObject> predicate) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < deadline) {
                JsonObject frame = frames.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && predicate.test(frame)) {
                    return frame;
                }
            }
            throw new AssertionError("No matching frame received within 10 seconds");
        }

        JsonObject awaitResult(String requestId) throws InterruptedException {
            return awaitFrame(f -> "result".equals(f.getString("type")) && requestId.equals(f.getString("requestId")));
        }

        @Override
        public void close() throws Exception {
            if (vertx != null) {
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                vertx = null;
            }
            sockets.clear();
            frames.clear();
        }
    }
}
