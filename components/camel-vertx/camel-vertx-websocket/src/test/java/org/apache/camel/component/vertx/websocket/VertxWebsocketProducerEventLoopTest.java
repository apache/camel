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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Context;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A send to a remote WebSocket server completes on the Vert.x event loop. The route must continue on another thread: on
 * the event loop, a second producer that connects to another server waits for a connect that needs that very event
 * loop, and the route hangs until the connect timeout (CAMEL-27507).
 */
public class VertxWebsocketProducerEventLoopTest extends VertxWebSocketTestSupport {

    private final AtomicReference<String> threadAfterSend = new AtomicReference<>();
    private final AtomicReference<Boolean> onEventLoopAfterSend = new AtomicReference<>();

    @Test
    public void testRouteContinuesOffTheEventLoopAfterASend() throws Exception {
        MockEndpoint first = getMockEndpoint("mock:first");
        first.expectedBodiesReceived("Hello");
        MockEndpoint second = getMockEndpoint("mock:second");
        second.expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        // the second producer connects to another server after the first send: it used to block the event loop
        // until the connect timeout, and the message never arrived
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertFalse(onEventLoopAfterSend.get(),
                "The route continued on the Vert.x event loop after the send: " + threadAfterSend.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/first", port.getPort())
                        .to("mock:first");

                fromF("vertx-websocket:localhost:%d/second", port.getPort())
                        .to("mock:second");

                // two producers to different URIs: each has its own endpoint, so the second one opens a new connection
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/first", port.getPort())
                        .process(exchange -> {
                            threadAfterSend.set(Thread.currentThread().getName());
                            onEventLoopAfterSend.set(Context.isOnEventLoopThread());
                        })
                        .toF("vertx-websocket:localhost:%d/second", port.getPort());

            }
        };
    }
}
