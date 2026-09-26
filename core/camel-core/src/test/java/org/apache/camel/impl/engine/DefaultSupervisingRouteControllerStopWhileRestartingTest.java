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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteRestartingEvent;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.util.backoff.BackOffTimer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route that is stopped manually while its restart attempt is running must not be started by that attempt.
 */
class DefaultSupervisingRouteControllerStopWhileRestartingTest extends ContextTestSupport {

    private final AtomicBoolean failing = new AtomicBoolean(true);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testStopRouteWhileRestartAttemptIsRunning() throws Exception {
        CountDownLatch attemptStarted = new CountDownLatch(1);
        CountDownLatch routeStopped = new CountDownLatch(1);
        AtomicReference<BackOffTimer.Task> restartTask = new AtomicReference<>();

        context.addComponent("flaky", new FlakyComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:start").routeId("flaky").to("mock:result");
            }
        });

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(10);
        src.setBackOffMaxAttempts(3);
        src.setInitialDelay(10);

        context.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof RouteRestartingEvent rre && "flaky".equals(rre.getRoute().getRouteId())
                        && restartTask.compareAndSet(null, src.getRestartingRouteState("flaky"))) {
                    // the restart attempt is about to start the route: let the route be stopped first
                    attemptStarted.countDown();
                    assertTrue(routeStopped.await(10, TimeUnit.SECONDS));
                }
            }
        });

        context.start();
        assertTrue(attemptStarted.await(10, TimeUnit.SECONDS));

        // the cause is fixed, but the route is stopped manually while the restart attempt is running
        failing.set(false);
        context.getRouteController().stopRoute("flaky");
        assertEquals("Stopped", context.getRouteController().getRouteStatus("flaky").toString());
        routeStopped.countDown();

        // the restart attempt finishes, and the route stays stopped
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> restartTask.get().getStatus() == BackOffTimer.Task.Status.Completed);
        assertEquals("Stopped", context.getRouteController().getRouteStatus("flaky").toString());
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
