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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.util.StatusCodes;
import io.undertow.websockets.core.CloseMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.StubOAuthTokenValidationFactory;
import org.apache.camel.component.undertow.UndertowBasicAuthHandler;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket endpoints apply the allowedRoles and handlers options without a security provider, as HTTP endpoints do.
 */
class UndertowWsSecurityWithoutProviderTest extends BaseUndertowTest {

    private final AtomicInteger lateRouteInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        context.getRegistry().bind("basicAuth", new UndertowBasicAuthHandler());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/roles?allowedRoles=user").to("mock:roles");

                from("undertow:ws://localhost:{{port}}/oauthRoles?oauthProfile=myprofile&allowedRoles=user")
                        .to("mock:oauthRoles");

                from("undertow:ws://localhost:{{port}}/basic?handlers=#basicAuth").to("mock:basic");

                from("direct:late").to("undertow:ws://localhost:{{port}}/late?sendToAll=true");
                from("undertow:ws://localhost:{{port}}/late?allowedRoles=user").routeId("late").autoStartup(false)
                        .process(exchange -> lateRouteInvocations.incrementAndGet());
            }
        };
    }

    @Test
    void allowedRolesWithoutProviderRefusesTheUpgrade() {
        assertRefused("/roles", null, StatusCodes.FORBIDDEN);
    }

    @Test
    void allowedRolesWithoutProviderRefusesAValidBearerToken() {
        assertRefused("/oauthRoles", "Bearer valid-token", StatusCodes.FORBIDDEN);
    }

    @Test
    void handlersRunBeforeTheUpgrade() throws Exception {
        assertRefused("/basic", null, StatusCodes.UNAUTHORIZED);

        getMockEndpoint("mock:basic").expectedBodiesReceived("hello");
        String credentials = Base64.getEncoder().encodeToString("guest:secret".getBytes(StandardCharsets.UTF_8));
        WebSocket webSocket = connect("/basic", "Basic " + credentials, new RecordingListener());
        webSocket.sendText("hello", true).join();

        MockEndpoint.assertIsSatisfied(context);
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void connectionOpenedBeforeTheConsumerStartedIsNotServed() throws Exception {
        // only the producer uses the path, and it has no security settings, so the upgrade is not checked
        RecordingListener listener = new RecordingListener();
        WebSocket webSocket = connect("/late", null, listener);

        context.getRouteController().startRoute("late");

        // the connection does not get what the producer sends
        template.sendBody("direct:late", "broadcast");
        // and its messages do not reach the route: it is closed instead
        webSocket.sendText("hello", true).join();

        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, listener.closeCode.orTimeout(10, TimeUnit.SECONDS).join());
        assertTrue(listener.received.isEmpty());
        assertEquals(0, lateRouteInvocations.get());
    }

    private void assertRefused(String path, String authorization, int statusCode) {
        CompletionException thrown
                = assertThrows(CompletionException.class, () -> connect(path, authorization, new RecordingListener()));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(statusCode, handshake.getResponse().statusCode());
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
