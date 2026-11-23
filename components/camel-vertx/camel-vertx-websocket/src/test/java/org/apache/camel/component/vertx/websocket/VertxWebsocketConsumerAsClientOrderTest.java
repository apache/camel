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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * The client consumer routes the messages of its WebSocket in the order they are received, unless parallelProcessing is
 * set. The server is a plain Vert.x HTTP server, which pushes numbered messages when the test asks for them. The route
 * waits a random few milliseconds for each message, so messages routed in parallel would overtake each other.
 */
class VertxWebsocketConsumerAsClientOrderTest extends VertxWebSocketTestSupport {

    private static final int MESSAGES = 50;

    private final Map<String, ServerWebSocket> open = new ConcurrentHashMap<>();
    private Vertx serverVertx;

    @Override
    protected void setupResources() throws Exception {
        serverVertx = Vertx.vertx();
        HttpServer server = serverVertx.createHttpServer();
        server.webSocketHandler(webSocket -> {
            open.put(webSocket.path(), webSocket);
            webSocket.closeHandler(closed -> open.remove(webSocket.path(), webSocket));
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
    void routesTheMessagesInTheOrderReceived() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:ordered");
        mock.expectedBodiesReceived(numbers());

        push("/ordered");

        mock.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Test
    void routesTheMessagesInParallelWhenAskedTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel");
        mock.expectedBodiesReceivedInAnyOrder(numbers());

        push("/parallel");

        mock.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private void push(String path) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> open.containsKey(path));
        ServerWebSocket webSocket = open.get(path);
        // written in order, from one thread
        numbers().forEach(webSocket::writeTextMessage);
    }

    private static List<String> numbers() {
        return IntStream.rangeClosed(1, MESSAGES).mapToObj(String::valueOf).toList();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/ordered?consumeAsClient=true", port.getPort())
                        .delay(simple("${random(0,10)}")).syncDelayed()
                        .to("mock:ordered");

                fromF("vertx-websocket:localhost:%d/parallel?consumeAsClient=true&parallelProcessing=true", port.getPort())
                        .delay(simple("${random(0,10)}")).syncDelayed()
                        .to("mock:parallel");
            }
        };
    }
}
