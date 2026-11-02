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
package org.apache.camel.processor.throttle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half open check of {@link ThrottlingExceptionRoutePolicy} runs on one thread of a scheduler that the policy
 * creates through the ExecutorServiceManager, and that thread ends when the last route of the policy is removed. An
 * exception of the check, or of suspending or resuming the consumer, goes to the exception handler of the policy.
 */
class ThrottlingExceptionRoutePolicyHalfOpenSchedulerTest extends ContextTestSupport {

    private final LatchPolicy policy = new LatchPolicy(100, 2);
    private final LatchPolicy removePolicy = new LatchPolicy(60000, 1);
    private final LatchPolicy sharedPolicy = new LatchPolicy(3000, 1);
    private final FailingSuspendPolicy suspendPolicy = new FailingSuspendPolicy();
    private final LatchPolicy handlerPolicy = new LatchPolicy(100, 1);
    private final List<Throwable> handled = new CopyOnWriteArrayList<>();
    private final AtomicInteger handlerChecks = new AtomicInteger();

    @Test
    void testHalfOpenChecksRunOnOneThreadOfThePolicy() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("checked").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:checked", "fail"));
        assertEquals("opened", policy.getStateAsString());
        await().atMost(10, TimeUnit.SECONDS).until(() -> policy.checkThreads.size() == 1);
        assertEquals("half opened", policy.getStateAsString());
        assertFalse(consumer.isSuspended());

        // a failure in the half open state opens the circuit again, and the next check runs on the same thread
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:checked", "fail"));
        assertTrue(policy.halfOpened.await(10, TimeUnit.SECONDS));
        assertEquals("half opened", policy.getStateAsString());

        Thread thread = policy.checkThreads.get(0);
        assertSame(thread, policy.checkThreads.get(1));
        assertTrue(isSchedulerThread(thread), thread.getName());
    }

    @Test
    void testRemovingTheRouteEndsTheThreadOfTheHalfOpenCheck() throws Exception {
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:removed", "fail"));
        assertEquals("opened", removePolicy.getStateAsString());
        assertEquals(1, schedulerThreads().size(), "the half open check is scheduled");

        context.getRouteController().stopRoute("removed");
        assertTrue(context.removeRoute("removed"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> schedulerThreads().isEmpty());
        assertEquals(1, removePolicy.halfOpened.getCount(), "the check scheduled before the removal must not run");
    }

    @Test
    void testRemovingOneOfTwoRoutesOfAPolicyKeepsItsHalfOpenCheck() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("shared2").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:shared2", "fail"));
        assertEquals("opened", sharedPolicy.getStateAsString());
        assertTrue(consumer.isSuspended());

        context.getRouteController().stopRoute("shared1");
        assertTrue(context.removeRoute("shared1"));
        assertEquals(1, sharedPolicy.halfOpened.getCount(), "the check is still pending when the other route is removed");

        // the route that is left still gets its half open check
        assertTrue(sharedPolicy.halfOpened.await(10, TimeUnit.SECONDS));
        assertFalse(consumer.isSuspended());

        context.getRouteController().stopRoute("shared2");
        assertTrue(context.removeRoute("shared2"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> schedulerThreads().isEmpty());
    }

    @Test
    void testRestartedContextKeepsTheHalfOpenCheckOfTheRouteThatIsLeft() throws Exception {
        // a CamelContext that is stopped calls onRemove of the policy for each route, and when it is started again it
        // creates the routes again (onInit and onStart)
        context.stop();
        context.start();
        ServiceSupport consumer = (ServiceSupport) context.getRoute("shared2").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:shared2", "fail"));
        assertEquals("opened", sharedPolicy.getStateAsString());
        assertTrue(consumer.isSuspended());

        context.getRouteController().stopRoute("shared1");
        assertTrue(context.removeRoute("shared1"));
        assertEquals(1, sharedPolicy.halfOpened.getCount(), "the check is still pending when the other route is removed");

        // the route that is left still gets its half open check
        assertTrue(sharedPolicy.halfOpened.await(10, TimeUnit.SECONDS));
        assertFalse(consumer.isSuspended());
    }

    @Test
    void testFailureToSuspendTheConsumerIsLogged() throws Exception {
        // the policy logs with the LoggingExceptionHandler that RoutePolicySupport.onInit creates
        String loggerName = FailingSuspendPolicy.class.getName();
        List<Throwable> logged = new CopyOnWriteArrayList<>();
        Appender appender = ConsumingAppender.newAppender(loggerName, "suspend-failure", Level.WARN, e -> {
            if (e.getThrown() != null) {
                logged.add(e.getThrown());
            }
        });
        try {
            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:suspendFails", "fail"));

            assertEquals(1, logged.size(), "the failure to suspend the consumer is logged");
            assertEquals("Forced suspend failure", logged.get(0).getMessage());
        } finally {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().removeLogger(loggerName);
            ctx.updateLoggers();
            appender.stop();
        }
    }

    @Test
    void testExceptionOfTheHalfOpenHandlerIsHandledAndTheCheckIsRetried() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("handler").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:handler", "fail"));
        assertEquals("opened", handlerPolicy.getStateAsString());

        // the first check fails, and keeps the circuit open; the next one, after halfOpenAfter, closes it
        await().atMost(10, TimeUnit.SECONDS).until(() -> "closed".equals(handlerPolicy.getStateAsString()));
        assertEquals(2, handlerChecks.get());
        assertEquals(1, handled.size(), "the exception of the half open handler is handled");
        assertEquals("Forced check failure", handled.get(0).getMessage());
        assertFalse(consumer.isSuspended());
    }

    @Test
    void testHalfOpenAfterMustBeGreaterThanZero() throws Exception {
        ThrottlingExceptionRoutePolicy configured = new ThrottlingExceptionRoutePolicy();
        assertThrows(IllegalArgumentException.class, () -> configured.setHalfOpenAfter(0));
        assertThrows(IllegalArgumentException.class, () -> configured.setHalfOpenAfter(-1));
        assertEquals(30000, configured.getHalfOpenAfter());

        // set through the constructor: the route that uses the policy is not created
        ThrottlingExceptionRoutePolicy constructed = new ThrottlingExceptionRoutePolicy(1, 60000, 0, List.of());
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:zero").routeId("zero").routePolicy(constructed).to("mock:zero");
            }
        }));
        Throwable cause = e;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertEquals("halfOpenAfter must be greater than 0, was: 0", cause != null ? cause.getMessage() : null, e.toString());
    }

    private boolean isSchedulerThread(Thread thread) {
        // the thread name pattern of the ExecutorServiceManager, with the name of this CamelContext
        String name = thread.getName();
        return name.startsWith("Camel (" + context.getName() + ") thread #")
                && name.endsWith(" - ThrottlingExceptionRoutePolicy");
    }

    private List<Thread> schedulerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).filter(this::isSchedulerThread)
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:checked").routeId("checked").routePolicy(policy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:removed").routeId("removed").routePolicy(removePolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:shared1").routeId("shared1").routePolicy(sharedPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));
                from("direct:shared2").routeId("shared2").routePolicy(sharedPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:suspendFails").routeId("suspendFails").routePolicy(suspendPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                handlerPolicy.setExceptionHandler(new RecordingExceptionHandler());
                handlerPolicy.setHalfOpenHandler(() -> {
                    if (handlerChecks.incrementAndGet() == 1) {
                        throw new IllegalStateException("Forced check failure");
                    }
                    return true;
                });
                from("direct:handler").routeId("handler").routePolicy(handlerPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    /**
     * The policy, with the threads of its half open checks and a latch counted down when the circuit is half opened.
     */
    private static final class LatchPolicy extends ThrottlingExceptionRoutePolicy {
        private final List<Thread> checkThreads = new CopyOnWriteArrayList<>();
        private final CountDownLatch halfOpened;

        LatchPolicy(long halfOpenAfter, int halfOpenings) {
            super(1, 60000, halfOpenAfter, List.of());
            this.halfOpened = new CountDownLatch(halfOpenings);
        }

        @Override
        protected void halfOpenCircuit(Route route) {
            super.halfOpenCircuit(route);
            checkThreads.add(Thread.currentThread());
            halfOpened.countDown();
        }
    }

    /**
     * The policy, failing to suspend the consumer.
     */
    private static final class FailingSuspendPolicy extends ThrottlingExceptionRoutePolicy {

        FailingSuspendPolicy() {
            super(1, 60000, 60000, List.of());
        }

        @Override
        public boolean suspendOrStopConsumer(Consumer consumer) {
            throw new IllegalStateException("Forced suspend failure");
        }
    }

    private final class RecordingExceptionHandler implements ExceptionHandler {

        @Override
        public void handleException(Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handled.add(exception);
        }
    }
}
