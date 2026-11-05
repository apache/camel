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
package org.apache.camel.component.jms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping and starting a JMS consumer (as route policies and JMX do) while the asynchronous stop or start of its
 * listener container is still pending must leave the consumer consuming.
 * <p/>
 * The tasks the consumer submits to the component's async start/stop thread pool are held, and the test runs them
 * itself in a chosen order, so the interleaving is deterministic.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Timeout(60)
public class JmsAsyncStopListenerRestartTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final String QUEUE = "JmsAsyncStopListenerRestartTest";

    // static: the CamelContext (and so the component) is created once for the class
    private static final HeldExecutorService HELD = new HeldExecutorService();
    private volatile Runnable initializeHook;
    private volatile AbstractMessageListenerContainer initializing;
    protected CamelContext context;
    protected ProducerTemplate template;

    @Test
    public void testRestartWhileAsyncStopIsPending() throws Exception {
        context.getRouteController().startRoute("jms");
        assertConsumes("Hello World");

        consumer().stop();
        consumer().start();
        assertEquals(1, HELD.pending(), "the asynchronous stop of the old listener container is pending");

        // the stop task of the first run of the route runs after the route was started again
        HELD.runPending();

        assertListening();
        assertConsumes("Bye World");
    }

    @Test
    public void testRestartWhileAsyncStopAndStartArePendingStopFirst() throws Exception {
        startRouteWithAsyncStart();

        consumer().stop();
        consumer().start();
        assertEquals(2, HELD.pending(), "the stop task and the start task are pending");

        HELD.runPending();

        assertListening();
        assertConsumes("Bye World");
    }

    @Test
    public void testRestartWhileAsyncStopAndStartArePendingStartFirst() throws Exception {
        startRouteWithAsyncStart();

        consumer().stop();
        consumer().start();
        assertEquals(2, HELD.pending(), "the stop task and the start task are pending");

        HELD.runPendingInReverse();

        assertListening();
        assertConsumes("Bye World");
    }

    @Test
    public void testStopWhileAsyncStartIsPending() throws Exception {
        int running = endpoint().getRunningMessageListeners();
        startRouteWithAsyncStart();

        consumer().stop();
        consumer().start();
        AbstractMessageListenerContainer second = consumer().getListenerContainer();
        consumer().stop();

        // the start task of the second run of the route runs after the route was stopped
        HELD.runPendingInReverse();

        assertTrue(consumer().isStopped(), "the consumer was stopped");
        assertFalse(second.isActive() && second.isRunning(), "the stopped route must not keep a listener container running");
        assertEquals(running, endpoint().getRunningMessageListeners(),
                "every started listener container is counted as stopped");
    }

    @Test
    public void testStopWhileAsyncStartIsInitializingTheListenerContainer() throws Exception {
        // the listener containers run the hook (if any) when they are initialized
        endpoint().getConfiguration().setMessageListenerContainerFactory(e -> new DefaultJmsMessageListenerContainer(e) {
            @Override
            public void afterPropertiesSet() {
                Runnable hook = initializeHook;
                initializeHook = null;
                if (hook != null) {
                    initializing = this;
                    hook.run();
                }
                super.afterPropertiesSet();
            }
        });
        endpoint().getConfiguration().setConsumerType(ConsumerType.Custom);
        startRouteWithAsyncStart();

        consumer().stop();
        consumer().start();
        // the consumer is stopped, and its stop task runs, after the start task checked that the consumer owns the
        // listener container and before the start task initializes and starts it
        initializeHook = () -> {
            consumer().stop();
            HELD.runPending();
        };
        HELD.runPending();

        assertTrue(consumer().isStopped(), "the consumer was stopped");
        assertNotNull(initializing, "the start task initialized the listener container");
        assertFalse(initializing.isActive() && initializing.isRunning(),
                "the stopped consumer must not keep a listener container running");
    }

    private void startRouteWithAsyncStart() throws Exception {
        endpoint().getConfiguration().setAsyncStartListener(true);
        context.getRouteController().startRoute("jms");
        HELD.runPending();
        assertConsumes("Hello World");
    }

    private JmsEndpoint endpoint() {
        return context.getEndpoint("activemq:queue:" + QUEUE, JmsEndpoint.class);
    }

    private JmsConsumer consumer() {
        return (JmsConsumer) context.getRoute("jms").getConsumer();
    }

    private void assertListening() throws Exception {
        assertTrue(consumer().isStarted(), "the consumer was started");
        assertTrue(consumer().getListenerContainer().isRunning(),
                "the started consumer must have a running listener container");
    }

    private void assertConsumes(String body) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedBodiesReceived(body);
        template.sendBody("activemq:queue:" + QUEUE, body);
        result.assertIsSatisfied();
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        JmsComponent jms = new JmsComponent() {
            @Override
            protected ExecutorService getAsyncStartStopExecutorService() {
                return HELD;
            }
        };
        jms.getConfiguration().setConnectionFactory(connectionFactory);
        jms.getConfiguration().setAsyncStopListener(true);
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:" + QUEUE).routeId("jms").autoStartup(false).to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        HELD.hold = true;
    }

    @AfterEach
    void stopRoute() throws Exception {
        // run what is left, and stop the route with the tasks running inline, for the next test
        HELD.hold = false;
        HELD.runPending();
        context.getRouteController().stopRoute("jms");
        endpoint().getConfiguration().setAsyncStartListener(false);
        endpoint().getConfiguration().setMessageListenerContainerFactory(null);
        endpoint().getConfiguration().setConsumerType(ConsumerType.Default);
        // drop what a failed test left in the queue, so it does not reach the next test
        while (camelContextExtension.getConsumerTemplate().receiveBody("activemq:queue:" + QUEUE, 100) != null) {
            // drained
        }
    }

    /**
     * An executor that keeps the submitted tasks until the test runs them (in submission order or in reverse order).
     */
    static final class HeldExecutorService extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        volatile boolean hold = true;

        @Override
        public void execute(Runnable command) {
            if (hold) {
                synchronized (tasks) {
                    tasks.add(command);
                }
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
