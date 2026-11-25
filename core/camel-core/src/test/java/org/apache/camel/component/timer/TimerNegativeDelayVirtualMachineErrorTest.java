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
package org.apache.camel.component.timer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VirtualMachineError (StackOverflowError) thrown in a timer route with a negative delay (executor path) fails the
 * exchange, ends the fire loop of the route, and the consumer logs at ERROR which route to restart (CAMEL-27515).
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-27470">CAMEL-27470</a>
 */
class TimerNegativeDelayVirtualMachineErrorTest extends ContextTestSupport {

    private static final String ROUTE_ID = "route-neg-delay";

    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AtomicInteger fired = new AtomicInteger();
    private AbstractAppender appender;

    @BeforeEach
    void captureErrors() {
        appender = new AbstractAppender("timer-errors-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                // other tests run in parallel: keep only the line of this route that says that its consumer stopped
                if (event.getLoggerName().equals(DefaultConsumer.class.getName()) && message.contains(ROUTE_ID)) {
                    errors.add(message);
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.ERROR, null);
        logging.updateLoggers();
    }

    @AfterEach
    void stopCapture() {
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    void errorEndsTheLoopAndIsLogged() throws Exception {
        // started here, once the appender is in place
        context.getRouteController().startRoute(ROUTE_ID);

        // the exchange fails with the Error (CAMEL-27515), and the consumer stops: it logs which route to restart
        await().atMost(10, TimeUnit.SECONDS).until(() -> !errors.isEmpty());
        String error = errors.get(0);
        assertTrue(error.contains("of route " + ROUTE_ID), error);
        assertTrue(error.contains("until the route is restarted"), error);
        TimerConsumer consumer = (TimerConsumer) context.getRoute(ROUTE_ID).getConsumer();
        assertInstanceOf(StackOverflowError.class, consumer.getFatalError());

        // the loop has ended: the route does not fire again
        await().during(Duration.ofMillis(500)).atMost(2, TimeUnit.SECONDS).until(() -> fired.get() == 1);
        assertEquals(1, errors.size());
        // the failed exchange completed: nothing stays inflight
        assertEquals(0, context.getInflightRepository().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:negDelay?delay=-1&synchronous=true")
                        .routeId(ROUTE_ID).autoStartup(false)
                        .process(exchange -> {
                            fired.incrementAndGet();
                            throw new StackOverflowError("simulated StackOverflowError in negative-delay route");
                        });
            }
        };
    }
}
