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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StatefulService;
import org.apache.camel.Suspendable;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A route is stopped (with a short timeout) while the CamelContext is stopped (with a long timeout), in either order.
 * The timeout of the route stop must not cancel, force or cut short the graceful shutdown of the CamelContext: the
 * CamelContext must wait for the exchange that is still in flight on another route, and not stop its consumer under it.
 * Also when the route stop shuts its route down while the CamelContext stop iterates the services of that route. And a
 * route stop that times out after a forced CamelContext stop must not turn the forced shutdown off.
 */
class DefaultShutdownStrategyConcurrentShutdownTest extends ContextTestSupport {

    private final CountDownLatch releaseA = new CountDownLatch(1);
    private final CountDownLatch releaseB = new CountDownLatch(1);
    // holds the task of the CamelContext stop while it iterates the services of route a
    private final AtomicBoolean holdPendingCheck = new AtomicBoolean();
    private final CountDownLatch inPendingCheck = new CountDownLatch(1);
    private final CountDownLatch releasePendingCheck = new CountDownLatch(1);
    // holds the CamelContext stop while it prepares route a for a forced shutdown
    private final AtomicBoolean holdForcedPrepare = new AtomicBoolean();
    private final CountDownLatch inForcedPrepare = new CountDownLatch(1);
    private final CountDownLatch releaseForcedPrepare = new CountDownLatch(1);

    @AfterEach
    void releaseExchanges() {
        releaseA.countDown();
        releaseB.countDown();
        releasePendingCheck.countDown();
        releaseForcedPrepare.countDown();
    }

    @Test
    void testRouteStopTimeoutDoesNotForceContextStop() throws Exception {
        DefaultShutdownStrategy strategy = (DefaultShutdownStrategy) context.getShutdownStrategy();
        strategy.setTimeout(60);

        StatefulService consumerB = (StatefulService) context.getRoute("b").getConsumer();

        // an exchange in flight on each route
        CompletableFuture<Exchange> a = consume("a", "A");
        CompletableFuture<Exchange> b = consume("b", "B");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("a") == 1
                && context.getInflightRepository().size("b") == 1);

        // stop route a with a short timeout: its graceful shutdown waits for the exchange on route a
        AtomicReference<Throwable> stopRouteFailure = new AtomicReference<>();
        Thread stopRoute = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("a", 5, TimeUnit.SECONDS);
            } catch (Throwable e) {
                stopRouteFailure.set(e);
            }
        }, "stop-route-a");
        stopRoute.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.getCurrentShutdownTaskFuture() != null);
        Future<?> routeTask = strategy.getCurrentShutdownTaskFuture();

        // meanwhile the CamelContext is stopped (60 seconds timeout)
        AtomicReference<Throwable> stopContextFailure = new AtomicReference<>();
        Thread stopContext = new Thread(() -> {
            try {
                context.stop();
            } catch (Throwable e) {
                stopContextFailure.set(e);
            }
        }, "stop-context");
        stopContext.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.getCurrentShutdownTaskFuture() != null
                && strategy.getCurrentShutdownTaskFuture() != routeTask);
        // the CamelContext stop has started before the route stop times out
        assertTrue(stopRoute.isAlive(), "the route stop timed out before the CamelContext stop started");

        // the route stop times out (its exchange is still in flight) and forces route a
        stopRoute.join(20_000);
        assertFalse(stopRoute.isAlive(), "the route stop did not complete");
        assertNull(stopRouteFailure.get());
        releaseA.countDown();

        // the CamelContext stop waits for the exchange on route b: its consumer is suspended, not stopped under it
        await("the CamelContext stop suspends route b").atMost(20, TimeUnit.SECONDS)
                .until(() -> consumerB.getStatus() == ServiceStatus.Suspended || consumerB.isStopping()
                        || consumerB.isStopped());
        if (consumerB.isStopping() || consumerB.isStopped()) {
            fail("The consumer of route b was stopped while its exchange was in flight (status "
                 + consumerB.getStatus() + ", force shutdown " + strategy.isForceShutdown()
                 + "): the timeout of the route stop forced the graceful shutdown of the CamelContext");
        }
        assertEquals(1, context.getInflightRepository().size("b"));

        // once the exchange completes the CamelContext stops, gracefully
        releaseB.countDown();
        stopContext.join(30_000);
        assertFalse(stopContext.isAlive(), "the CamelContext stop did not complete");
        assertNull(stopContextFailure.get());
        assertTrue(context.isStopped());
        Exchange exchangeB = b.get(10, TimeUnit.SECONDS);
        assertNull(exchangeB.getException());
        assertEquals("Done B", exchangeB.getMessage().getBody());
        assertFalse(strategy.isForceShutdown());
        assertTrue(a.isDone());
    }

    @Test
    void testRouteStopTimeoutDoesNotCutShortContextStop() throws Exception {
        DefaultShutdownStrategy strategy = (DefaultShutdownStrategy) context.getShutdownStrategy();
        strategy.setTimeout(60);
        StatefulService consumerB = (StatefulService) context.getRoute("b").getConsumer();

        // an exchange in flight on each route
        CompletableFuture<Exchange> a = consume("a", "A");
        CompletableFuture<Exchange> b = consume("b", "B");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("a") == 1
                && context.getInflightRepository().size("b") == 1);

        // the CamelContext is stopped (60 seconds timeout), and waits for the exchanges in flight
        AtomicReference<Throwable> stopContextFailure = new AtomicReference<>();
        Thread stopContext = new Thread(() -> {
            try {
                context.stop();
            } catch (Throwable e) {
                stopContextFailure.set(e);
            }
        }, "stop-context");
        stopContext.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumerB.getStatus() == ServiceStatus.Suspended);

        // meanwhile route a is stopped with a short timeout, which times out (its task waits for the one of the
        // CamelContext stop)
        AtomicReference<Throwable> stopRouteFailure = new AtomicReference<>();
        Thread stopRoute = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("a", 1, TimeUnit.SECONDS);
            } catch (Throwable e) {
                stopRouteFailure.set(e);
            }
        }, "stop-route-a");
        stopRoute.start();
        stopRoute.join(20_000);
        assertFalse(stopRoute.isAlive(), "the route stop did not complete");
        assertNull(stopRouteFailure.get());
        assertTrue(strategy.isTimeoutOccurred(), "the route stop did not time out");

        // the CamelContext stop keeps waiting for the exchange on route b (its wait loop checks every second)
        await("the CamelContext stop keeps waiting for route b").during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> consumerB.getStatus() == ServiceStatus.Suspended && stopContext.isAlive());
        assertEquals(1, context.getInflightRepository().size("b"));
        assertFalse(strategy.isForceShutdown());

        // once the exchanges complete the CamelContext stops, gracefully
        releaseA.countDown();
        releaseB.countDown();
        stopContext.join(30_000);
        assertFalse(stopContext.isAlive(), "the CamelContext stop did not complete");
        assertNull(stopContextFailure.get());
        Exchange exchangeB = b.get(10, TimeUnit.SECONDS);
        assertNull(exchangeB.getException());
        assertEquals("Done B", exchangeB.getMessage().getBody());
        assertTrue(a.isDone());
    }

    @Test
    void testRouteShutdownDuringContextStopDoesNotFailItsWait() throws Exception {
        DefaultShutdownStrategy strategy = (DefaultShutdownStrategy) context.getShutdownStrategy();
        strategy.setTimeout(60);
        StatefulService consumerB = (StatefulService) context.getRoute("b").getConsumer();
        CompletableFuture<Exchange> b = consume("b", "B");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("b") == 1);

        // the task of the CamelContext stop is held while it iterates the services of route a (to count their pending
        // exchanges)
        holdPendingCheck.set(true);
        AtomicReference<Throwable> stopContextFailure = new AtomicReference<>();
        Thread stopContext = new Thread(() -> {
            try {
                context.stop();
            } catch (Throwable e) {
                stopContextFailure.set(e);
            }
        }, "stop-context");
        stopContext.start();
        assertTrue(inPendingCheck.await(10, TimeUnit.SECONDS), "the CamelContext stop did not check route a");

        // meanwhile route a is stopped: its task waits behind the one of the CamelContext stop, so it times out, and
        // as the CamelContext is stopping it shuts route a down, which clears the services of the route
        AtomicReference<Throwable> stopRouteFailure = new AtomicReference<>();
        Thread stopRoute = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("a", 1, TimeUnit.SECONDS);
            } catch (Throwable e) {
                stopRouteFailure.set(e);
            }
        }, "stop-route-a");
        stopRoute.start();
        stopRoute.join(20_000);
        assertFalse(stopRoute.isAlive(), "the route stop did not complete");
        assertNull(stopRouteFailure.get());
        assertTrue(context.getRoute("a").getServices().isEmpty(), "the route stop did not shut down route a");
        assertFalse(strategy.isForceShutdown());

        // the task of the CamelContext stop goes on iterating, and keeps waiting for the exchange on route b
        releasePendingCheck.countDown();
        await("the CamelContext stop keeps waiting for route b").during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> consumerB.getStatus() == ServiceStatus.Suspended && stopContext.isAlive());
        assertEquals(1, context.getInflightRepository().size("b"));

        releaseB.countDown();
        stopContext.join(30_000);
        assertFalse(stopContext.isAlive(), "the CamelContext stop did not complete");
        assertNull(stopContextFailure.get());
        Exchange exchangeB = b.get(10, TimeUnit.SECONDS);
        assertNull(exchangeB.getException());
        assertEquals("Done B", exchangeB.getMessage().getBody());
    }

    @Test
    void testRouteStopTimeoutKeepsForcedContextStop() throws Exception {
        DefaultShutdownStrategy strategy = (DefaultShutdownStrategy) context.getShutdownStrategy();
        // the CamelContext stop times out (2 seconds) before the route stop (10 seconds)
        strategy.setTimeout(2);
        holdForcedPrepare.set(true);

        CompletableFuture<Exchange> a = consume("a", "A");
        CompletableFuture<Exchange> b = consume("b", "B");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("a") == 1
                && context.getInflightRepository().size("b") == 1);

        AtomicReference<Throwable> stopRouteFailure = new AtomicReference<>();
        Thread stopRoute = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("a", 10, TimeUnit.SECONDS);
            } catch (Throwable e) {
                stopRouteFailure.set(e);
            }
        }, "stop-route-a");
        stopRoute.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.getCurrentShutdownTaskFuture() != null);

        AtomicReference<Throwable> stopContextFailure = new AtomicReference<>();
        Thread stopContext = new Thread(() -> {
            try {
                context.stop();
            } catch (Throwable e) {
                stopContextFailure.set(e);
            }
        }, "stop-context");
        stopContext.start();

        // the CamelContext stop times out and forces the routes to shut down; it is held while it prepares route a
        assertTrue(inForcedPrepare.await(20, TimeUnit.SECONDS), "the CamelContext stop did not force route a");
        assertTrue(strategy.isForceShutdown());
        assertTrue(stopRoute.isAlive(), "the route stop timed out before the CamelContext stop");

        // then the route stop times out, which must not turn the forced shutdown off again
        stopRoute.join(20_000);
        assertFalse(stopRoute.isAlive(), "the route stop did not complete");
        assertNull(stopRouteFailure.get());
        assertTrue(strategy.isForceShutdown(), "the route stop turned off the forced shutdown of the CamelContext");

        releaseForcedPrepare.countDown();
        releaseA.countDown();
        releaseB.countDown();
        stopContext.join(30_000);
        assertFalse(stopContext.isAlive(), "the CamelContext stop did not complete");
        assertNull(stopContextFailure.get());
        assertNotNull(a.get(10, TimeUnit.SECONDS));
        assertNotNull(b.get(10, TimeUnit.SECONDS));
    }

    /**
     * The consumer of the route receives a message (in another thread).
     */
    private CompletableFuture<Exchange> consume(String routeId, String body) {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute(routeId).getConsumer();
        Exchange exchange = consumer.createExchange(false);
        exchange.getMessage().setBody(body);
        CompletableFuture<Exchange> answer = new CompletableFuture<>();
        Thread t = new Thread(
                () -> consumer.getAsyncProcessor().process(exchange, doneSync -> answer.complete(exchange)),
                "consumer-" + routeId);
        t.start();
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a consumer that is suspended (not stopped) during a graceful shutdown
                context.addComponent("suspendable", new DefaultComponent() {
                    @Override
                    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                        return new DefaultEndpoint(uri, this) {
                            @Override
                            public Producer createProducer() {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public Consumer createConsumer(Processor processor) {
                                return new SuspendableConsumer(this, processor);
                            }
                        };
                    }
                });

                from("suspendable:a").routeId("a")
                        .process(new PendingCheck())
                        .process(e -> assertTrue(releaseA.await(60, TimeUnit.SECONDS)))
                        .setBody(simple("Done ${body}"));
                from("suspendable:b").routeId("b")
                        .process(e -> assertTrue(releaseB.await(60, TimeUnit.SECONDS)))
                        .setBody(simple("Done ${body}"));
            }
        };
    }

    /**
     * A service of route a: it can hold the task of the CamelContext stop while that counts the pending exchanges of
     * route a, or the CamelContext stop while that prepares route a for a forced shutdown.
     */
    private final class PendingCheck extends ServiceSupport implements Processor, ShutdownAware {
        @Override
        public void process(Exchange exchange) {
            // noop
        }

        @Override
        public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
            return false;
        }

        @Override
        public int getPendingExchangesSize() {
            if (Thread.currentThread().getName().contains("ShutdownTask") && holdPendingCheck.compareAndSet(true, false)) {
                inPendingCheck.countDown();
                try {
                    assertTrue(releasePendingCheck.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return 0;
        }

        @Override
        public void prepareShutdown(boolean suspendOnly, boolean forced) {
            if (forced && "stop-context".equals(Thread.currentThread().getName())
                    && holdForcedPrepare.compareAndSet(true, false)) {
                inForcedPrepare.countDown();
                try {
                    assertTrue(releaseForcedPrepare.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static final class SuspendableConsumer extends DefaultConsumer implements Suspendable {
        SuspendableConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }
    }
}
