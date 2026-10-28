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
package org.apache.camel.component.vertx.websocket;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import org.apache.camel.Consumer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reconnects of the client consumer against a server whose answer takes longer than the reconnect interval, or that
 * rejects the handshake. The server is a plain Vert.x HTTP server that counts the handshakes and the open WebSockets.
 */
@Timeout(90)
class VertxWebsocketConsumerAsClientSlowReconnectTest extends VertxWebSocketTestSupport {

    private static final int RECONNECT_INTERVAL = 50;
    // several reconnect intervals
    private static final int HANDSHAKE_DELAY = 1000;

    private enum Mode {
        ACCEPT,
        ACCEPT_LATE,
        HOLD,
        REJECT
    }

    private final Set<ServerWebSocket> open = ConcurrentHashMap.newKeySet();
    private final AtomicInteger handshakes = new AtomicInteger();
    private final AtomicInteger pending = new AtomicInteger();
    // WebSockets the server has opened, including the ones that are closed again
    private final AtomicInteger opened = new AtomicInteger();
    // handshakes of the HOLD mode, answered when the test says so
    private final Queue<ServerWebSocketHandshake> held = new ConcurrentLinkedQueue<>();
    private volatile Mode mode = Mode.ACCEPT;

    private Vertx serverVertx;

    @Override
    protected void setupResources() throws Exception {
        serverVertx = Vertx.vertx();
        HttpServer server = serverVertx.createHttpServer();
        server.webSocketHandshakeHandler(handshake -> {
            handshakes.incrementAndGet();
            switch (mode) {
                case ACCEPT -> handshake.accept();
                case REJECT -> handshake.reject();
                case HOLD -> held.add(handshake);
                case ACCEPT_LATE -> {
                    pending.incrementAndGet();
                    serverVertx.setTimer(HANDSHAKE_DELAY,
                            timerId -> handshake.accept().onComplete(done -> pending.decrementAndGet()));
                }
            }
        });
        server.webSocketHandler(webSocket -> {
            open.add(webSocket);
            opened.incrementAndGet();
            webSocket.closeHandler(closed -> open.remove(webSocket));
        });
        server.listen(port.getPort(), "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (serverVertx != null) {
            serverVertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void reconnectSlowerThanTheIntervalOpensOneWebSocket() throws Exception {
        awaitOpenWebSockets(1);

        // the connection is lost, and the server answers a handshake only after several reconnect intervals
        mode = Mode.ACCEPT_LATE;
        int before = handshakes.get();
        closeOpenWebSockets();

        // all the handshakes the consumer sent are answered
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> handshakes.get() > before && pending.get() == 0 && !open.isEmpty());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");
        mock.setAssertPeriod(1000);
        List.copyOf(open).forEach(webSocket -> webSocket.writeTextMessage("Hello"));

        assertAll(
                () -> assertEquals(1, handshakes.get() - before, "handshakes sent for one reconnect"),
                () -> assertEquals(1, open.size(), "WebSockets the consumer has open after one reconnect"),
                () -> mock.assertIsSatisfied());
    }

    @Test
    void reconnectThatCompletesAfterStopIsClosed() throws Exception {
        awaitOpenWebSockets(1);

        mode = Mode.ACCEPT_LATE;
        closeOpenWebSockets();
        // a reconnect is in flight
        await().atMost(30, TimeUnit.SECONDS).until(() -> pending.get() > 0);

        context.getRouteController().stopRoute("client");

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(0, pending.get() + open.size(),
                        "WebSockets pending or open after the consumer was stopped"));
    }

    @Test
    void stoppedConsumerDoesNotReconnect() throws Exception {
        awaitOpenWebSockets(1);

        // the consumer retries every 50 ms while the server rejects the handshake
        mode = Mode.REJECT;
        int before = handshakes.get();
        closeOpenWebSockets();
        await().atMost(30, TimeUnit.SECONDS).until(() -> handshakes.get() >= before + 3);

        context.getRouteController().stopRoute("client");

        // no handshake for 20 reconnect intervals
        AtomicInteger seen = new AtomicInteger(-1);
        await("no reconnect attempt after the consumer was stopped")
                .during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10))
                .until(() -> seen.getAndSet(handshakes.get()) == handshakes.get());
    }

    @Test
    void restartWhileReconnectIsPendingOpensOneWebSocket() throws Exception {
        awaitOpenWebSockets(1);

        // the connection is lost, and the server does not answer the handshake of the reconnect yet
        mode = Mode.HOLD;
        int handshakesBefore = handshakes.get();
        int openedBefore = opened.get();
        closeOpenWebSockets();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !held.isEmpty());

        // the route is stopped and started again (with a new consumer) while that attempt waits for the answer
        Consumer stopped = context.getRoute("client").getConsumer();
        context.getRouteController().stopRoute("client");
        CompletableFuture<Void> restart = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().startRoute("client");
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
        try {
            await().atMost(30, TimeUnit.SECONDS)
                    .until(() -> isStarting(context.getRoute("client").getConsumer(), stopped) || restart.isDone());

            // now the server answers: the pending handshake(s), and at once from here on
            mode = Mode.ACCEPT;
            for (ServerWebSocketHandshake handshake = held.poll(); handshake != null; handshake = held.poll()) {
                handshake.accept();
            }
            restart.get(30, TimeUnit.SECONDS);
        } finally {
            // never leave a start waiting for a handshake that is not answered
            mode = Mode.ACCEPT;
            for (ServerWebSocketHandshake handshake = held.poll(); handshake != null; handshake = held.poll()) {
                handshake.accept();
            }
        }

        // The pending attempt belongs to the stopped consumer: it closes its WebSocket, and the start of the new
        // consumer, which waited for that attempt, connects. Two handshakes, one WebSocket left.
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertAll(
                        () -> assertEquals(2, handshakes.get() - handshakesBefore,
                                "handshakes sent by the reconnect and the restart"),
                        () -> assertEquals(2, opened.get() - openedBefore,
                                "WebSockets opened by the reconnect and the restart"),
                        () -> assertEquals(1, open.size(), "WebSockets the consumer has open after the restart")));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");
        mock.setAssertPeriod(1000);
        List.copyOf(open).forEach(webSocket -> webSocket.writeTextMessage("Hello"));
        mock.assertIsSatisfied();

        assertAll(
                () -> assertEquals(2, handshakes.get() - handshakesBefore, "no further handshake after the restart"),
                () -> assertEquals(1, open.size(), "WebSockets the consumer has open after the message"));
    }

    private static boolean isStarting(Consumer consumer, Consumer stopped) {
        return consumer != stopped && consumer instanceof StatefulService service && service.isStarting();
    }

    private void awaitOpenWebSockets(int expected) {
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(expected, open.size()));
    }

    private void closeOpenWebSockets() {
        List.copyOf(open).forEach(ServerWebSocket::close);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/feed?consumeAsClient=true&reconnectInterval=%d", port.getPort(),
                        RECONNECT_INTERVAL)
                        .routeId("client")
                        .to("mock:result");
            }
        };
    }
}
