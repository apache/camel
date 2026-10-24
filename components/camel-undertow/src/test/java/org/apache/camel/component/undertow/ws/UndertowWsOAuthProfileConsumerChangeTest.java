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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.undertow.websockets.core.CloseMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a WebSocket channel is checked against the OAuth profile of the consumer that currently serves the
 * path: when a producer route keeps the shared {@code CamelWebSocketHandler} registered and the consumer of the path is
 * replaced by one with another OAuth profile, a channel validated by the previous profile is closed, so it is neither
 * sent messages nor served by the new route.
 */
public class UndertowWsOAuthProfileConsumerChangeTest extends BaseUndertowTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new TwoProfilesTokenValidationFactory());
        // the expected issuer names the profile, so that the factory can tell the profiles apart
        context.getPropertiesComponent().addInitialProperty("camel.oauth.first.expected-issuer", "first");
        context.getPropertiesComponent().addInitialProperty("camel.oauth.second.expected-issuer", "second");
        return context;
    }

    @Test
    public void channelValidatedByThePreviousProfileIsNotServed() throws Exception {
        RecordingListener first = new RecordingListener();
        connect("first-token", first);

        context.getRouteController().stopRoute("firstWs");
        context.removeRoute("firstWs");
        context.getRouteController().startRoute("secondWs");

        // the channel validated by the previous profile is closed as soon as the new consumer serves the path
        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, first.closeCode.orTimeout(10, TimeUnit.SECONDS).join());

        RecordingListener second = new RecordingListener();
        connect("second-token", second);

        MockEndpoint mock = getMockEndpoint("mock:second");
        mock.expectedBodiesReceived("from second");

        template.sendBody("direct:broadcast", "broadcast");
        await().atMost(10, TimeUnit.SECONDS).until(() -> second.received.contains("broadcast"));

        assertTrue(first.received.isEmpty());

        // the channel validated by the current profile keeps working
        second.webSocket.sendText("from second", true).orTimeout(5, TimeUnit.SECONDS).join();
        mock.assertIsSatisfied();
    }

    private WebSocket connect(String token, RecordingListener listener) {
        WebSocket webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://localhost:" + getPort() + "/change"), listener)
                .orTimeout(5, TimeUnit.SECONDS).join();
        listener.webSocket = webSocket;
        return webSocket;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:broadcast").routeId("broadcast")
                        .to("undertow:ws://localhost:{{port}}/change?sendToAll=true");

                from("undertow:ws://localhost:{{port}}/change?oauthProfile=first").routeId("firstWs")
                        .to("mock:first");

                from("undertow:ws://localhost:{{port}}/change?oauthProfile=second").routeId("secondWs")
                        .autoStartup(false)
                        .to("mock:second");
            }
        };
    }

    private static final class RecordingListener implements WebSocket.Listener {

        private final List<String> received = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        private volatile WebSocket webSocket;

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

    /**
     * Accepts the token {@code <profile>-token} for each profile, identified by its expected issuer.
     */
    private static final class TwoProfilesTokenValidationFactory implements OAuthTokenValidationFactory {

        @Override
        public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
            String profile = config.getExpectedIssuer();
            if ((profile + "-token").equals(token)) {
                return OAuthTokenValidationResult.valid(profile + "-user", profile, List.of(), List.of(), Map.of(), 0);
            }
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
        }

        @Override
        public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
            throw new UnsupportedOperationException("the profiles are resolved to their configuration");
        }

        @Override
        public void validateConfiguration(OAuthTokenValidationConfig config) {
        }

        @Override
        public void validateConfiguration(CamelContext context, String profileName) {
        }
    }
}
