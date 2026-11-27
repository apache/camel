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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.util.function.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suspending and resuming a JMS consumer with asyncStartListener=true (as route policies, JMX and the route controller
 * do) while the asynchronous start of its listener container is pending, or after it failed, must leave a suspended
 * consumer idle and a resumed consumer with one listener container, initialized once and counted once.
 * <p/>
 * The tasks the consumer submits to the component's async start/stop thread pool are held, and the test runs them
 * itself, so the interleaving is deterministic.
 * <p/>
 * The start must also start the listener container once while the container is not allowed to run (the CamelContext is
 * stopping, or the endpoint is not started), with and without asyncStartListener.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Timeout(60)
class JmsAsyncStartListenerSuspendResumeTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final String QUEUE = "JmsAsyncStartListenerSuspendResumeTest";

    // static: the CamelContext (and so the component) is created once for the class
    private static final JmsAsyncStopListenerRestartTest.HeldExecutorService HELD
            = new JmsAsyncStopListenerRestartTest.HeldExecutorService();
    // when true every connection the consumer creates fails, as if the broker was unreachable
    private static volatile boolean brokerDown;
    private volatile ThrowingRunnable<Exception> initializeHook;
    private volatile ThrowingRunnable<Exception> beforeStartHook;
    private volatile ThrowingRunnable<Exception> afterStartHook;
    // the hooks the listener containers ran
    private final AtomicInteger hooksRun = new AtomicInteger();
    private CamelContext context;
    private ProducerTemplate template;
    private int listenersBefore;

    @Test
    void testSuspendWhileAsyncStartIsPending() throws Exception {
        startRoute();
        assertEquals(1, HELD.pending(), "the asynchronous start is pending");
        context.getRouteController().suspendRoute("jms");

        HELD.runPending();

        assertSuspendedAndIdle();
        resumeAndAssertConsumes();
    }

    @Test
    void testSuspendWhileAsyncStartIsStartingTheListenerContainer() throws Exception {
        useListenerContainerWithHooks();
        startRoute();
        // the route is suspended after the start task checked that the consumer is not suspended, and before the
        // listener container starts
        beforeStartHook = () -> context.getRouteController().suspendRoute("jms");

        HELD.runPending();

        assertEquals(1, hooksRun.get(), "the route was suspended before the listener container started");
        assertSuspendedAndIdle();
        resumeAndAssertConsumes();
    }

    @Test
    void testSuspendAndResumeWhileAsyncStartIsStartingTheListenerContainer() throws Exception {
        useListenerContainerWithHooks();
        startRoute();
        // the route is suspended and resumed after the listener container started, and before the start task checks
        // the status again: the suspend stopped the container
        afterStartHook = () -> {
            context.getRouteController().suspendRoute("jms");
            context.getRouteController().resumeRoute("jms");
        };

        HELD.runPending();

        assertEquals(1, hooksRun.get(), "the route was suspended and resumed after the listener container started");
        assertEquals(0, HELD.pending(), "the resume must not submit a second start of the same listener container");
        assertOneListenerContainerConsumes();
        assertStopReleasesTheListener();
    }

    @Test
    void testResumeWhileAsyncStartIsPending() throws Exception {
        startRoute();
        context.getRouteController().suspendRoute("jms");
        context.getRouteController().resumeRoute("jms");
        assertEquals(1, HELD.pending(), "the resume must not submit a second start of the same listener container");

        HELD.runPending();

        assertOneListenerContainerConsumes();
        assertStopReleasesTheListener();
    }

    @Test
    void testResumeWhileAsyncStartIsInitializingTheListenerContainer() throws Exception {
        useListenerContainerWithHooks();
        startRoute();
        context.getRouteController().suspendRoute("jms");
        // the route is resumed while the start task initializes the listener container, before the task checks the
        // status: the task starts the container
        initializeHook = () -> context.getRouteController().resumeRoute("jms");

        HELD.runPending();

        assertEquals(1, hooksRun.get(), "the route was resumed while the listener container was initialized");
        assertEquals(0, HELD.pending(), "the resume must not submit a second start of the same listener container");
        assertOneListenerContainerConsumes();
        assertStopReleasesTheListener();
    }

    @Test
    void testResumeWhileAsyncStartIsPendingOnTopicProcessesEachMessageOnce() throws Exception {
        topicEndpoint().getConfiguration().setAsyncStartListener(true);
        context.getRouteController().startRoute("topic");
        context.getRouteController().suspendRoute("topic");
        context.getRouteController().resumeRoute("topic");
        HELD.runPending();

        MockEndpoint topic = getMockEndpoint("mock:topic");
        topic.reset();
        AtomicInteger sent = new AtomicInteger();
        // the subscriptions are not durable: publish until the consumer received a few messages, so it subscribed
        await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() -> {
            template.sendBody("activemq:topic:" + QUEUE, "m" + sent.incrementAndGet());
            return countByBody(topic).size() >= 5;
        });
        template.sendBody("activemq:topic:" + QUEUE, "last");
        await().atMost(10, TimeUnit.SECONDS).until(() -> countByBody(topic).containsKey("last"));
        await().atMost(5, TimeUnit.SECONDS).during(1, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(countByBody(topic).values().stream().allMatch(c -> c == 1),
                        "a topic consumer with concurrentConsumers=1 must process each message once: "
                                                                                             + countByBody(topic)));

        DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) ((JmsConsumer) context
                .getRoute("topic").getConsumer()).getListenerContainer();
        assertEquals(container.getConcurrentConsumers(), container.getScheduledConsumerCount(),
                "the listener container must run concurrentConsumers invokers, not one set per start task");
    }

    @Test
    void testSuspendAndResumeAfterFailedAsyncStart() throws Exception {
        endpoint().getConfiguration().setTestConnectionOnStartup(true);
        startRoute();
        brokerDown = true;
        try {
            // the start task logs the failed connection test and returns
            HELD.runPending();
        } finally {
            brokerDown = false;
        }
        assertFalse(consumer().getListenerContainer().isRunning(), "the asynchronous start failed");

        context.getRouteController().suspendRoute("jms");
        context.getRouteController().resumeRoute("jms");
        assertEquals(1, HELD.pending(), "the resume starts the listener container again");
        HELD.runPending();

        assertOneListenerContainerConsumes();
        assertStopReleasesTheListener();
    }

    @Test
    void testSuspendAndResumeWithSynchronousStart() throws Exception {
        // control: asyncStartListener=false is not affected by the asynchronous start
        endpoint().getConfiguration().setAsyncStartListener(false);
        listenersBefore = endpoint().getRunningMessageListeners();
        context.getRouteController().startRoute("jms");
        assertEquals(0, HELD.pending(), "the listener container is started synchronously");
        context.getRouteController().suspendRoute("jms");
        assertSuspendedAndIdle();

        resumeAndAssertConsumes();
        assertStopReleasesTheListener();
    }

    private void startRoute() throws Exception {
        endpoint().getConfiguration().setAsyncStartListener(true);
        listenersBefore = endpoint().getRunningMessageListeners();
        context.getRouteController().startRoute("jms");
    }

    @Test
    void testAsyncStartWhileCamelContextIsStoppingStartsTheListenerContainerOnce() throws Exception {
        // while the CamelContext is stopping, the listener container is not allowed to run (runningAllowed() is
        // false), and with shutdownRoute=Defer the consumer is not suspended while another route has inflight
        // exchanges: the start task must start the container once, not again and again
        JmsAsyncStopListenerRestartTest.HeldExecutorService held = new JmsAsyncStopListenerRestartTest.HeldExecutorService();
        AtomicInteger starts = new AtomicInteger();
        CountDownLatch slowIn = new CountDownLatch(1);
        CountDownLatch slowGo = new CountDownLatch(1);
        CamelContext stopping = new DefaultCamelContext();
        JmsComponent jms = new JmsComponent() {
            @Override
            protected ExecutorService getAsyncStartStopExecutorService() {
                return held;
            }
        };
        jms.getConfiguration().setConnectionFactory(ConnectionFactoryHelper.createConnectionFactory(service));
        useListenerContainerCountingStarts(jms.getConfiguration(), starts);
        stopping.addComponent("activemq", jms);
        stopping.addRoutes(new RouteBuilder() {
            public void configure() {
                from("activemq:queue:" + QUEUE + "Stopping?asyncStartListener=true&testConnectionOnStartup=true")
                        .routeId("jms").shutdownRoute(ShutdownRoute.Defer).to("log:stopping?level=OFF");
                from("seda:slow").routeId("slow").process(e -> {
                    slowIn.countDown();
                    slowGo.await(20, TimeUnit.SECONDS);
                });
            }
        });
        stopping.start();
        Thread stopper = new Thread(stopping::stop, "JmsAsyncStartListenerSuspendResumeTest-stopper");
        try {
            assertEquals(1, held.pending(), "the asynchronous start is pending");
            stopping.createProducerTemplate().sendBody("seda:slow", "inflight");
            assertTrue(slowIn.await(10, TimeUnit.SECONDS), "the slow route has an inflight exchange");
            stopper.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> stopping.getStatus().isStopping());

            held.runPending();

            assertEquals(1, starts.get(), "the start task starts the listener container once");
        } finally {
            held.hold = false;
            slowGo.countDown();
            if (stopper.getState() == Thread.State.NEW) {
                stopping.stop();
            } else {
                stopper.join(TimeUnit.SECONDS.toMillis(30));
            }
        }
        assertTrue(stopping.isStopped(), "the CamelContext stopped");
    }

    @Test
    void testSynchronousStartWhileEndpointIsNotStartedStartsTheListenerContainerOnce() throws Exception {
        // with asyncStartListener=false the start runs on the calling thread: a consumer started while its endpoint is
        // not started (runningAllowed() is false) must start its listener container once and return
        AtomicInteger starts = new AtomicInteger();
        CamelContext notStarted = new DefaultCamelContext();
        JmsComponent jms = new JmsComponent();
        jms.getConfiguration().setConnectionFactory(ConnectionFactoryHelper.createConnectionFactory(service));
        useListenerContainerCountingStarts(jms.getConfiguration(), starts);
        notStarted.addComponent("activemq", jms);
        JmsEndpoint notStartedEndpoint = notStarted.getEndpoint("activemq:queue:" + QUEUE + "NotStarted", JmsEndpoint.class);
        JmsConsumer notStartedConsumer = notStartedEndpoint.createConsumer(exchange -> {
        });
        try {
            assertFalse(notStartedEndpoint.isStarted(), "the endpoint is not started");

            notStartedConsumer.start();

            assertEquals(ServiceStatus.Started, notStartedConsumer.getStatus());
            assertEquals(1, starts.get(), "the start starts the listener container once");
        } finally {
            notStartedConsumer.stop();
            notStarted.stop();
        }
    }

    private void useListenerContainerWithHooks() {
        // the listener containers run the hooks (if any) when they are initialized and started
        endpoint().getConfiguration().setMessageListenerContainerFactory(e -> new DefaultJmsMessageListenerContainer(e) {
            @Override
            public void afterPropertiesSet() {
                runHook(initializeHook);
                initializeHook = null;
                super.afterPropertiesSet();
            }

            @Override
            public void start() {
                ThrowingRunnable<Exception> before = beforeStartHook;
                beforeStartHook = null;
                runHook(before);
                super.start();
                ThrowingRunnable<Exception> after = afterStartHook;
                afterStartHook = null;
                runHook(after);
            }
        });
        endpoint().getConfiguration().setConsumerType(ConsumerType.Custom);
    }

    private void runHook(ThrowingRunnable<Exception> hook) {
        if (hook != null) {
            try {
                hook.run();
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
            hooksRun.incrementAndGet();
        }
    }

    private static void useListenerContainerCountingStarts(JmsConfiguration configuration, AtomicInteger starts) {
        // the listener containers count their starts, and fail a second start, so a start that repeats ends
        configuration.setConsumerType(ConsumerType.Custom);
        configuration.setMessageListenerContainerFactory(e -> new DefaultJmsMessageListenerContainer(e) {
            @Override
            public void start() {
                if (starts.incrementAndGet() > 1) {
                    throw new IllegalStateException("The listener container is started again");
                }
                super.start();
            }
        });
    }

    private void assertSuspendedAndIdle() throws Exception {
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("jms"));
        assertFalse(consumer().getListenerContainer().isRunning(),
                "a suspended consumer must not have a running listener container");
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(0);
        result.setAssertPeriod(1000);
        template.sendBody("activemq:queue:" + QUEUE, "sent while suspended");
        result.assertIsSatisfied();
    }

    private void resumeAndAssertConsumes() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedBodiesReceived("sent while suspended");
        context.getRouteController().resumeRoute("jms");
        result.assertIsSatisfied();
        assertOneListenerContainerConsumes();
    }

    private void assertOneListenerContainerConsumes() throws Exception {
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("jms"));
        DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) consumer().getListenerContainer();
        assertTrue(container.isRunning(), "the resumed consumer must have a running listener container");
        assertEquals(container.getConcurrentConsumers(), container.getScheduledConsumerCount(),
                "the listener container must run concurrentConsumers invokers, not one set per start task");
        assertEquals(1, endpoint().getRunningMessageListeners() - listenersBefore,
                "one listener container is counted once on the endpoint");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedBodiesReceived("Hello World");
        template.sendBody("activemq:queue:" + QUEUE, "Hello World");
        result.assertIsSatisfied();
    }

    private void assertStopReleasesTheListener() throws Exception {
        context.getRouteController().stopRoute("jms");
        HELD.runPending();
        assertEquals(listenersBefore, endpoint().getRunningMessageListeners(),
                "a stopped route leaves no running message listener counted on the endpoint");
    }

    private static Map<Object, Long> countByBody(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream()
                .collect(Collectors.groupingBy(e -> e.getMessage().getBody(), Collectors.counting()));
    }

    private JmsEndpoint topicEndpoint() {
        return context.getEndpoint("activemq:topic:" + QUEUE, JmsEndpoint.class);
    }

    private JmsEndpoint endpoint() {
        return context.getEndpoint("activemq:queue:" + QUEUE, JmsEndpoint.class);
    }

    private JmsConsumer consumer() {
        return (JmsConsumer) context.getRoute("jms").getConsumer();
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        ConnectionFactory failing = (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ConnectionFactory.class }, (proxy, method, args) -> {
                    if (brokerDown && method.getName().startsWith("create")) {
                        throw new JMSException("The broker is down (test)");
                    }
                    try {
                        return method.invoke(connectionFactory, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        JmsComponent jms = new JmsComponent() {
            @Override
            protected ExecutorService getAsyncStartStopExecutorService() {
                return HELD;
            }
        };
        jms.getConfiguration().setConnectionFactory(failing);
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:" + QUEUE).routeId("jms").autoStartup(false).to("mock:result");
                from("activemq:topic:" + QUEUE).routeId("topic").autoStartup(false).to("mock:topic");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() throws Exception {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        HELD.hold = true;
        brokerDown = false;
        initializeHook = null;
        beforeStartHook = null;
        afterStartHook = null;
        // the consumers and their listener containers are created when the CamelContext starts, before a test
        // configures them: stopping the routes drops those listener containers, so each test starts its route with a
        // new one made from its own configuration, whatever ran before it
        context.getRouteController().stopRoute("jms");
        context.getRouteController().stopRoute("topic");
    }

    @AfterEach
    void stopRoutes() throws Exception {
        // run what is left, and stop the routes with the tasks running inline, for the next test
        HELD.hold = false;
        HELD.runPending();
        context.getRouteController().stopRoute("jms");
        context.getRouteController().stopRoute("topic");
        for (JmsEndpoint endpoint : new JmsEndpoint[] { endpoint(), topicEndpoint() }) {
            endpoint.getConfiguration().setAsyncStartListener(false);
            endpoint.getConfiguration().setTestConnectionOnStartup(false);
            endpoint.getConfiguration().setMessageListenerContainerFactory(null);
            endpoint.getConfiguration().setConsumerType(ConsumerType.Default);
        }
        // drop what a failed test left in the queue, so it does not reach the next test
        while (camelContextExtension.getConsumerTemplate().receiveBody("activemq:queue:" + QUEUE, 100) != null) {
            // drained
        }
    }
}
