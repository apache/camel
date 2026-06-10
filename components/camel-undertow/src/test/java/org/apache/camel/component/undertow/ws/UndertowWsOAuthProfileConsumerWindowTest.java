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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

/**
 * Verifies that WebSocket channels whose handshake completed while no consumer was set on the shared
 * {@code CamelWebSocketHandler} (here: while the secured consumer route was stopped and a producer route kept the
 * handler registered) cannot deliver messages to the route once the secured consumer is back.
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
    public void closesChannelEstablishedWhileConsumerWasStopped() throws Exception {
        // stop the secured consumer; the producer route on the same path keeps the shared WebSocket handler registered
        context.getRouteController().stopRoute("secureWs");

        // the handshake completes without OAuth validation because no consumer is set on the shared handler
        CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        WebSocket webSocket = connectWithoutAuthorization(closeCode);

        // once the secured consumer is back, the unauthenticated channel must not reach the route
        context.getRouteController().startRoute("secureWs");

        webSocket.sendText("hello", true).orTimeout(5, TimeUnit.SECONDS).join();

        assertEquals(CloseMessage.MSG_VIOLATES_POLICY, closeCode.orTimeout(10, TimeUnit.SECONDS).join());
        assertEquals(0, routeInvocations.get());
    }

    private WebSocket connectWithoutAuthorization(CompletableFuture<Integer> closeCode) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + getPort() + "/window"), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closeCode.complete(statusCode);
                        return null;
                    }
                })
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
                        .process(exchange -> routeInvocations.incrementAndGet())
                        .to("mock:message");
            }
        };
    }
}
