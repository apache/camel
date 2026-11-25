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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The suspend and the stop of a consumer stop its listener container, which stops its JMS connection. A JMS client may
 * not return from that call before a timeout of its own elapses when the broker does not answer (the call timeout of
 * ActiveMQ Artemis: 30 seconds by default), and does not react to an interrupt. The consumer waits for its listener
 * container at most the shutdownAwaitTermination of the ExecutorServiceManager: then the listener container is stopped
 * and destroyed in the background, and the consumer creates a new one when it is resumed or started again.
 * <p/>
 * The listener containers of the test block, when the test says so, in the stop of their shared connection or in their
 * destroy, until the test releases them, and ignore interrupts.
 */
@Timeout(60)
public class JmsConsumerListenerContainerStopTimeoutTest {

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    // how long the consumer waits for its listener container, in these tests
    private static final long BOUND = 1000;

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<BlockingListenerContainer> containers = new CopyOnWriteArrayList<>();
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final ExecutorService caller = Executors.newCachedThreadPool();
    private volatile Block block = Block.NONE;
    private volatile boolean failDestroy;
    private AbstractAppender appender;
    private CamelContext context;
    private ProducerTemplate template;
    private String queue;

    private enum Block {
        NONE,
        // in the stop of the shared connection (the stop of the listener container: a suspend of the consumer)
        STOP,
        // in the destroy of the listener container (the stop of the consumer)
        DESTROY
    }

    @BeforeEach
    void setUp(TestInfo info) throws Exception {
        queue = getClass().getSimpleName() + "." + info.getTestMethod().orElseThrow().getName();
        appender = new AbstractAppender("listenerContainerStop", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        ((Logger) LogManager.getLogger(JmsConsumer.class)).addAppender(appender);

        context = new DefaultCamelContext();
        context.getExecutorServiceManager().setShutdownAwaitTermination(BOUND);
        // longer than the test waits: the suspend or stop must not end because the graceful shutdown timed out
        context.getShutdownStrategy().setTimeout(60);
        context.getRegistry().bind("blockingFactory", (MessageListenerContainerFactory) endpoint -> {
            BlockingListenerContainer container = new BlockingListenerContainer(endpoint);
            containers.add(container);
            return container;
        });
        context.addComponent("jms", JmsComponent.jmsComponent(ConnectionFactoryHelper.createConnectionFactory(service)));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:" + queue + "?messageListenerContainerFactory=#blockingFactory").routeId("jms")
                        .to("mock:result");
            }
        });
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        // the stop of a listener container left in the background returns
        release.countDown();
        try {
            context.getExecutorServiceManager().setShutdownAwaitTermination(BOUND);
            context.stop();
        } finally {
            ((Logger) LogManager.getLogger(JmsConsumer.class)).removeAppender(appender);
            appender.stop();
            caller.shutdownNow();
        }
    }

    @Test
    public void testContextStopWhileTheSuspendOfTheListenerContainerDoesNotReturn() throws Exception {
        BlockingListenerContainer container = containers.get(0);
        JmsEndpoint endpoint = endpoint();
        block = Block.STOP;

        assertReturns("the stop of the CamelContext", () -> {
            context.stop();
            return null;
        });

        assertEquals(0, blocked.getCount(), "the stop of the listener container is blocked");
        assertWarning("The suspend of the consumer on destination " + queue + " did not end within " + BOUND + " millis");
        assertFalse(container.destroyed, "the listener container is not destroyed while its stop is blocked");
        // the ExecutorServiceManager did not wait for (and then force) the private thread pool of the listener container
        assertFalse(container.taskExecutor.isTerminated(), "the threads of the listener container are left to its stop");

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> container.destroyed && endpoint.getRunningMessageListeners() == 0);
    }

    @Test
    public void testContextStopWhileTheDestroyOfTheListenerContainerDoesNotReturn() throws Exception {
        BlockingListenerContainer container = containers.get(0);
        JmsEndpoint endpoint = endpoint();
        suspendRouteInTime();
        block = Block.DESTROY;

        assertReturns("the stop of the CamelContext", () -> {
            context.stop();
            return null;
        });

        assertEquals(0, blocked.getCount(), "the destroy of the listener container is blocked");
        assertWarning("The stop of the consumer on destination " + queue + " did not end within " + BOUND + " millis");
        assertEquals(1, endpoint.getRunningMessageListeners(), "the listener container is not stopped yet");

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> container.destroyed && endpoint.getRunningMessageListeners() == 0);
    }

    @Test
    public void testResumeAfterASuspendThatDidNotEndCreatesANewListenerContainer() throws Exception {
        assertConsumedOnce("Hello World");
        BlockingListenerContainer first = containers.get(0);
        block = Block.STOP;

        assertReturns("the suspend of the route", () -> {
            context.getRouteController().suspendRoute("jms");
            return null;
        });

        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("jms"));
        assertWarning("The suspend of the consumer on destination " + queue + " did not end within " + BOUND + " millis");
        assertTrue(first.taskExecutor.isShutdown(),
                "the private thread pool of the first listener container takes no new task");

        context.getRouteController().resumeRoute("jms");

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("jms"));
        assertEquals(2, containers.size(), "the resume creates a new listener container");
        BlockingListenerContainer second = containers.get(1);
        assertSame(second, consumer().getListenerContainer());
        assertTrue(second.isRunning(), "the new listener container runs");
        assertConsumedOnce("Bye World");

        // the stop of the first listener container returns: it is destroyed, and its private thread pool shut down
        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> first.destroyed && first.taskExecutor.isTerminated()
                && endpoint().getRunningMessageListeners() == 1);
        assertTrue(second.isRunning(), "the new listener container still runs");
        assertFalse(second.taskExecutor.isShutdown(), "the thread pool of the new listener container is not shut down");
        assertConsumedOnce("Hello again");
    }

    @Test
    public void testStartAfterAStopThatDidNotEndCreatesANewListenerContainer() throws Exception {
        assertConsumedOnce("Hello World");
        BlockingListenerContainer first = containers.get(0);
        suspendRouteInTime();
        block = Block.DESTROY;

        assertReturns("the stop of the route", () -> {
            context.getRouteController().stopRoute("jms");
            return null;
        });

        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("jms"));
        assertWarning("The stop of the consumer on destination " + queue + " did not end within " + BOUND + " millis");
        assertEquals(1, endpoint().getRunningMessageListeners(), "the first listener container is not stopped yet");

        context.getRouteController().startRoute("jms");

        assertEquals(2, containers.size(), "the start creates a new listener container");
        BlockingListenerContainer second = containers.get(1);
        assertSame(second, consumer().getListenerContainer());
        assertConsumedOnce("Bye World");

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> first.destroyed && first.taskExecutor.isTerminated()
                && endpoint().getRunningMessageListeners() == 1);
        assertTrue(second.isRunning(), "the new listener container still runs");
    }

    @Test
    public void testSuspendReturnsWhenTheWaitingThreadIsInterrupted() throws Exception {
        BlockingListenerContainer container = containers.get(0);
        JmsConsumer consumer = consumer();
        // much longer than the test waits
        context.getExecutorServiceManager().setShutdownAwaitTermination(TimeUnit.MINUTES.toMillis(5));
        block = Block.STOP;
        CountDownLatch suspended = new CountDownLatch(1);
        AtomicBoolean interruptKept = new AtomicBoolean();
        Future<?> suspend = caller.submit(() -> {
            try {
                consumer.suspend();
                interruptKept.set(Thread.currentThread().isInterrupted());
            } finally {
                suspended.countDown();
            }
        });
        assertTrue(blocked.await(10, TimeUnit.SECONDS), "the stop of the listener container is blocked");

        // as the graceful shutdown does to its task when its timeout elapses
        suspend.cancel(true);

        assertTrue(suspended.await(10, TimeUnit.SECONDS), "the suspend returns once its thread is interrupted");
        assertTrue(interruptKept.get(), "the thread keeps its interrupt status");
        assertTrue(consumer.isSuspended(), "the consumer is suspended");
        assertWarning("The suspend of the consumer on destination " + queue
                      + " was interrupted before its listener container stopped");

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> container.destroyed && container.taskExecutor.isTerminated()
                && endpoint().getRunningMessageListeners() == 0);
    }

    @Test
    public void testResumeAfterASuspendThatDidNotEndStartsTheNewListenerContainerWithoutAutoStartup() throws Exception {
        String manualQueue = queue + ".manual";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:" + manualQueue + "?autoStartup=false&messageListenerContainerFactory=#blockingFactory")
                        .routeId("manual").to("mock:manual");
            }
        });
        JmsConsumer consumer = (JmsConsumer) context.getRoute("manual").getConsumer();
        AbstractMessageListenerContainer first = consumer.getListenerContainer();
        assertFalse(first.isRunning(), "the listener container does not start with the consumer");
        consumer.startListenerContainer();
        assertConsumedOnce("mock:manual", manualQueue, "Hello World");
        block = Block.STOP;

        assertReturns("the suspend of the route", () -> {
            context.getRouteController().suspendRoute("manual");
            return null;
        });
        assertWarning("The suspend of the consumer on destination " + manualQueue + " did not end within " + BOUND
                      + " millis");

        context.getRouteController().resumeRoute("manual");

        AbstractMessageListenerContainer second = consumer.getListenerContainer();
        assertNotSame(first, second, "the resume creates a new listener container");
        // as the resume starts the listener container that a suspend that ends in time keeps
        assertTrue(second.isRunning(), "the resume starts the new listener container");
        assertConsumedOnce("mock:manual", manualQueue, "Bye World");
    }

    @Test
    public void testErrorOfTheStopInTheBackgroundIsLogged() throws Exception {
        BlockingListenerContainer container = containers.get(0);
        suspendRouteInTime();
        block = Block.DESTROY;
        failDestroy = true;

        assertReturns("the stop of the route", () -> {
            context.getRouteController().stopRoute("jms");
            return null;
        });
        assertWarning("The stop of the consumer on destination " + queue + " did not end within " + BOUND + " millis");

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> warnings().size() == 2);
        LogEvent error = warnings().get(1);
        assertTrue(error.getMessage().getFormattedMessage()
                .startsWith("Error stopping listener container on destination: " + queue + " in the background."),
                error.getMessage().getFormattedMessage());
        assertNotNull(error.getThrown(), "the exception is logged");
        assertEquals(0, endpoint().getRunningMessageListeners());
        assertTrue(container.taskExecutor.isShutdown(), "the private thread pool is shut down");
    }

    @Test
    public void testSuspendResumeAndStopOfAListenerContainerThatStopsInTime() throws Exception {
        // the default: 10 seconds
        context.getExecutorServiceManager().setShutdownAwaitTermination(10000);
        assertConsumedOnce("Hello World");
        BlockingListenerContainer container = containers.get(0);

        context.getRouteController().suspendRoute("jms");

        assertSame(container, consumer().getListenerContainer(), "the suspended consumer keeps its listener container");
        assertFalse(container.isRunning(), "the listener container is stopped");
        assertFalse(container.destroyed, "the listener container is not destroyed");

        context.getRouteController().resumeRoute("jms");

        assertSame(container, consumer().getListenerContainer(), "the resumed consumer keeps its listener container");
        assertTrue(container.isRunning(), "the listener container runs again");
        assertEquals(1, containers.size());
        assertConsumedOnce("Bye World");

        context.getRouteController().stopRoute("jms");

        // the stop returns once the listener container is destroyed and its private thread pool shut down
        assertTrue(container.destroyed, "the listener container is destroyed");
        assertTrue(container.taskExecutor.isShutdown(), "the private thread pool is shut down");
        assertEquals(0, endpoint().getRunningMessageListeners());
        assertEquals(List.of(), warnings(), "no WARN");
    }

    /**
     * A route that suspends its own consumer while it processes a message, as a route policy such as
     * ThrottlingInflightRoutePolicy does when an exchange is done. With a synchronous task executor, the simple
     * listener container processes the message in the message listener of the JMS client.
     */
    @ParameterizedTest
    @ValueSource(strings = { "Default", "Simple", "SimpleSync" })
    public void testSuspendFromTheListenerThread(String type) throws Exception {
        // the default: 10 seconds
        context.getExecutorServiceManager().setShutdownAwaitTermination(10000);
        context.getRegistry().bind("syncTaskExecutor", new SyncTaskExecutor());
        String listenerQueue = queue + "." + type;
        String options
                = "SimpleSync".equals(type) ? "consumerType=Simple&taskExecutor=#syncTaskExecutor" : "consumerType=" + type;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:" + listenerQueue + "?" + options).routeId("self")
                        .process(exchange -> ServiceHelper
                                .suspendService(exchange.getContext().getRoute("self").getConsumer()))
                        .to("mock:self");
            }
        });
        JmsConsumer consumer = (JmsConsumer) context.getRoute("self").getConsumer();
        AbstractMessageListenerContainer container = consumer.getListenerContainer();
        MockEndpoint self = MockEndpoint.resolve(context, "mock:self");
        self.expectedBodiesReceived("Hello World");

        template.sendBody("jms:queue:" + listenerQueue, "Hello World");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertTrue(consumer.isSuspended(), "the consumer is suspended");
        // the suspend did not give up waiting for the listener container
        assertSame(container, consumer.getListenerContainer(), "the suspended consumer keeps its listener container");
        List<String> warnings = events.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(e -> e.getMessage().getFormattedMessage()).filter(m -> m.contains(listenerQueue)).toList();
        assertEquals(List.of(), warnings, "no WARN");
    }

    /**
     * Suspends the route, waiting for the listener container as long as by default (10 seconds), so that only the stop
     * that follows, which the test blocks, has the short time limit of the test.
     */
    private void suspendRouteInTime() throws Exception {
        context.getExecutorServiceManager().setShutdownAwaitTermination(10000);
        context.getRouteController().suspendRoute("jms");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("jms"));
        context.getExecutorServiceManager().setShutdownAwaitTermination(BOUND);
    }

    private JmsEndpoint endpoint() {
        return context.getEndpoint("jms:queue:" + queue + "?messageListenerContainerFactory=#blockingFactory",
                JmsEndpoint.class);
    }

    private JmsConsumer consumer() {
        return (JmsConsumer) context.getRoute("jms").getConsumer();
    }

    private void assertConsumedOnce(String body) throws Exception {
        assertConsumedOnce("mock:result", queue, body);
    }

    private void assertConsumedOnce(String mock, String destination, String body) throws Exception {
        MockEndpoint result = MockEndpoint.resolve(context, mock);
        result.reset();
        result.expectedBodiesReceived(body);
        // and not a second time
        result.setAssertPeriod(500);
        template.sendBody("jms:queue:" + destination, body);
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Runs the given action on another thread, and fails when it does not return within 10 seconds (the listener
     * container of the test blocks until the test releases it).
     */
    private void assertReturns(String action, Callable<Void> callable) throws Exception {
        Future<Void> future = caller.submit(callable);
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail(action + " did not return within 10 seconds, while the listener container does not stop");
        }
    }

    private List<LogEvent> warnings() {
        return events.stream()
                .filter(e -> e.getLevel() == Level.WARN && e.getMessage().getFormattedMessage().contains(queue))
                .toList();
    }

    private void assertWarning(String start) {
        List<String> warnings = warnings().stream().map(e -> e.getMessage().getFormattedMessage()).toList();
        assertEquals(1, warnings.size(), "one WARN: " + warnings);
        assertTrue(warnings.get(0).startsWith(start), warnings.get(0));
        assertTrue(warnings.get(0).contains("The listener container is stopped and destroyed in the background"),
                warnings.get(0));
    }

    /**
     * Waits until the test releases the listener container: as a JMS client waiting for a broker that does not answer,
     * an interrupt does not end the wait.
     */
    private void blockUntilReleased() {
        blocked.countDown();
        boolean interrupted = false;
        try {
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final class BlockingListenerContainer extends DefaultJmsMessageListenerContainer {

        private volatile ExecutorService taskExecutor;
        private volatile boolean destroyed;

        BlockingListenerContainer(JmsEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void setTaskExecutor(Executor taskExecutor) {
            super.setTaskExecutor(taskExecutor);
            // the private thread pool of the consumer
            this.taskExecutor = (ExecutorService) taskExecutor;
        }

        @Override
        protected void stopSharedConnection() {
            super.stopSharedConnection();
            if (block == Block.STOP) {
                blockUntilReleased();
            }
        }

        @Override
        protected void doShutdown() throws JMSException {
            if (block == Block.DESTROY) {
                blockUntilReleased();
            }
            super.doShutdown();
            if (failDestroy) {
                throw new JMSException("The broker does not answer");
            }
        }

        @Override
        public void destroy() {
            super.destroy();
            destroyed = true;
        }
    }
}
