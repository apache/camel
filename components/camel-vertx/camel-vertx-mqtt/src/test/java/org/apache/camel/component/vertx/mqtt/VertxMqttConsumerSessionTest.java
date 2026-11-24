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
package org.apache.camel.component.vertx.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer across a restart on a connection its producer keeps, a session the broker kept, and a redelivery after a
 * reconnect.
 */
class VertxMqttConsumerSessionTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private Vertx vertx;
    private CamelContext context;
    private TestBroker broker;
    private MqttServer server;
    private final Queue<String> received = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws Exception {
        vertx = Vertx.vertx();
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
    }

    @AfterEach
    void stop() throws Exception {
        context.stop();
        if (broker != null) {
            broker.close();
        }
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * A broker on the Vert.x MQTT server that kept the session of the client: each endpoint is handed to the handler,
     * which accepts it.
     */
    private int startServer(Handler<MqttEndpoint> handler) throws Exception {
        server = MqttServer.create(vertx, new MqttServerOptions().setHost("127.0.0.1").setPort(0));
        server.endpointHandler(endpoint -> {
            endpoint.publishAutoAck(false);
            endpoint.subscribeHandler(subscribe -> endpoint.subscribeAcknowledge(subscribe.messageId(),
                    subscribe.topicSubscriptions().stream().map(MqttTopicSubscription::qualityOfService).toList()));
            handler.handle(endpoint);
        });
        server.listen().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        return server.actualPort();
    }

    @Test
    void consumerStartedAgainOnTheConnectionOfItsProducerReadsAgain() throws Exception {
        broker = TestBroker.start(vertx);
        final String uri = "vertx-mqtt:a?brokerUrl=" + broker.url() + "&qos=1&ackTimeout=3";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("consumer").process(e -> received.add(e.getMessage().getBody(String.class)));
                from("direct:send").routeId("producer").to(uri);
            }
        });
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));

        // the producer keeps the connection while the consumer stops and starts again
        context.getRouteController().stopRoute("consumer");
        context.getRouteController().startRoute("consumer");
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));
        assertEquals(1, broker.connects(), "The consumer and the producer share one connection");

        // the PUBACK of a publish of quality of service 1 must be read
        final Exchange sent = context.createProducerTemplate().send("direct:send",
                e -> e.getMessage().setBody("out"));
        assertNull(sent.getException(), "The broker acknowledged the message, the producer must read the PUBACK");
        broker.send("a", "after".getBytes(StandardCharsets.UTF_8), 1, false, null);
        await().atMost(WAIT).until(() -> received.contains("after"));
    }

    @Test
    void consumerStoppedAsTheLastUserOfItsConnectionLeavesTheMessagesItDidNotReadUnacknowledged() throws Exception {
        final Queue<Integer> acknowledged = new ConcurrentLinkedQueue<>();
        final List<MqttEndpoint> endpoints = new CopyOnWriteArrayList<>();
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final int port = startServer(endpoint -> {
            endpoints.add(endpoint);
            endpoint.publishAcknowledgeHandler(acknowledged::add);
            endpoint.subscribeHandler(subscribe -> {
                endpoint.subscribeAcknowledge(subscribe.messageId(),
                        subscribe.topicSubscriptions().stream().map(MqttTopicSubscription::qualityOfService).toList());
                subscribed.countDown();
            });
            endpoint.closeHandler(v -> closed.countDown());
            endpoint.accept(false);
        });
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final String uri = "vertx-mqtt:a?brokerUrl=tcp://127.0.0.1:" + port + "&clientId=alone&cleanStart=false&qos=1"
                           + "&automaticReconnect=false&maxPendingMessages=1";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("consumer").process(e -> {
                    final String body = e.getMessage().getBody(String.class);
                    received.add(body);
                    if ("first".equals(body)) {
                        firstStarted.countDown();
                        assertTrue(releaseFirst.await(20, TimeUnit.SECONDS));
                    }
                });
            }
        });
        final VertxMqttConsumer consumer = (VertxMqttConsumer) context.getRoute("consumer").getConsumer();
        assertTrue(subscribed.await(10, TimeUnit.SECONDS));

        // the first message is processed, and at maxPendingMessages the client stops reading: the other two wait
        // unread (and so unacknowledged) on the connection
        final MqttEndpoint endpoint = endpoints.get(0);
        vertx.runOnContext(v -> endpoint.publish("a", Buffer.buffer("first"), MqttQoS.AT_LEAST_ONCE, false, false));
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS));
        vertx.runOnContext(v -> {
            endpoint.publish("a", Buffer.buffer("second"), MqttQoS.AT_LEAST_ONCE, false, false);
            endpoint.publish("a", Buffer.buffer("third"), MqttQoS.AT_LEAST_ONCE, false, false);
        });

        // the consumer stops: the connection has no other user and stops with it, so the session the broker keeps
        // must still hold the two messages for the next connect
        consumer.prepareShutdown(false, false);
        releaseFirst.countDown();
        await().atMost(WAIT).until(() -> consumer.getPendingExchangesSize() == 0);
        final VertxMqttEndpoint endpointOfRoute = consumer.getEndpoint();
        // the consumer itself, so that its doStop runs on this thread (a route stop hands it to a shutdown thread)
        final Thread stopper = new Thread(consumer::stop, "stop-consumer");
        // the stop is held where it gives the connection back (as a busy thread may be): the client must not read the
        // waiting messages meanwhile, as it would acknowledge them
        synchronized (endpointOfRoute) {
            stopper.start();
            await().atMost(WAIT).until(() -> stopper.getState() == Thread.State.BLOCKED);
            await().during(Duration.ofSeconds(1)).atMost(WAIT).until(() -> acknowledged.size() == 1);
        }
        stopper.join(WAIT.toMillis());
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("first"), List.copyOf(received));
        assertEquals(1, acknowledged.size(), "Only the message the consumer processed may be acknowledged");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void messagesOfAKeptSessionRightAfterTheConnackReachTheConsumer(boolean manualAcks) throws Exception {
        final Queue<Integer> acknowledged = new ConcurrentLinkedQueue<>();
        // the queued message comes in the same flush as the CONNACK
        final int port = startServer(endpoint -> {
            endpoint.publishAcknowledgeHandler(acknowledged::add);
            endpoint.accept(true);
            endpoint.publish("a", Buffer.buffer("queued"), MqttQoS.AT_LEAST_ONCE, false, false);
        });
        // without automaticReconnect the start waits for the CONNACK
        final String uri = "vertx-mqtt:a?brokerUrl=tcp://127.0.0.1:" + port + "&clientId=kept&cleanStart=false&qos=1"
                           + "&automaticReconnect=false&manualAcksEnabled=" + manualAcks;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("consumer").process(e -> received.add(e.getMessage().getBody(String.class)));
            }
        });

        await().atMost(WAIT).untilAsserted(() -> {
            assertEquals(List.of("queued"), List.copyOf(received));
            assertEquals(1, acknowledged.size(), "The message must be acknowledged once");
        });
    }

    @Test
    void messageFailingOnTheCurrentConnectionAfterAFailureOnAnEarlierOneIsDeliveredAgain() throws Exception {
        // a session for one client: the message stays until it is acknowledged, and comes again on every connect
        final AtomicBoolean acked = new AtomicBoolean();
        final AtomicInteger connects = new AtomicInteger();
        final List<MqttEndpoint> endpoints = new CopyOnWriteArrayList<>();
        final int port = startServer(endpoint -> {
            final int connect = connects.incrementAndGet();
            endpoints.add(endpoint);
            endpoint.publishAcknowledgeHandler(id -> acked.set(true));
            endpoint.accept(connect > 1);
            if (!acked.get()) {
                endpoint.publish("a", Buffer.buffer("poison"), MqttQoS.AT_LEAST_ONCE, connect > 1, false);
            }
        });
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final AtomicInteger attempts = new AtomicInteger();
        final String uri = "vertx-mqtt:a?brokerUrl=tcp://127.0.0.1:" + port
                           + "&clientId=redeliver&cleanStart=false&sessionExpiryInterval=60&qos=1&manualAcksEnabled=true"
                           + "&reconnectDelay=50&maxReconnectDelay=200";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("consumer").process(e -> {
                    final int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        firstStarted.countDown();
                        assertTrue(releaseFirst.await(20, TimeUnit.SECONDS));
                    }
                    // the copies of the first two connections fail, the third succeeds
                    if (attempt <= 2) {
                        throw new IllegalStateException("Forced failure " + attempt);
                    }
                });
            }
        });
        final VertxMqttConsumer consumer = (VertxMqttConsumer) context.getRoute("consumer").getConsumer();

        // the first copy is processed while connection 1 drops; the client reconnects and reads the second copy
        assertTrue(firstStarted.await(20, TimeUnit.SECONDS));
        vertx.runOnContext(v -> endpoints.get(0).close());
        await().atMost(WAIT).until(() -> connects.get() == 2 && consumer.getPendingExchangesSize() == 2);

        // the first copy fails (connection 1), then the second copy fails on connection 2: the consumer must close
        // connection 2, and the broker sends the message again on connection 3
        releaseFirst.countDown();
        await().atMost(WAIT).untilAsserted(() -> {
            assertTrue(acked.get(), "The message must be delivered again and acknowledged");
            assertEquals(3, attempts.get());
            assertEquals(3, connects.get());
        });
    }
}
