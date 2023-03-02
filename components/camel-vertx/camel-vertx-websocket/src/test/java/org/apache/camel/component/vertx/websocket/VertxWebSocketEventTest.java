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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebSocketEventTest extends VertxWebSocketTestSupport {

    private static final String MESSAGE_BODY = "Hello World";
    private final CompletableFuture<ServerWebSocket> webSocketFuture = new CompletableFuture<>();

    @BindToRegistry("serverOptions")
    public HttpServerOptions serverOptions() {
        HttpServerOptions options = new HttpServerOptions();
        options.setMaxWebSocketMessageSize(MESSAGE_BODY.length());
        return options;
    }

    @Test
    void webSocketEvents() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceivedInAnyOrder("WebSocket Open", "WebSocket Message", "WebSocket Error",
                "WebSocket Close");

        template.sendBody("vertx-websocket:localhost:" + port + "/test", MESSAGE_BODY);

        ServerWebSocket webSocket = webSocketFuture.get(5000, TimeUnit.SECONDS);
        assertNotNull(webSocket);

        // Trigger error event (message length > max allowed)
        template.sendBody("vertx-websocket:localhost:" + port + "/test", MESSAGE_BODY + " Again");

        // Trigger close event
        CountDownLatch latch = new CountDownLatch(1);
        webSocket.close(event -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class)
                        .handled(true)
                        .choice()
                        .when(simple("${header.CamelVertxWebsocket.event} == 'ERROR'"))
                        .setBody().constant("WebSocket Error")
                        .to("mock:result")
                        .endChoice();

                fromF("vertx-websocket:localhost:%d/test?fireWebSocketConnectionEvents=true&serverOptions=#serverOptions&bridgeErrorHandler=true",
                        port)
                        .choice()
                        .when(simple("${header.CamelVertxWebsocket.event} == 'OPEN'"))
                        .process(exchange -> {
                            Message message = exchange.getMessage();
                            webSocketFuture.complete(message.getBody(ServerWebSocket.class));
                        })
                        .setBody().constant("WebSocket Open")
                        .to("mock:result")
                        .endChoice()

                        .when(simple("${header.CamelVertxWebsocket.event} == 'MESSAGE'"))
                        .setBody().constant("WebSocket Message")
                        .to("mock:result")
                        .endChoice()

                        .when(simple("${header.CamelVertxWebsocket.event} == 'CLOSE'"))
                        .setBody().constant("WebSocket Close")
                        .to("mock:result")
                        .endChoice();
            }
        };
    }
}
