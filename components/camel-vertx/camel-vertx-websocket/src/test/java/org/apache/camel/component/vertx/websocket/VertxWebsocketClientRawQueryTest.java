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

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The query of a remote WebSocket URI reaches the server as written, with RAW values that contain +, & and spaces. The
 * server is a plain Vert.x HTTP server that keeps the query of each handshake by path, and sends it back.
 */
class VertxWebsocketClientRawQueryTest extends VertxWebSocketTestSupport {

    private final Map<String, String> queries = new ConcurrentHashMap<>();
    private Vertx serverVertx;

    @Override
    protected void setupResources() throws Exception {
        serverVertx = Vertx.vertx();
        HttpServer server = serverVertx.createHttpServer();
        server.webSocketHandler(webSocket -> {
            queries.put(webSocket.path(), String.valueOf(webSocket.query()));
            webSocket.writeTextMessage(String.valueOf(webSocket.query()));
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
    void clientConsumerSendsTheRawValuesAsWritten() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:consumer");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        assertParameters(mock.getReceivedExchanges().get(0).getMessage().getBody(String.class));
    }

    @Test
    void producerSendsTheRawValuesAsWritten() {
        // no path in the endpoint URI: the component fills in "/" and must keep the query as it is encoded
        template.sendBody("direct:produce", "Hello");

        await().atMost(20, TimeUnit.SECONDS).until(() -> queries.containsKey("/"));
        assertParameters(queries.get("/"));
    }

    private static void assertParameters(String query) {
        Map<String, List<String>> parameters = new QueryStringDecoder(query, false).parameters();
        assertEquals(List.of("a+b"), parameters.get("token"), "query " + query);
        assertEquals(List.of("x&y=z"), parameters.get("sig"), "query " + query);
        assertEquals(List.of("a b"), parameters.get("text"), "query " + query);
        assertEquals(3, parameters.size(), "query " + query);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/consumer?consumeAsClient=true"
                      + "&token=RAW(a+b)&sig=RAW(x&y=z)&text=RAW(a b)",
                        port.getPort())
                        .to("mock:consumer");

                from("direct:produce")
                        .toF("vertx-websocket:localhost:%d?token=RAW(a+b)&sig=RAW(x&y=z)&text=RAW(a b)", port.getPort());
            }
        };
    }
}
