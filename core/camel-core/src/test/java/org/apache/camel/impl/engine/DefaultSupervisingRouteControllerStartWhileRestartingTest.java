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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route that fails to start manually while its previous restart attempt is running gets a new restart task, which
 * must stay registered when the previous attempt ends.
 */
class DefaultSupervisingRouteControllerStartWhileRestartingTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testStartRouteFailsWhileRestartAttemptIsRunning() throws Exception {
        CountDownLatch attemptStarted = new CountDownLatch(1);
        CountDownLatch routeStarted = new CountDownLatch(1);
        AtomicReference<BackOffTimer.Task> oldTask = new AtomicReference<>();

        context.addComponent("broken", new BrokenComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("broken:start").routeId("broken").to("mock:result");
            }
        });

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(10);
        src.setInitialDelay(10);
        src.setUnhealthyOnRestarting(true);

        context.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof RouteRestartingEvent rre && "broken".equals(rre.getRoute().getRouteId())
                        && oldTask.compareAndSet(null, src.getRestartingRouteState("broken"))) {
                    // the first restart attempt is about to start the route: let it be started manually first
                    attemptStarted.countDown();
                    assertTrue(routeStarted.await(10, TimeUnit.SECONDS));
                }
            }
        });

        context.start();
        assertTrue(attemptStarted.await(10, TimeUnit.SECONDS));

        // the route is started manually, which cancels the old restart task, and fails, so it gets a new restart task
        assertThrows(Exception.class, () -> context.getRouteController().startRoute("broken"));
        BackOffTimer.Task newTask = src.getRestartingRouteState("broken");
        assertNotSame(oldTask.get(), newTask);

        // the old restart attempt fails too, and completes the old task again
        CountDownLatch oldTaskCompleted = new CountDownLatch(1);
        oldTask.get().whenComplete((task, cause) -> oldTaskCompleted.countDown());
        routeStarted.countDown();
        assertTrue(oldTaskCompleted.await(10, TimeUnit.SECONDS));

        // the new restart task is still the one of the route (so it is reported, and a stopRoute cancels it)
        assertSame(newTask, src.getRestartingRouteState("broken"));
        assertTrue(((DefaultSupervisingRouteController) src).hasUnhealthyRoutes());
    }

    private static final class BrokenComponent extends DefaultComponent {

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
                        protected void doStart() {
                            throw new IllegalStateException("Cannot connect");
                        }
                    };
                }
            };
        }
    }
}
