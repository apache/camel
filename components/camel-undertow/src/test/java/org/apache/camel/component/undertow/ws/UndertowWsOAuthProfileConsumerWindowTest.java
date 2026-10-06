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
package org.apache.camel.component.undertow.ws;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.websockets.core.CloseMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.StubOAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the OAuth validation of a WebSocket path whose secured consumer is not running while a producer route keeps
 * the shared {@code CamelWebSocketHandler} registered: while the consumer is stopped its settings keep applying to the
 * path, and a channel whose handshake completed before the consumer was first started is neither served by the route
 * nor sent messages once the secured consumer is up.
 */
public class UndertowWsOAuthProfileConsumerWindowTest extends BaseUndertowTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        return context;
    }

    @Test
    public void closesChannelEstablishedBeforeSecuredConsumerStarted() throws Exception {
        // the secured consumer has not been started yet; the producer route on the same path keeps the shared
        // WebSocket handler registered, so the handshake completes without OAuth validation
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = connect(null, listener);

        // once the secured consumer is up, the unauthenticated channel must neither get messages nor reach the route
        context.getRouteController().startRoute("secureWs");
        template.sendBody("direct:keepalive", "broadcast");

        webSocket.sendText("hello", true).orTimeout(5, TimeUnit.SECONDS).join();

        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, listener.closeCode.orTimeout(10, TimeUnit.SECONDS).join());
        assertTrue(listener.received.isEmpty());
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void validatesHandshakeWhileSecuredConsumerIsStopped() throws Exception {
        context.getRouteController().startRoute("secureWs");
        context.getRouteController().stopRoute("secureWs");

        CompletionException thrown = assertThrows(CompletionException.class, () -> connect(null, new RecordingListener()));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(401, handshake.getResponse().statusCode());

        WebSocket webSocket = connect("valid-token", new RecordingListener());
        context.getRouteController().startRoute("secureWs");

        getMockEndpoint("mock:message").expectedBodiesReceived("hello");
        webSocket.sendText("hello", true).orTimeout(5, TimeUnit.SECONDS).join();

        getMockEndpoint("mock:message").assertIsSatisfied();
    }

    private WebSocket connect(String token, RecordingListener listener) {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + getPort() + "/window"), listener)
                .orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:keepalive").routeId("keepalive")
                        .to("undertow:ws://localhost:{{port}}/window?sendToAll=true");

                from("undertow:ws://localhost:{{port}}/window?oauthProfile=myprofile").routeId("secureWs")
                        .autoStartup(false)
                        .process(exchange -> routeInvocations.incrementAndGet())
                        .to("mock:message");
            }
        };
    }

    private static final class RecordingListener implements WebSocket.Listener {

        private final List<String> received = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            received.add(data.toString());
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
