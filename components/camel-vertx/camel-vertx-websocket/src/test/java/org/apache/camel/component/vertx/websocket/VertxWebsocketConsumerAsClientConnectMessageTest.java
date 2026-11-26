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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * The client consumer sends the connectMessage when it connects and after each reconnect. The server is a plain Vert.x
 * HTTP server that only sends a message to a client that sent "subscribe", like a feed that needs a subscribe request.
 */
class VertxWebsocketConsumerAsClientConnectMessageTest extends VertxWebSocketTestSupport {

    private final Set<ServerWebSocket> open = ConcurrentHashMap.newKeySet();
    private final AtomicInteger subscribes = new AtomicInteger();
    private Vertx serverVertx;

    @Override
    protected void setupResources() throws Exception {
        serverVertx = Vertx.vertx();
        HttpServer server = serverVertx.createHttpServer();
        server.webSocketHandler(webSocket -> {
            open.add(webSocket);
            webSocket.closeHandler(closed -> open.remove(webSocket));
            webSocket.textMessageHandler(text -> {
                if ("subscribe".equals(text)) {
                    webSocket.writeTextMessage("subscribed " + subscribes.incrementAndGet());
                }
            });
        });
        server.listen(port.getPort(), "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (serverVertx != null) {
            serverVertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void sendsTheConnectMessageWhenItConnects() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("subscribed 1");

        mock.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void sendsTheConnectMessageAgainAfterAReconnect() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("subscribed 1");
        mock.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        // the server drops the connection: the consumer reconnects and subscribes again
        mock.reset();
        mock.expectedBodiesReceived("subscribed 2");
        List.copyOf(open).forEach(ServerWebSocket::close);

        mock.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> open.size() == 1);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/feed?consumeAsClient=true&connectMessage=subscribe&reconnectInterval=100",
                        port.getPort())
                        .to("mock:result");
            }
        };
    }
}
