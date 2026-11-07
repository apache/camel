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
package org.apache.camel.component.sjms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping and starting a consumer (as route policies and JMX do) while the asynchronous stop or start of its listener
 * container is still pending must leave a started consumer consuming, and a stopped consumer not consuming.
 * <p/>
 * The tasks the consumers submit to the component's async start/stop thread pool are held, and the test runs them
 * itself in a chosen order (or on a thread of their own, with the stop of the listener container held), so the
 * interleaving is deterministic.
 */
@Timeout(120)
public class SjmsAsyncStartStopListenerRestartTest extends JmsTestSupport {

    private static final String QUEUE = "SjmsAsyncStartStopListenerRestartTest.";
    private static final String STOP = "?asyncStopListener=true";
    private static final String START = "?asyncStartListener=true";
    private static final String BOTH = "?asyncStartListener=true&asyncStopListener=true";

    private final HeldExecutorService held = new HeldExecutorService();
    // run (once) when a listener container is configured, just before it is started
    private volatile Runnable configureHook;
    // when set, the next stop of a listener container counts down stopEntered and then waits for it
    private volatile CountDownLatch stopGate;
    private final CountDownLatch stopEntered = new CountDownLatch(1);

    public SjmsAsyncStartStopListenerRestartTest() {
        addSjmsComponent = false;
    }

    @Test
    public void testRestartWhileAsyncStopIsPending() throws Exception {
        String id = "restartStopPending";
        context.getRouteController().startRoute(id);
        assertConsumes(id, "Hello World");

        consumer(id).stop();
        consumer(id).start();
        assertEquals(1, held.pending(), "the asynchronous stop of the listener container is pending");

        // the stop task of the first run runs after the consumer was started again
        held.runPending();

        assertConsumes(id, "Bye World");
        assertListening(id);
    }

    @Test
    public void testRestartWhileAsyncStopAndStartArePendingStartFirst() throws Exception {
        String id = "restartBothPendingStartFirst";
        startRouteWithAsyncStart(id);

        consumer(id).stop();
        consumer(id).start();
        assertEquals(2, held.pending(), "the stop task and the start task are pending");

        held.runPendingInReverse();

        assertConsumes(id, "Bye World");
        assertListening(id);
    }

    @Test
    public void testRestartWhileAsyncStopAndStartArePendingStopFirst() throws Exception {
        // control: on main the start task starts the container again after the stop task shut it down
        String id = "restartBothPendingStopFirst";
        startRouteWithAsyncStart(id);

        consumer(id).stop();
        consumer(id).start();
        assertEquals(2, held.pending(), "the stop task and the start task are pending");

        held.runPending();

        assertConsumes(id, "Bye World");
        assertListening(id);
    }

    @Test
    public void testStopWhileAsyncStartIsPending() throws Exception {
        String id = "stopStartPending";
        startRouteWithAsyncStart(id);

        consumer(id).stop();
        consumer(id).start();
        consumer(id).stop();
        assertEquals(1, held.pending(), "the start task of the second start is pending");

        // the start task runs after the consumer was stopped
        held.runPending();

        assertTrue(consumer(id).isStopped(), "the consumer was stopped");
        assertFalse(container(id).isStarted(), "the stopped consumer must not keep its listener container started");
    }

    @Test
    public void testStopWhileAsyncStartIsStartingTheListenerContainer() throws Exception {
        String id = "stopStartRunning";
        startRouteWithAsyncStart(id);

        consumer(id).stop();
        consumer(id).start();
        // the consumer is stopped after the start task checked that the consumer runs, and before it starts the
        // listener container
        configureHook = () -> consumer(id).stop();
        held.runPending();

        assertTrue(consumer(id).isStopped(), "the consumer was stopped");
        assertFalse(container(id).isStarted(), "the stopped consumer must not keep its listener container started");
    }

    @Test
    public void testStartWhileAsyncStopIsStoppingTheListenerContainer() throws Exception {
        String id = "startStopRunning";
        context.getRouteController().startRoute(id);
        assertConsumes(id, "Hello World");

        CountDownLatch gate = new CountDownLatch(1);
        stopGate = gate;
        held.hold = false;
        held.ownThread = true;
        Thread starter = new Thread(() -> consumer(id).start(), "starter");
        try {
            consumer(id).stop();
            assertTrue(stopEntered.await(20, TimeUnit.SECONDS), "the stop task stopped the listener container");

            // the consumer is started again after the stop task stopped the listener container and before it shuts
            // it down
            starter.start();
            // the start either waits for the stop task, or is done
            await().atMost(20, TimeUnit.SECONDS).until(
                    () -> consumer(id).isStarting() && starter.getState() == Thread.State.WAITING
                            || starter.getState() == Thread.State.TERMINATED);
        } finally {
            // the stop task took the gate, so the cleanup cannot open it: a failed wait must not leave the starter and
            // the stop task blocking each other
            gate.countDown();
        }
        starter.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(starter.isAlive(), "the start of the consumer completed");

        assertConsumes(id, "Bye World");
        assertListening(id);
    }

    @Test
    public void testRouteStopDoesNotWaitForAsyncStop() throws Exception {
        // guards that the stop task does not hold the lock of the consumer (passes on main too)
        String id = "routeStopStopRunning";
        context.getRouteController().startRoute(id);
        assertConsumes(id, "Hello World");

        CountDownLatch gate = new CountDownLatch(1);
        stopGate = gate;
        held.hold = false;
        held.ownThread = true;
        try {
            CompletableFuture<Void> stopRoute = CompletableFuture.runAsync(() -> {
                try {
                    context.getRouteController().stopRoute(id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertTrue(stopEntered.await(20, TimeUnit.SECONDS), "the stop task is stopping the listener container");

            // asyncStopListener=true: the route stop does not wait for the listener container to stop
            stopRoute.get(20, TimeUnit.SECONDS);
            assertTrue(consumer(id).isStopped(), "the consumer was stopped");
        } finally {
            gate.countDown();
        }
        // the stop task shuts the listener container down once the gate is open
        await().atMost(20, TimeUnit.SECONDS).until(() -> ((ServiceSupport) container(id)).isShutdown());
    }

    @Test
    public void testRestartAfterAsyncTasksRanInOrder() throws Exception {
        // control: the tasks run before the next lifecycle change
        String id = "restartInOrder";
        startRouteWithAsyncStart(id);

        consumer(id).stop();
        held.runPending();
        assertFalse(container(id).isStarted(), "the stopped consumer stopped its listener container");
        consumer(id).start();
        held.runPending();

        assertConsumes(id, "Bye World");
        assertListening(id);
    }

    private void startRouteWithAsyncStart(String routeId) throws Exception {
        context.getRouteController().startRoute(routeId);
        held.runPending();
        assertConsumes(routeId, "Hello World");
    }

    private SjmsConsumer consumer(String routeId) {
        return (SjmsConsumer) context.getRoute(routeId).getConsumer();
    }

    private StatefulService container(String routeId) {
        return (StatefulService) consumer(routeId).getListenerContainer();
    }

    private void assertListening(String routeId) {
        assertTrue(consumer(routeId).isStarted(), "the consumer was started");
        assertTrue(container(routeId).isStarted(), "the started consumer must have a started listener container");
    }

    private void assertConsumes(String routeId, String body) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedBodiesReceived(body);
        template.sendBody("sjms:queue:" + QUEUE + routeId, body);
        result.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        SjmsComponent component = new SjmsComponent() {
            @Override
            protected ExecutorService getAsyncStartStopExecutorService() {
                return held;
            }

            @Override
            protected SjmsEndpoint createSjmsEndpoint(String uri, String remaining) {
                return new SjmsEndpoint(uri, this, remaining) {
                    @Override
                    public MessageListenerContainer createMessageListenerContainer(SjmsEndpoint endpoint) {
                        return new TestListenerContainer(endpoint);
                    }
                };
            }
        };
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                route("restartStopPending", STOP);
                route("restartBothPendingStartFirst", BOTH);
                route("restartBothPendingStopFirst", BOTH);
                route("stopStartPending", START);
                route("stopStartRunning", START);
                route("startStopRunning", STOP);
                route("routeStopStopRunning", STOP);
                route("restartInOrder", BOTH);
            }

            private void route(String routeId, String options) {
                from("sjms:queue:" + QUEUE + routeId + options).routeId(routeId).autoStartup(false).to("mock:result");
            }
        };
    }

    @Override
    protected void cleanupResources() throws Exception {
        // release and run what a failed test left
        CountDownLatch gate = stopGate;
        if (gate != null) {
            gate.countDown();
        }
        held.hold = false;
        held.runPending();
        super.cleanupResources();
    }

    private final class TestListenerContainer extends SimpleMessageListenerContainer {

        TestListenerContainer(SjmsEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void afterPropertiesConfigured(CamelContext camelContext) {
            Runnable hook = configureHook;
            configureHook = null;
            if (hook != null) {
                hook.run();
            }
            super.afterPropertiesConfigured(camelContext);
        }

        @Override
        public void stop() {
            super.stop();
            // after the stop, before the shutdown of an asynchronous stop task
            CountDownLatch gate = stopGate;
            stopGate = null;
            if (gate != null) {
                stopEntered.countDown();
                try {
                    if (!gate.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("the stop gate was not opened");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /**
     * An executor that keeps the submitted tasks until the test runs them (in submission order or in reverse order), or
     * runs them inline or on a thread of their own.
     */
    static final class HeldExecutorService extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        volatile boolean hold = true;
        volatile boolean ownThread;

        @Override
        public void execute(Runnable command) {
            if (hold) {
                synchronized (tasks) {
                    tasks.add(command);
                }
            } else if (ownThread) {
                new Thread(command, "AsyncStartStopListenerTask").start();
            } else {
                command.run();
            }
        }

        int pending() {
            synchronized (tasks) {
                return tasks.size();
            }
        }

        void runPending() {
            runPending(false);
        }

        void runPendingInReverse() {
            runPending(true);
        }

        private void runPending(boolean reverse) {
            List<Runnable> copy;
            synchronized (tasks) {
                copy = new ArrayList<>(tasks);
                tasks.clear();
            }
            if (reverse) {
                Collections.reverse(copy);
            }
            copy.forEach(Runnable::run);
        }

        @Override
        public void shutdown() {
            // noop
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
