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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketHandshakeHeadersTest extends VertxWebSocketTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketHandshakeHeadersTest.class);

    Map<String, String> headers = Map.of("Authorization", "Bearer example", "ApiSign", "12345");

    @Test
    public void testHandshakeHeadersAsProducer() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        Route route = router.route("/ws");
        route.handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext context) {
                HttpServerRequest request = context.request();

                String authorizationHeader = request.getHeader("Authorization");
                assertEquals(headers.get("Authorization"), authorizationHeader, "Authorization header is incorrect.");

                String apiSignHeader = request.getHeader("ApiSign");
                assertEquals(headers.get("ApiSign"), apiSignHeader, "ApiSign header is incorrect.");

                String connectionHeader = request.headers().get(HttpHeaders.CONNECTION);
                if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
                    context.response().setStatusCode(400);
                    context.response().end("Can \"Upgrade\" only to \"WebSocket\".");
                } else {
                    // we're about to upgrade the connection, which means an asynchronous
                    // operation. We have to pause the request otherwise we will loose the
                    // body of the request once the upgrade completes
                    final boolean parseEnded = request.isEnded();
                    if (!parseEnded) {
                        request.pause();
                    }
                    // upgrade
                    request.toWebSocket(toWebSocket -> {
                        if (toWebSocket.succeeded()) {
                            // resume the parsing
                            if (!parseEnded) {
                                request.resume();
                            }
                            // handle the websocket session as usual
                            ServerWebSocket webSocket = toWebSocket.result();
                            webSocket.textMessageHandler(new Handler<String>() {
                                @Override
                                public void handle(String message) {
                                    latch.countDown();
                                }
                            });
                        } else {
                            // the upgrade failed
                            context.fail(toWebSocket.cause());
                        }
                    });
                }
            }
        });

        HttpServerOptions options = new HttpServerOptions();

        VertxWebsocketHostConfiguration configuration = new VertxWebsocketHostConfiguration(vertx, router, options, null);
        VertxWebsocketHostKey key = new VertxWebsocketHostKey("localhost", 0);
        VertxWebsocketHost host = new VertxWebsocketHost(context, configuration, key);
        host.start();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("headers", headers);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toD("vertx-websocket:localhost:${header.port}/ws?handshakeHeaders=#headers");
            }
        });

        context.start();
        try {
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBodyAndHeader("direct:start", "Hello world", "port", host.getPort());
            template.sendBodyAndHeader("direct:start", "Hello world after handshake", "port", host.getPort());

            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            try {
                host.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop Vert.x server", e);
            }
            context.stop();
        }
    }

    @Test
    public void testHandshakeHeadersAsConsumer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        Route route = router.route("/ws");
        route.handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext context) {
                HttpServerRequest request = context.request();

                String authorizationHeader = request.getHeader("Authorization");
                assertEquals(headers.get("Authorization"), authorizationHeader, "Authorization header is incorrect.");

                String apiSignHeader = request.getHeader("ApiSign");
                assertEquals(headers.get("ApiSign"), apiSignHeader, "ApiSign header is incorrect.");

                String connectionHeader = request.headers().get(HttpHeaders.CONNECTION);
                if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
                    context.response().setStatusCode(400);
                    context.response().end("Can \"Upgrade\" only to \"WebSocket\".");
                } else {
                    // we're about to upgrade the connection, which means an asynchronous
                    // operation. We have to pause the request otherwise we will loose the
                    // body of the request once the upgrade completes
                    final boolean parseEnded = request.isEnded();
                    if (!parseEnded) {
                        request.pause();
                    }

                    // upgrade
                    request.toWebSocket(toWebSocket -> {
                        if (toWebSocket.succeeded()) {
                            // resume the parsing
                            if (!parseEnded) {
                                request.resume();
                            }
                            // Send a text message to consumer
                            ServerWebSocket webSocket = toWebSocket.result();
                            webSocket.writeTextMessage("Hello World");
                            latch.countDown();
                        } else {
                            // the upgrade failed
                            context.fail(toWebSocket.cause());
                        }
                    });
                }
            }
        });

        HttpServerOptions options = new HttpServerOptions();

        VertxWebsocketHostConfiguration configuration = new VertxWebsocketHostConfiguration(vertx, router, options, null);
        VertxWebsocketHostKey key = new VertxWebsocketHostKey("localhost", 0);
        VertxWebsocketHost host = new VertxWebsocketHost(context, configuration, key);
        host.start();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("headers", headers);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/ws?consumeAsClient=true&handshakeHeaders=#headers", host.getPort())
                        .log("Consume websocket message ${body}")
                        .to("mock:result");
            }
        });

        context.start();
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello World");
            mockEndpoint.assertIsSatisfied();
        } finally {
            try {
                host.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop Vert.x server", e);
            }
            context.stop();
        }
    }

    @Override
    protected void startCamelContext() {
    }
}
