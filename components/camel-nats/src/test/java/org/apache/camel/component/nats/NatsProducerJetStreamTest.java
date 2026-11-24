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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamOptions;
import io.nats.client.Message;
import io.nats.client.api.ApiResponse;
import io.nats.client.api.PublishAck;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsMessage;
import io.nats.client.support.JsonParser;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JetStream producer: the requestTimeout reaches jnats, a missing acknowledgement is reported clearly, and the
 * stream of jetstreamName is created when it does not exist (CAMEL-27198).
 */
class NatsProducerJetStreamTest extends CamelTestSupport {

    private final FakeServer server = new FakeServer();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("conn", server.connection());
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the tests send to the endpoint directly
            }
        };
    }

    private Exchange send(String options) {
        return template.send("nats:orders?connection=#conn&jetstreamEnabled=true" + options,
                e -> e.getIn().setBody("order-1"));
    }

    @Test
    void requestTimeoutReachesJnats() {
        assertNull(send("&requestTimeout=1234").getException());
        assertNull(send("&requestTimeout=1234").getException());
        assertNull(send("&requestTimeout=1234&jetstreamAsync=false").getException());

        // one JetStream context per producer, created at the start with the requestTimeout of the endpoint
        assertEquals(2, server.jetStreamOptions.size());
        for (JetStreamOptions options : server.jetStreamOptions) {
            assertEquals(Duration.ofMillis(1234), options.getRequestTimeout());
        }
        assertEquals(2, server.publishedAsync.get());
        assertEquals(1, server.publishedSync.get());
    }

    @Test
    void timeoutSaysWhatHappened() {
        server.ack = CompletableFuture::new;

        Exchange exchange = send("&requestTimeout=200");

        Exception e = exchange.getException();
        assertNotNull(e);
        assertEquals("No acknowledgement from JetStream for subject orders within 200 ms", e.getMessage());
        assertInstanceOf(TimeoutException.class, e.getCause());
    }

    @Test
    void cancelledAcknowledgementSaysWhatHappened() {
        // as jnats: the future of the PublishAck is composed on the future of the reply, which jnats cancels when it
        // gives up on it
        server.ack = () -> {
            CompletableFuture<Message> reply = new CompletableFuture<>();
            CompletableFuture<PublishAck> ack = reply.thenCompose(m -> CompletableFuture.completedFuture(null));
            reply.completeExceptionally(
                    new CancellationException("Future cancelled, response not registered in time, check connection status."));
            return ack;
        };

        Exchange exchange = send("&requestCleanupInterval=1000");

        Exception e = exchange.getException();
        assertNotNull(e);
        assertEquals("No acknowledgement from JetStream for subject orders: Future cancelled, response not registered in"
                     + " time, check connection status. (with jetstreamAsync=true the NATS client gives up on the"
                     + " acknowledgement after requestCleanupInterval, 1000 ms, to twice that)",
                e.getMessage());
        assertInstanceOf(CancellationException.class, e.getCause());
    }

    @Test
    void streamIsCreatedWhenMissing() {
        assertNull(send("&jetstreamName=ORDERS").getException());

        assertEquals(1, server.added.size());
        assertEquals("ORDERS", server.added.get(0).getName());
        assertEquals(List.of("orders"), server.added.get(0).getSubjects());
    }

    @Test
    void streamIsNotCreatedWhenItExists() {
        server.streamExists = true;

        assertNull(send("&jetstreamName=ORDERS").getException());

        assertTrue(server.added.isEmpty());
    }

    @Test
    void streamIsNotCreatedWhenAnotherStreamTakesInTheSubject() {
        server.otherStreams = List.of("ALL_ORDERS");

        assertNull(send("&jetstreamName=ORDERS").getException());

        assertTrue(server.added.isEmpty());
    }

    @Test
    void producerStartsWhenTheStreamCannotBeChecked() {
        server.lookupFailure = new IOException("Timeout or no response waiting for NATS JetStream server");

        assertNull(send("&jetstreamName=ORDERS").getException());

        assertTrue(server.added.isEmpty());
        assertEquals(1, server.publishedAsync.get());
    }

    @Test
    void noStreamIsLookedUpWithoutJetstreamName() {
        assertNull(send("").getException());

        assertEquals(0, server.lookups.get());
        assertTrue(server.added.isEmpty());
    }

    static final class FakeServer {
        final List<JetStreamOptions> jetStreamOptions = new CopyOnWriteArrayList<>();
        final List<StreamConfiguration> added = new CopyOnWriteArrayList<>();
        volatile Supplier<CompletableFuture<PublishAck>> ack = () -> CompletableFuture.completedFuture(publishAck());
        volatile boolean streamExists;
        volatile List<String> otherStreams = List.of();
        volatile IOException lookupFailure;
        final AtomicInteger lookups = new AtomicInteger();
        final AtomicInteger publishedAsync = new AtomicInteger();
        final AtomicInteger publishedSync = new AtomicInteger();

        Connection connection() {
            JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
                case "getStreamInfo" -> streamInfo();
                case "getStreamNames" -> otherStreams;
                case "addStream" -> {
                    added.add((StreamConfiguration) args[0]);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(name);
            });
            JetStream js = proxy(JetStream.class, (name, args) -> switch (name) {
                case "publishAsync" -> {
                    publishedAsync.incrementAndGet();
                    yield ack.get();
                }
                case "publish" -> {
                    publishedSync.incrementAndGet();
                    yield publishAck();
                }
                default -> throw new UnsupportedOperationException(name);
            });
            ServerInfo info = new ServerInfo("INFO {\"server_id\":\"fake\",\"jetstream\":true,\"headers\":true}");
            return proxy(Connection.class, (name, args) -> switch (name) {
                case "getServerInfo" -> info;
                case "jetStreamManagement" -> jsm;
                case "jetStream" -> {
                    jetStreamOptions.add(args.length == 1 ? (JetStreamOptions) args[0] : null);
                    yield js;
                }
                case "getStatus" -> Connection.Status.CONNECTED;
                default -> null;
            });
        }

        private StreamInfo streamInfo() throws IOException, JetStreamApiException {
            lookups.incrementAndGet();
            if (lookupFailure != null) {
                throw lookupFailure;
            }
            if (!streamExists) {
                // the server's answer as jnats reads it (ApiResponse is abstract)
                throw new JetStreamApiException(
                        new ApiResponse<Void>(
                                JsonParser.parseUnchecked(
                                        "{\"error\":{\"code\":404,\"err_code\":10059,\"description\":\"stream not found\"}}")) {
                        });
            }
            return new StreamInfo(
                    JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"]},\"state\":{}}"));
        }

        private static PublishAck publishAck() {
            try {
                return new PublishAck(
                        new NatsMessage(
                                "_INBOX.1", null, "{\"stream\":\"ORDERS\",\"seq\":1}".getBytes(StandardCharsets.UTF_8)));
            } catch (IOException | JetStreamApiException e) {
                throw new IllegalStateException(e);
            }
        }

        interface Handler {
            Object handle(String name, Object[] args) throws Exception;
        }

        @SuppressWarnings("unchecked")
        static <T> T proxy(Class<T> type, Handler handler) {
            return (T) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), new Class<?>[] { type },
                    (p, m, a) -> {
                        if (m.getDeclaringClass() == Object.class) {
                            return switch (m.getName()) {
                                case "hashCode" -> System.identityHashCode(p);
                                case "equals" -> p == a[0];
                                default -> type.getSimpleName() + "@fake";
                            };
                        }
                        Object r = handler.handle(m.getName(), a == null ? new Object[0] : a);
                        if (r == null && m.getReturnType() == boolean.class) {
                            return false;
                        }
                        return r;
                    });
        }
    }
}
