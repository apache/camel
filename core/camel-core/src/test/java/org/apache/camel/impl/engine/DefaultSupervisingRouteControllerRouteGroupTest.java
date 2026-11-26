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
package org.apache.camel.impl.engine;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starting or stopping a route group with the supervising route controller does what starting or stopping each of its
 * routes does: a stopped route is no longer started by its restart task, a started route is no longer exhausted, and a
 * route that fails to start is restarted by the controller.
 */
class DefaultSupervisingRouteControllerRouteGroupTest extends ContextTestSupport {

    private final AtomicBoolean failing = new AtomicBoolean(true);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testStopRouteGroupCancelsRestart() throws Exception {
        SupervisingRouteController src = setUp(-1);
        context.start();

        // the route failed to start and waits for its next restart attempt
        await().atMost(Duration.ofSeconds(10)).until(() -> src.getRestartingRouteState("flaky") != null
                && "Started".equals(context.getRouteController().getRouteStatus("other").toString()));

        context.getRouteController().stopRouteGroup("myGroup");
        assertNull(src.getRestartingRouteState("flaky"), "the stopped route should no longer be restarted");

        // the cause is fixed: the route stays stopped, as it was stopped with its group
        failing.set(false);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .until(() -> "Stopped".equals(context.getRouteController().getRouteStatus("flaky").toString()));
        assertEquals("Stopped", context.getRouteController().getRouteStatus("other").toString());
    }

    @Test
    void testStartRouteGroupAfterExhausted() throws Exception {
        SupervisingRouteController src = setUp(2);
        src.setUnhealthyOnExhausted(true);
        context.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, src.getExhaustedRoutes().size()));
        assertTrue(((DefaultSupervisingRouteController) src).hasUnhealthyRoutes());

        // the cause is fixed, and the route group is started
        failing.set(false);
        context.getRouteController().startRouteGroup("myGroup");

        assertEquals("Started", context.getRouteController().getRouteStatus("flaky").toString());
        assertEquals(0, src.getExhaustedRoutes().size(), "the started route should no longer be exhausted");
        assertFalse(((DefaultSupervisingRouteController) src).hasUnhealthyRoutes(),
                "the started route should no longer be unhealthy");
    }

    @Test
    void testStartRouteGroupFailureIsRestarted() throws Exception {
        SupervisingRouteController src = setUp(-1);
        failing.set(false);
        context.start();
        // the supervising route controller starts the routes after its initial delay
        await().atMost(Duration.ofSeconds(10))
                .until(() -> "Started".equals(context.getRouteController().getRouteStatus("flaky").toString()));

        context.getRouteController().stopRouteGroup("myGroup");
        assertEquals("Stopped", context.getRouteController().getRouteStatus("flaky").toString());

        // the route fails to start with its group, and is restarted by the controller once the cause is fixed
        failing.set(true);
        assertThrows(Exception.class, () -> context.getRouteController().startRouteGroup("myGroup"));
        assertNotNull(src.getRestartingRouteState("flaky"), "the route should be restarted by the controller");

        failing.set(false);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> "Started".equals(context.getRouteController().getRouteStatus("flaky").toString()));
    }

    private SupervisingRouteController setUp(int maxAttempts) throws Exception {
        context.addComponent("flaky", new FlakyComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:start").routeId("flaky").routeGroup("myGroup").to("mock:result");
                from("direct:other").routeId("other").routeGroup("myGroup").to("mock:other");
            }
        });

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(50);
        src.setBackOffMaxAttempts(maxAttempts);
        src.setInitialDelay(10);
        return src;
    }

    private final class FlakyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() throws Exception {
                            if (failing.get()) {
                                throw new IllegalStateException("Cannot connect");
                            }
                            super.doStart();
                        }
                    };
                }
            };
        }
    }
}
