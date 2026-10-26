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
package org.apache.camel.component.undertow.spi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.websockets.core.CloseMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.UndertowBasicAuthHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WebSocket path whose producer applies the allowed roles while its consumer, started later, adds its own handlers on
 * the same endpoint URI: a connection accepted while only the producer served the path runs the allowed-roles check but
 * not the consumer handlers, so once that consumer is up the connection is neither served by the route nor sent
 * messages, while a connection that goes through the consumer handlers is served.
 */
class SecurityProviderWebSocketConsumerHandlersWindowTest extends AbstractSecurityProviderTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("wsHandlers", new UndertowBasicAuthHandler());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // producer and consumer share the same endpoint URI: the producer keeps the shared handler
                // registered and applies allowedRoles, the consumer adds the handlers on top
                from("direct:feed")
                        .to("undertow:ws://localhost:{{port}}/guarded?allowedRoles=user&handlers=#wsHandlers&sendToAll=true");
                from("undertow:ws://localhost:{{port}}/guarded?allowedRoles=user&handlers=#wsHandlers&sendToAll=true")
                        .routeId("guarded").autoStartup(false)
                        .process(exchange -> routeInvocations.incrementAndGet())
                        .to("mock:guarded");
            }
        };
    }

    @Test
    void connectionAcceptedBeforeAConsumerWithHandlersStartedIsNotServed() throws Exception {
        securityConfiguration.setRoleToAssign("user");

        // only the producer serves the path, so the handshake runs the allowed-roles check but not the consumer handlers
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = connect("/guarded", null, listener);

        context.getRouteController().startRoute("guarded");

        // the connection does not get what the producer sends
        template.sendBody("direct:feed", "broadcast");
        // a message from the stale connection never reaches the route; the send may already fail because the
        // connection was closed when the consumer started, so it is best effort here
        webSocket.sendText("hello", true).whenComplete((result, error) -> {
        });

        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, listener.closeCode.orTimeout(10, TimeUnit.SECONDS).join());
        assertTrue(listener.received.isEmpty());
        assertEquals(0, routeInvocations.get());
    }

    @Test
    void receiveOnlyConnectionBeforeConsumerStartsIsClosedWhenConsumerStarts() throws Exception {
        securityConfiguration.setRoleToAssign("user");

        // only the producer serves the path: the connection passes the allowed-roles check but not the consumer handlers
        RecordingListener listener = new RecordingListener();
        connect("/guarded", null, listener);

        // a first broadcast confirms the peer is registered server-side while only the producer serves the path
        template.sendBody("direct:feed", "before");
        assertEquals("before", listener.firstText.orTimeout(10, TimeUnit.SECONDS).join());

        context.getRouteController().startRoute("guarded");

        // the client sends nothing: a receive-only connection must still be closed when the consumer starts, so that
        // reconnect-on-close logic runs instead of the client silently receiving no further broadcasts
        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, listener.closeCode.orTimeout(10, TimeUnit.SECONDS).join());

        // a broadcast after the close does not reach the stale connection
        template.sendBody("direct:feed", "after");
        assertEquals(List.of("before"), listener.received);
        assertEquals(0, routeInvocations.get());
    }

    @Test
    void connectionThatRunsTheConsumerHandlersIsServed() throws Exception {
        securityConfiguration.setRoleToAssign("user");
        context.getRouteController().startRoute("guarded");

        getMockEndpoint("mock:guarded").expectedBodiesReceived("hello");
        String credentials = Base64.getEncoder().encodeToString("guest:secret".getBytes(StandardCharsets.UTF_8));
        WebSocket webSocket = connect("/guarded", "Basic " + credentials, new RecordingListener());
        webSocket.sendText("hello", true).join();

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, routeInvocations.get());
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    private WebSocket connect(String path, String authorization, WebSocket.Listener listener) {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + getPort() + path), listener)
                .orTimeout(5, TimeUnit.SECONDS).join();
    }

    private static final class RecordingListener implements WebSocket.Listener {

        private final List<String> received = new CopyOnWriteArrayList<>();
        private final CompletableFuture<String> firstText = new CompletableFuture<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            received.add(data.toString());
            firstText.complete(data.toString());
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }
    }
}
