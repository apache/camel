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
package org.apache.camel.component.disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A thread that is interrupted while it waits for the event handlers of a disruptor to start or to stop must not wait
 * again in a loop (each wait throws at once while the flag is set), which never ends and logs a line per iteration: it
 * keeps its interrupt flag and logs once. The wait for the start goes on until the event handlers run, as the disruptor
 * must not be used before; the wait for the stop ends.
 */
class DisruptorInterruptedWaitTest extends CamelTestSupport {

    private static final String URI = "disruptor:foo";

    private final List<Runnable> heldTasks = new ArrayList<>();
    private volatile boolean holding;
    private final AtomicInteger interruptedLogLines = new AtomicInteger();
    private AbstractAppender appender;
    private Thread waiting;

    @BeforeEach
    void captureLog() {
        // counts the log lines of DisruptorReference, which do not go to the log file meanwhile: if the wait loops, it
        // logs a line per iteration
        appender = new AbstractAppender("interrupted-wait", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().startsWith("Interrupted while waiting")) {
                    interruptedLogLines.incrementAndGet();
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(DisruptorReference.class);
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    @AfterEach
    void releaseAndRestoreLog() {
        // event handlers that were held start now, which also ends a wait for them that loops
        releaseHeldTasks();
        if (waiting != null) {
            await().atMost(20, TimeUnit.SECONDS).until(() -> !waiting.isAlive());
        }
        Logger logger = (Logger) LogManager.getLogger(DisruptorReference.class);
        logger.removeAppender(appender);
        logger.setAdditive(true);
        appender.stop();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getExecutorServiceManager().setThreadPoolFactory(new HoldingThreadPoolFactory());
        return camelContext;
    }

    @Test
    void interruptedStartWaitsForTheEventHandlersBeforeTheDisruptorIsUsed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("m0", "m1", "m2", "m3", "m4");
        DisruptorReference reference = context.getEndpoint(URI, DisruptorEndpoint.class).getDisruptor();

        // the executor keeps the event processors of the next Disruptor without running them, so its event handler
        // does not start yet; the thread that reconfigures the disruptor is interrupted, then sends 5 exchanges and
        // reconfigures the disruptor again (not interrupted). The next shutdown only waits for the event processors that
        // run: had the interrupted start returned before its event processor ran, that shutdown would halt the ring
        // buffer with the 5 exchanges on it, and they would never be processed
        holding = true;
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicBoolean interruptedAfterwards = new AtomicBoolean();
        waiting = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                reference.reconfigure();
                interruptedAfterwards.set(Thread.interrupted());
                for (int i = 0; i < 5; i++) {
                    template.sendBody(URI, "m" + i);
                }
                reference.reconfigure();
            } catch (Exception e) {
                failure.set(e);
            }
        }, "interrupted-reconfiguration");
        waiting.setDaemon(true);
        waiting.start();

        // the interrupted start waits for the event handler that does not start yet, without looping: it logs at most
        // once for the shutdown of the old event handler and once for the start of the new one
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> heldTaskCount() > 0 && waiting.getState() == Thread.State.TIMED_WAITING);
        assertTrue(interruptedLogLines.get() <= 2, "logged " + interruptedLogLines.get() + " times");

        // once the executor runs the event processors, the start ends with the interrupt flag kept, and every exchange
        // sent afterwards is processed
        releaseHeldTasks();
        await().atMost(20, TimeUnit.SECONDS).until(() -> !waiting.isAlive());
        assertFalse(waiting.isAlive(), "the reconfiguration still waits for the event handler");
        assertNull(failure.get());
        assertTrue(interruptedAfterwards.get(), "the interrupt flag was not kept");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
    }

    @Test
    void interruptedWaitForEventHandlersThatDoNotStopEnds() {
        // event handlers that have started and do not stop (as a consumer busy past the time limit)
        LifecycleAwareExchangeEventHandler first = startedEventHandler();
        LifecycleAwareExchangeEventHandler second = startedEventHandler();
        AtomicBoolean interruptedAfterwards = new AtomicBoolean();
        waiting = new Thread(() -> {
            Thread.currentThread().interrupt();
            DisruptorReference.awaitEventHandlers(new LifecycleAwareExchangeEventHandler[] { first, second }, false);
            interruptedAfterwards.set(Thread.currentThread().isInterrupted());
        }, "interrupted-shutdown");
        waiting.setDaemon(true);
        waiting.start();

        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> !waiting.isAlive());
            assertFalse(waiting.isAlive(), "the interrupted shutdown still waits for the event handlers");
            assertTrue(interruptedAfterwards.get(), "the interrupt flag was not kept");
            assertEquals(1, interruptedLogLines.get());
        } finally {
            // a wait that loops ends only once they stop: stop them before the log appender is removed
            first.onShutdown();
            second.onShutdown();
        }
    }

    private static LifecycleAwareExchangeEventHandler startedEventHandler() {
        LifecycleAwareExchangeEventHandler eventHandler = new AbstractLifecycleAwareExchangeEventHandler() {
            @Override
            public void onEvent(ExchangeEvent event, long sequence, boolean endOfBatch) {
                // not used
            }
        };
        eventHandler.onStart();
        return eventHandler;
    }

    private int heldTaskCount() {
        synchronized (heldTasks) {
            return heldTasks.size();
        }
    }

    private void releaseHeldTasks() {
        List<Runnable> tasks;
        synchronized (heldTasks) {
            holding = false;
            tasks = new ArrayList<>(heldTasks);
            heldTasks.clear();
        }
        for (Runnable task : tasks) {
            task.run();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).to("mock:result");
            }
        };
    }

    /**
     * Creates thread pools that keep the tasks they get while {@link #holding} is set, without running them.
     */
    private final class HoldingThreadPoolFactory extends DefaultThreadPoolFactory {

        @Override
        public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory factory) {
            return new ThreadPoolExecutor(
                    profile.getPoolSize(), profile.getMaxPoolSize(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                    factory) {
                @Override
                public void execute(Runnable command) {
                    synchronized (heldTasks) {
                        if (holding) {
                            heldTasks.add(() -> super.execute(command));
                            return;
                        }
                    }
                    super.execute(command);
                }
            };
        }
    }
}
