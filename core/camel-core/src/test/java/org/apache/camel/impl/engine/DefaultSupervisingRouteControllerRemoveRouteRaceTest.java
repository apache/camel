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
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * removeRoute releases the route without the lock of the controller (ManagedRoutePolicy.onRemove, taking that lock
 * there could deadlock), so it can run in the middle of a restart task that is being marked exhausted, or of a manual
 * start that failed. A removed route must not be left exhausted or restarting (and the controller unhealthy).
 * <p>
 * Isolated: the first test adds an appender to the logger of the controller, which holds the thread that logs the
 * exhaustion WARN, and the supervising tests of other classes log that WARN too.
 */
@Isolated
class DefaultSupervisingRouteControllerRemoveRouteRaceTest extends ContextTestSupport {

    private static final String LOGGER = DefaultSupervisingRouteController.class.getName();

    private final CountDownLatch inWindow = new CountDownLatch(1);
    private final CountDownLatch goOn = new CountDownLatch(1);
    private final AtomicBoolean hold = new AtomicBoolean(true);
    private AbstractAppender appender;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @AfterEach
    void releaseAndRemoveAppender() {
        goOn.countDown();
        if (appender != null) {
            LoggerContext lc = (LoggerContext) LogManager.getContext(false);
            lc.getConfiguration().removeLogger(LOGGER);
            lc.updateLoggers();
            appender.stop();
        }
    }

    @Test
    void testRemoveRouteWhileItsLastRestartAttemptIsMarkedExhausted() throws Exception {
        // hold the scheduler thread in the WARN that the exhaustion logs, after its checks under the lock of the
        // controller and before it puts the route into the exhausted routes
        appender = new AbstractAppender("hold-exhausted", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN
                        && event.getMessage().getFormattedMessage().contains("is exhausted after")
                        && hold.compareAndSet(true, false)) {
                    inWindow.countDown();
                    // the release of the route cancels the running task with an interrupt: wait on
                    boolean interrupted = false;
                    while (goOn.getCount() > 0) {
                        try {
                            goOn.await(30, TimeUnit.SECONDS);
                            break;
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
        appender.start();
        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.INFO, true);
        loggerConfig.addAppender(appender, null, null);
        lc.getConfiguration().addLogger(LOGGER, loggerConfig);
        lc.updateLoggers();

        DefaultSupervisingRouteController src = createRoute(10, 2);
        context.start();

        assertTrue(inWindow.await(20, TimeUnit.SECONDS), "the restart task of route a was not exhausted");
        ReentrantLock controllerLock = (ReentrantLock) src.getLock();
        assertTrue(controllerLock.isLocked());

        // removeRoute does not wait for the lock of the controller
        AtomicReference<Object> removed = new AtomicReference<>();
        Thread remove = new Thread(() -> {
            try {
                removed.set(context.removeRoute("a"));
            } catch (Exception e) {
                removed.set(e);
            }
        }, "remove-a");
        remove.start();
        remove.join(10_000);
        assertFalse(remove.isAlive(), "removeRoute waited for the lock of the controller");
        assertEquals(Boolean.TRUE, removed.get());
        assertNull(context.getRoute("a"));

        // the exhaustion goes on
        goOn.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !controllerLock.isLocked());

        assertTrue(src.getExhaustedRoutes().isEmpty(), "the removed route a is reported as exhausted");
        assertNull(src.getRestartingRouteState("a"), "the removed route a still has a restart state");
        assertFalse(src.hasUnhealthyRoutes(), "the controller reports unhealthy routes after route a was removed");
    }

    @Test
    void testRemoveRouteWhileAManualStartFails() throws Exception {
        DefaultSupervisingRouteController src = createRoute(3600_000, 0);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                // in the manual start thread, after the failed start released the route lock of the CamelContext and
                // before the controller adds a restart task for the route
                if (event instanceof CamelEvent.RouteRestartingFailureEvent rf && rf.getAttempt() == 0
                        && "manual-start".equals(Thread.currentThread().getName()) && hold.compareAndSet(true, false)) {
                    inWindow.countDown();
                    try {
                        goOn.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> src.getRestartingRouteState("a") != null);

        Thread start = new Thread(() -> {
            try {
                context.getRouteController().startRoute("a");
            } catch (Exception e) {
                // expected: the route fails to start
            }
        }, "manual-start");
        start.start();
        assertTrue(inWindow.await(10, TimeUnit.SECONDS), "the manual start did not fail");

        assertTrue(context.removeRoute("a"));
        assertNull(context.getRoute("a"));
        goOn.countDown();
        start.join(10_000);
        assertFalse(start.isAlive());

        assertTrue(src.getRestartingRoutes().isEmpty(), "the removed route a is reported as restarting");
        assertNull(src.getRestartingRouteState("a"), "the removed route a still has a restart state");
        assertFalse(src.hasUnhealthyRoutes(), "the controller reports unhealthy routes after route a was removed");
    }

    private DefaultSupervisingRouteController createRoute(long backOffDelay, long maxAttempts) throws Exception {
        context.addComponent("flaky", new DefaultComponent() {
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
                                throw new IllegalStateException("Cannot connect");
                            }
                        };
                    }
                };
            }
        });
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(backOffDelay);
        if (maxAttempts > 0) {
            src.setBackOffMaxAttempts(maxAttempts);
        }
        src.setInitialDelay(0);
        src.setUnhealthyOnExhausted(true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:a").routeId("a").to("mock:result");
            }
        });
        return (DefaultSupervisingRouteController) src;
    }
}
