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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumers bound to the same host and port share one {@link VertxWebsocketHost}, so stopping one of them must leave
 * the others able to serve and, later, to disconnect.
 */
public class VertxWebsocketMultiConsumerLifecycleTest extends VertxWebSocketTestSupport {

    private Map<VertxWebsocketHostKey, VertxWebsocketHost> hostRegistry() {
        return context.getComponent("vertx-websocket", VertxWebsocketComponent.class).getVertxHostRegistry();
    }

    @Test
    void theHostOutlivesTheFirstConsumerToStop() throws Exception {
        assertEquals(1, hostRegistry().size());

        context.getRouteController().stopRoute("a");

        // the host still serves route b, so it must still be reachable - it used to be dropped from the
        // registry here, which left b unable to ever disconnect and its server running for good
        assertEquals(1, hostRegistry().size());

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello b");

        template.sendBody("vertx-websocket:localhost:" + port + "/test/b", "b");

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void theHostGoesWithTheLastConsumerToStop() throws Exception {
        assertEquals(1, hostRegistry().size());

        context.getRouteController().stopRoute("a");
        context.getRouteController().stopRoute("b");

        assertTrue(hostRegistry().isEmpty());
    }

    @Test
    void stoppingAConsumerTwiceIsHarmless() throws Exception {
        context.getRouteController().stopRoute("a");
        context.getRouteController().startRoute("a");
        context.getRouteController().stopRoute("a");

        assertEquals(1, hostRegistry().size());
    }

    @Test
    void consumersSharingAHostCanBeStoppedConcurrently() throws Exception {
        assertEquals(1, hostRegistry().size());

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (String routeId : List.of("a", "b")) {
                executor.submit(() -> {
                    try {
                        startLine.await();
                        context.getRouteController().stopRoute(routeId);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startLine.countDown();
            assertTrue(finished.await(30, TimeUnit.SECONDS), "the consumers did not stop in time");
        } finally {
            executor.shutdownNow();
        }

        assertTrue(failures.isEmpty(), "stopping the consumers concurrently failed with " + failures);
        assertTrue(hostRegistry().isEmpty(), "the host outlived both of its consumers");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test/a", port.getPort())
                        .routeId("a")
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                fromF("vertx-websocket:localhost:%d/test/b", port.getPort())
                        .routeId("b")
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        };
    }
}
