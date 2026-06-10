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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.StubOAuthTokenValidationFactory;
import org.apache.camel.component.undertow.UndertowConstants;
import org.apache.camel.component.undertow.UndertowConstants.EventType;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UndertowWsOAuthProfileTest extends BaseUndertowTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        return context;
    }

    @Test
    public void validBearerTokenConnectsAndPropagatesResult() throws Exception {
        MockEndpoint onOpen = getMockEndpoint("mock:onOpen");
        onOpen.expectedBodiesReceived("undertow-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null");

        MockEndpoint message = getMockEndpoint("mock:message");
        message.expectedBodiesReceived("undertow-user:https://issuer.example:[camel-api]:[read]:acme:1234567890:null:hello");

        WebSocket webSocket = connect("valid-token");
        try {
            onOpen.assertIsSatisfied();

            webSocket.sendText("hello", true).orTimeout(5, TimeUnit.SECONDS).join();
            message.assertIsSatisfied();
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").orTimeout(5, TimeUnit.SECONDS).join();
        }
    }

    @Test
    public void rejectsMissingBearerTokenBeforeConnect() {
        WebSocketHandshakeException exception = assertRejected(null);

        assertEquals(401, exception.getResponse().statusCode());
        assertEquals("Bearer",
                exception.getResponse().headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsInvalidBearerTokenBeforeConnect() {
        WebSocketHandshakeException exception = assertRejected("invalid-token");

        assertEquals(401, exception.getResponse().statusCode());
        assertEquals("Bearer error=\"invalid_token\"",
                exception.getResponse().headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsMalformedBearerTokenBeforeConnect() {
        WebSocketHandshakeException exception = assertRejectedAuthorization("Bearer valid token");

        assertEquals(400, exception.getResponse().statusCode());
        assertEquals("Bearer error=\"invalid_request\"",
                exception.getResponse().headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void mapsInfrastructureErrorToServiceUnavailableBeforeConnect() {
        WebSocketHandshakeException exception = assertRejected("error-token");

        assertEquals(503, exception.getResponse().statusCode());
        assertNull(exception.getResponse().headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(0, routeInvocations.get());
    }

    private WebSocket connect(String token) {
        return connectAuthorization(token == null ? null : "Bearer " + token);
    }

    private WebSocket connectAuthorization(String authorization) {
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + getPort() + "/secure"), new NoopWebSocketListener())
                .orTimeout(5, TimeUnit.SECONDS).join();
    }

    private WebSocketHandshakeException assertRejected(String token) {
        CompletionException exception = assertThrows(CompletionException.class, () -> connect(token));
        return assertInstanceOf(WebSocketHandshakeException.class, exception.getCause());
    }

    private WebSocketHandshakeException assertRejectedAuthorization(String authorization) {
        CompletionException exception = assertThrows(CompletionException.class, () -> connectAuthorization(authorization));
        return assertInstanceOf(WebSocketHandshakeException.class, exception.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://0.0.0.0:{{port}}/secure?oauthProfile=myprofile&fireWebSocketChannelEvents=true")
                        .process(exchange -> routeInvocations.incrementAndGet())
                        .choice()
                        .when(header(UndertowConstants.EVENT_TYPE_ENUM).isEqualTo(EventType.ONOPEN))
                        .process(exchange -> {
                            OAuthTokenValidationResult result = exchange.getProperty(
                                    OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT,
                                    OAuthTokenValidationResult.class);
                            WebSocketHttpExchange transportExchange = exchange.getMessage().getHeader(
                                    UndertowConstants.EXCHANGE, WebSocketHttpExchange.class);
                            String authorization
                                    = transportExchange.getRequestHeader("Authorization");
                            assertNull(authorization);
                            exchange.getMessage().setBody(result.getSubject() + ":"
                                                          + result.getIssuer() + ":"
                                                          + result.getAudience() + ":"
                                                          + result.getScopes() + ":"
                                                          + result.getClaim("tenant") + ":"
                                                          + result.getExpiresAt() + ":"
                                                          + authorization);
                        })
                        .to("mock:onOpen")
                        .when(body().isNotNull())
                        .process(exchange -> {
                            OAuthTokenValidationResult result = exchange.getProperty(
                                    OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT,
                                    OAuthTokenValidationResult.class);
                            exchange.getMessage().setBody(result.getSubject() + ":"
                                                          + result.getIssuer() + ":"
                                                          + result.getAudience() + ":"
                                                          + result.getScopes() + ":"
                                                          + result.getClaim("tenant") + ":"
                                                          + result.getExpiresAt() + ":"
                                                          + exchange.getMessage().getHeader(
                                                                  "Authorization")
                                                          + ":" + exchange.getMessage().getBody(String.class));
                        })
                        .to("mock:message");
            }
        };
    }

    private static final class NoopWebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }
}
