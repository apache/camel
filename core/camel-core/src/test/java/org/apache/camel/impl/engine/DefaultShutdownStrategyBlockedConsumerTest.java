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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Suspendable;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer whose suspend or stop blocks (such as a client call that waits for a reply on a connection that stopped
 * answering) and ignores the interrupt. The graceful shutdown suspends or stops it on the ShutdownTask thread. The stop
 * stays synchronous and waits for that call, but when the shutdown timeout elapses a WARN names the consumer, its
 * route, and where the call waits.
 * <p>
 * Isolated: the test adds an appender to the logger of the shutdown strategy, whose configuration other tests (such as
 * ShutdownWaitingAtTest) replace and remove while they run.
 */
@Isolated
class DefaultShutdownStrategyBlockedConsumerTest extends ContextTestSupport {

    private static final String LOGGER = DefaultShutdownStrategy.class.getName();
    private static final String NOT_RETURNED = "has not returned within the timeout";

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private volatile String blockIn = "";
    private AbstractAppender appender;

    @BeforeEach
    void addAppender() {
        appender = new AbstractAppender("BlockedConsumerTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.INFO, true);
        loggerConfig.addAppender(appender, null, null);
        lc.getConfiguration().addLogger(LOGGER, loggerConfig);
        lc.updateLoggers();
    }

    @AfterEach
    void releaseConsumerAndRemoveAppender() {
        release.countDown();
        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
        lc.getConfiguration().removeLogger(LOGGER);
        lc.updateLoggers();
        appender.stop();
    }

    @Override
    protected int getShutdownTimeout() {
        return 1;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testContextStopWhenSuspendBlocks() throws Exception {
        assertStopWaitsAndWarns("blockedSuspend", "suspend", true, "$BlockingSuspendableConsumer.doSuspend(",
                () -> context.stop());
        assertTrue(context.isStopped());
    }

    @Test
    void testContextStopWhenStopOfSuspendableConsumerBlocks() throws Exception {
        // the consumer is suspended first, and its stop (deferred to the end of the graceful shutdown) blocks
        assertStopWaitsAndWarns("blockedDeferredStop", "stop", true, "$BlockingConsumer.doStop(", () -> context.stop());
        assertTrue(context.isStopped());
    }

    @Test
    void testContextStopWhenStopBlocks() throws Exception {
        assertStopWaitsAndWarns("blockedStop", "stop", false, "$BlockingConsumer.doStop(", () -> context.stop());
        assertTrue(context.isStopped());
    }

    @Test
    void testRouteStopWhenSuspendBlocks() throws Exception {
        assertStopWaitsAndWarns("blockedRouteSuspend", "suspend", true, "$BlockingSuspendableConsumer.doSuspend(",
                () -> context.getRouteController().stopRoute("blockedRouteSuspend", 1, TimeUnit.SECONDS));
        assertTrue(context.getRouteController().getRouteStatus("blockedRouteSuspend").isStopped());
    }

    @Test
    void testRouteStopWithoutShutdownNowOnTimeoutWhenSuspendBlocks() throws Exception {
        context.getShutdownStrategy().setShutdownNowOnTimeout(false);
        assertStopWaitsAndWarns("blockedRouteIgnore", "suspend", true, "$BlockingSuspendableConsumer.doSuspend(",
                () -> context.getRouteController().stopRoute("blockedRouteIgnore", 1, TimeUnit.SECONDS));
        assertTrue(context.getRouteController().getRouteStatus("blockedRouteIgnore").isStopped());
    }

    @Test
    void testNoWarningWhenTheTimeoutWaitsForAnInflightExchange() throws Exception {
        CountDownLatch inflight = new CountDownLatch(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("inflight").process(exchange -> {
                    inflight.countDown();
                    release.await();
                });
            }
        });
        context.start();
        // a thread of the test (not a thread pool of Camel, whose shutdown would wait for it); send, not sendBody, as the
        // exchange fails once released (its route was forced to shut down)
        Thread sender = new Thread(() -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "sender");
        sender.setDaemon(true);
        sender.start();
        assertTrue(inflight.await(10, TimeUnit.SECONDS));

        // the timeout elapses while the graceful shutdown waits for the exchange, not inside a consumer's suspend or stop
        context.stop();

        assertTrue(context.isStopped());
        assertTrue(warningsOf("inflight").isEmpty(), () -> String.valueOf(warningsOf("inflight")));
    }

    private void assertStopWaitsAndWarns(
            String routeId, String blockIn, boolean suspendable, String frame, ThrowingRunnable stop)
            throws Exception {
        this.blockIn = blockIn;
        context.addComponent("blocking", new BlockingComponent(suspendable));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("blocking:x").routeId(routeId).to("mock:result");
            }
        });
        context.start();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread stopper = new Thread(() -> {
            try {
                stop.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "stopper");
        stopper.setDaemon(true);
        stopper.start();
        assertTrue(blocked.await(10, TimeUnit.SECONDS), "The consumer's " + blockIn + " was not called");

        // the shutdown timeout elapses while the call blocks: the WARN names the consumer and where the call waits
        await().atMost(10, TimeUnit.SECONDS).until(() -> !warningsOf(routeId).isEmpty());
        String warning = warningsOf(routeId).get(0);
        assertTrue(warning.startsWith("The " + blockIn + " of the consumer of route: " + routeId
                                      + " (consuming from: blocking://x) " + NOT_RETURNED),
                warning);
        assertTrue(warning.contains(frame), warning);
        // the stop stays synchronous, and waits for the consumer
        assertTrue(stopper.isAlive(), "The stop returned while the consumer's " + blockIn + " still blocked");

        release.countDown();
        stopper.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(stopper.isAlive(), "The stop did not return after the consumer's " + blockIn + " returned");
        assertNull(failure.get());
        assertEquals(1, warningsOf(routeId).size(), () -> String.valueOf(warningsOf(routeId)));
    }

    private List<String> warningsOf(String routeId) {
        return warnings.stream().filter(w -> w.contains(NOT_RETURNED) && w.contains("route: " + routeId + " ")).toList();
    }

    private void block() {
        blocked.countDown();
        // like a client call that waits for a reply on a silent connection and does not react to the interrupt
        boolean interrupted = false;
        while (true) {
            try {
                release.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final class BlockingComponent extends DefaultComponent {

        private final boolean suspendable;

        BlockingComponent(boolean suspendable) {
            this.suspendable = suspendable;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new BlockingEndpoint(uri, this);
        }
    }

    private final class BlockingEndpoint extends DefaultEndpoint {

        BlockingEndpoint(String uri, BlockingComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return ((BlockingComponent) getComponent()).suspendable
                    ? new BlockingSuspendableConsumer(this, processor) : new BlockingConsumer(this, processor);
        }
    }

    private class BlockingConsumer extends DefaultConsumer {

        BlockingConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStop() throws Exception {
            if ("stop".equals(blockIn)) {
                block();
            }
            super.doStop();
        }
    }

    private final class BlockingSuspendableConsumer extends BlockingConsumer implements Suspendable {

        BlockingSuspendableConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doSuspend() throws Exception {
            if ("suspend".equals(blockIn)) {
                block();
            }
            super.doSuspend();
        }
    }
}
