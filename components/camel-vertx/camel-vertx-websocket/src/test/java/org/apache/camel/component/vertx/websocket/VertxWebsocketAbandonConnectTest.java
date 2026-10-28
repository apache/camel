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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.WebSocket;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A connect the endpoint stopped waiting for must not leave an open WebSocket behind (CAMEL-25693).
 */
public class VertxWebsocketAbandonConnectTest extends VertxWebSocketTestSupport {

    @Test
    void connectCompletedAfterTheTimeoutIsClosed() throws Exception {
        WebSocket webSocket = openWebSocketConnection("localhost", port.getPort(), "/test", message -> {
        });
        assertFalse(webSocket.isClosed());

        // the connect completed between the timed out wait and the cancel
        CompletableFuture<WebSocket> future = CompletableFuture.completedFuture(webSocket);
        VertxWebsocketEndpoint.abandonConnect(future);

        await().atMost(10, TimeUnit.SECONDS).until(webSocket::isClosed);
    }

    @Test
    void pendingConnectIsCancelled() {
        CompletableFuture<WebSocket> future = new CompletableFuture<>();
        VertxWebsocketEndpoint.abandonConnect(future);

        // the connect handler sees the cancelled future and closes the WebSocket itself
        assertTrue(future.isCancelled());
    }

    @Test
    void failedConnectIsIgnored() {
        CompletableFuture<WebSocket> future = CompletableFuture.failedFuture(new IOException("Connection refused"));
        VertxWebsocketEndpoint.abandonConnect(future);

        assertTrue(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test", port.getPort())
                        .log("${body}");
            }
        };
    }
}
