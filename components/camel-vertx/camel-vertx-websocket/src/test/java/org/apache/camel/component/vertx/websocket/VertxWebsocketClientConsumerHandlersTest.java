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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a client consumer started against an already-open WebSocket (CAMEL-25601) correctly registers its
 * message, close, and exception handlers, and that stopping the consumer detaches them so no messages leak through.
 * <p>
 * Two scenarios:
 * <ol>
 * <li>The consumer route is stopped and restarted while the server is still up and the connection is healthy; the
 * restarted consumer must receive messages (previously it registered no handlers).</li>
 * <li>A producer on the same {@code consumeAsClient=true} endpoint opens the WebSocket first; a consumer started
 * afterward must register its handlers on that already-open socket.</li>
 * </ol>
 */
public class VertxWebsocketClientConsumerHandlersTest extends VertxWebSocketTestSupport {

    /**
     * Scenario 1: client consumer route stopped then restarted while the WebSocket stays open.
     * <p>
     * When a route is stopped and restarted (while the server is healthy), the endpoint is not stopped and the
     * WebSocket stays open. The restarted consumer must register its handlers on the existing socket and continue to
     * receive messages.
     */
    @Test
    void clientConsumerReceivesMessagesAfterRouteStopAndRestart() throws Exception {
        String serverUri = String.format("vertx-websocket:localhost:%d/echo", port.getPort());

        // Start the client-consumer route first (it uses noAutoStartup).
        context.getRouteController().startRoute("client-consumer");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("before-restart");
        template.sendBody(serverUri, "before-restart");
        mock.assertIsSatisfied();

        // Stop the consumer route. The WebSocket stays open (the endpoint is not stopped).
        context.getRouteController().stopRoute("client-consumer");

        // While the route is stopped, messages must NOT be delivered to the mock (stop half of CAMEL-25601).
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        template.sendBody(serverUri, "while-stopped");
        mock.assertIsSatisfied();

        // Restart the consumer route. The WebSocket is already open — the fix ensures handlers are re-registered.
        context.getRouteController().startRoute("client-consumer");

        mock.reset();
        mock.expectedBodiesReceived("after-restart");
        template.sendBody(serverUri, "after-restart");
        mock.assertIsSatisfied();
    }

    /**
     * Scenario 2: consumer started after a producer on the same endpoint has already opened the WebSocket.
     * <p>
     * A producer sending via the {@code consumeAsClient=true} endpoint opens (and caches) the client WebSocket on that
     * endpoint. A consumer route started afterward must register its handlers on the same already-open socket.
     */
    @Test
    void clientConsumerReceivesMessagesWhenStartedAfterProducerOpenedSocket() throws Exception {
        String serverUri = String.format("vertx-websocket:localhost:%d/echo", port.getPort());
        String clientUri = String.format("vertx-websocket:localhost:%d/echo?consumeAsClient=true", port.getPort());

        // Send via the consumeAsClient endpoint to open its WebSocket before the consumer route starts.
        // The server echoes the message; this reply arrives on the socket we just opened.
        template.sendBody(clientUri, "warm-up");

        // Now start the client-consumer route: the WebSocket on the consumeAsClient endpoint is already open.
        context.getRouteController().startRoute("client-consumer");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        // Send another message so the server echoes it to the consumer.
        template.sendBody(serverUri, "after-consumer-start");
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Echo server: reflects every message back to all connected clients.
                fromF("vertx-websocket:localhost:%d/echo", port.getPort()).routeId("server")
                        .toF("vertx-websocket:localhost:%d/echo?sendToAll=true", port.getPort());

                // Client consumer: connects as a WebSocket client and forwards received messages to the mock.
                // noAutoStartup=true so the tests can control when it connects.
                fromF("vertx-websocket:localhost:%d/echo?consumeAsClient=true", port.getPort())
                        .routeId("client-consumer")
                        .noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
