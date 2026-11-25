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
package org.apache.camel.component.springrabbit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The stop of a listener container cancels its consumers and waits for the broker to answer, without reacting to an
 * interrupt: on a connection that stopped answering, up to the {@code channelRpcTimeout} of the RabbitMQ client (10
 * minutes by default). The suspend and the stop of the consumer must not wait for it without a bound, and a resume or a
 * start must work with a new listener container meanwhile.
 * <p/>
 * The connection factory is a mock, whose channels answer a cancel only when the test lets them, once they stopped
 * answering.
 */
class SpringRabbitMQConsumerStopTest {

    // how long the consumer waits for its listener container to stop (shutdownAwaitTermination)
    private static final long BOUND = 1000;
    // much longer than the bound, when the listener container stops in time
    private static final long HEALTHY_BOUND = 30000;
    // the time a suspend or a stop that does not wait for the broker may take on a busy machine: far less than the
    // time the broker takes to answer (when the test lets it)
    private static final long STOP_TIME = 20000;

    // the exchange and the queue, unique to the test: the log of the test names them only
    private final String name = "orders-" + UUID.randomUUID();

    private final List<AbstractMessageListenerContainer> containers = new CopyOnWriteArrayList<>();
    // each destroy of a listener container
    private final List<AbstractMessageListenerContainer> destroyed = new CopyOnWriteArrayList<>();
    // an error that the next stop of a listener container throws, once it stopped
    private final AtomicReference<Error> stopError = new AtomicReference<>();
    // the channels on which a consumer subscribed, in that order
    private final List<FakeChannel> subscribed = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> removedConnectionListeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger consumerTags = new AtomicInteger();
    // the broker answers the cancel on a channel that stopped answering, only when the test lets it
    private final CountDownLatch answer = new CountDownLatch(1);
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final ExecutorService testThreads = Executors.newCachedThreadPool();
    private CamelContext context;
    private AbstractAppender appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.getExecutorServiceManager().setShutdownAwaitTermination(BOUND);
        // longer than the test waits for a stop: the stop must not end because the graceful shutdown timed out
        context.getShutdownStrategy().setTimeout(60);
        context.getRegistry().bind("cf", connectionFactory());
        AmqpAdmin admin = mock(AmqpAdmin.class);
        when(admin.declareQueue(any(Queue.class))).thenAnswer(invocation -> invocation.getArgument(0, Queue.class).getName());
        SpringRabbitMQComponent component = context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class);
        component.setAmqpAdmin(admin);
        component.setListenerContainerFactory(this::createListenerContainer);

        appender = new AbstractAppender("consumerStop", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger = (Logger) LogManager.getLogger(SpringRabbitMQConsumer.class);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        // the broker answers: a stop that waits for it ends
        answer.countDown();
        logger.removeAppender(appender);
        appender.stop();
        context.stop();
        testThreads.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void suspendDoesNotWaitForTheBrokerToAnswerTheCancel(String type) throws Exception {
        startRoute(type);
        FakeChannel channel = subscribed(0);
        channel.stopAnswering();

        suspendThatDoesNotWait();

        await("the cancel waits for the broker").atMost(10, TimeUnit.SECONDS).until(channel::isCancelling);
        List<String> warnings = warnings();
        assertEquals(1, warnings.size(), "one WARN for the listener container that stops in the background: " + warnings);
        assertTrue(warnings.get(0).contains("(queues: [" + name + "]) did not stop within " + BOUND + " millis"),
                warnings.get(0));
        AbstractMessageListenerContainer container = containers.get(0);
        assertFalse(destroyed.contains(container));

        // the stop that follows has no listener container to wait for
        CompletableFuture<Void> stop = inAnotherThread(() -> context.getRouteController().stopRoute("orders"));
        await("the stop returns").atMost(STOP_TIME, TimeUnit.MILLISECONDS).until(stop::isDone);
        stop.join();
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("orders"));
        assertEquals(1, warnings().size(), "no other WARN: " + warnings());

        // the broker answers: the stop of the listener container ends, and destroys it
        answer.countDown();
        await("the listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(container));
        assertFalse(container.isRunning());
        assertEquals(1, containers.size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void contextStopDoesNotWaitForTheBrokerToAnswerTheCancel(String type) throws Exception {
        startRoute(type);
        subscribed(0).stopAnswering();

        CompletableFuture<Void> stop = inAnotherThread(context::stop);

        await("the context stops").atMost(STOP_TIME, TimeUnit.MILLISECONDS).until(stop::isDone);
        stop.join();
        assertTrue(context.isStopped());
        // the graceful shutdown suspends the consumer, which does not wait; its stop then has nothing to wait for
        assertEquals(1, warnings().size(), "one WARN: " + warnings());
        AbstractMessageListenerContainer container = containers.get(0);
        assertFalse(destroyed.contains(container));

        answer.countDown();
        await("the listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(container));
        assertFalse(container.isRunning());
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void interruptedSuspendDoesNotWaitForTheBrokerToAnswerTheCancel(String type) throws Exception {
        // the graceful shutdown interrupts the suspend when its timeout elapses, which can be before this bound
        context.getExecutorServiceManager().setShutdownAwaitTermination(60000);
        startRoute(type);
        FakeChannel channel = subscribed(0);
        channel.stopAnswering();
        SpringRabbitMQConsumer consumer = (SpringRabbitMQConsumer) context.getRoute("orders").getConsumer();
        AtomicReference<Thread> suspending = new AtomicReference<>();
        AtomicBoolean interruptedAfterSuspend = new AtomicBoolean();
        CompletableFuture<Void> suspend = inAnotherThread(() -> {
            suspending.set(Thread.currentThread());
            consumer.suspend();
            interruptedAfterSuspend.set(Thread.currentThread().isInterrupted());
        });
        await("the cancel waits for the broker").atMost(10, TimeUnit.SECONDS).until(channel::isCancelling);

        suspending.get().interrupt();

        await("the suspend returns").atMost(STOP_TIME, TimeUnit.MILLISECONDS).until(suspend::isDone);
        suspend.join();
        assertTrue(consumer.isSuspended());
        assertTrue(interruptedAfterSuspend.get(), "the interrupt is kept");
        List<String> warnings = warnings();
        assertEquals(1, warnings.size(), "one WARN: " + warnings);
        assertTrue(warnings.get(0).startsWith("Interrupted while waiting for the listener container"), warnings.get(0));

        answer.countDown();
        await("the listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(containers.get(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void errorOfAStopInTheBackgroundIsLoggedAndTheListenerContainerDestroyed(String type) throws Exception {
        startRoute(type);
        subscribed(0).stopAnswering();
        AssertionError error = new AssertionError("the stop failed");
        stopError.set(error);
        suspendThatDoesNotWait();
        AbstractMessageListenerContainer container = containers.get(0);

        // the broker answers: the stop, which nobody waits for anymore, fails with an error
        answer.countDown();

        await("the error is logged").atMost(10, TimeUnit.SECONDS).until(() -> events.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getThrown() == error
                        && e.getMessage().getFormattedMessage().contains(name)));
        await("the listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(container));
        assertEquals(1, Collections.frequency(destroyed, container), "destroyed once: " + destroyed);
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void errorOfAStopInTimeReachesTheConsumerUnchanged(String type) throws Exception {
        startRoute(type);
        subscribed(0);
        AssertionError error = new AssertionError("the stop failed");
        stopError.set(error);
        SpringRabbitMQConsumer consumer = (SpringRabbitMQConsumer) context.getRoute("orders").getConsumer();

        // as when the consumer stopped the listener container on its own thread
        assertSame(error, assertThrows(AssertionError.class, consumer::suspend));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void resumeAfterASuspendThatDidNotWaitConsumesWithANewListenerContainer(String type) throws Exception {
        startRoute(type);
        subscribed(0).stopAnswering();
        suspendThatDoesNotWait();
        assertEquals(1, connectionListeners.size());

        // while the previous listener container still waits for the broker
        context.getRouteController().resumeRoute("orders");

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("orders"));
        assertEquals(2, containers.size());
        AbstractMessageListenerContainer previous = containers.get(0);
        AbstractMessageListenerContainer current = containers.get(1);
        assertTrue(current.isRunning());
        // the connection listener that declares on new connections declares with the new listener container
        assertEquals(2, connectionListeners.size());
        assertEquals(List.of(connectionListeners.get(0)), removedConnectionListeners);
        MockEndpoint mock = context.getEndpoint("mock:orders", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello", "World");
        subscribed(1).deliver("Hello");

        // the broker answers: the stop of the previous listener container ends, and does not touch the current one
        answer.countDown();
        await("the previous listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(previous));
        assertFalse(previous.isRunning());
        assertFalse(destroyed.contains(current));
        assertTrue(current.isRunning());
        subscribed(1).deliver("World");
        mock.assertIsSatisfied();
        assertEquals(1, warnings().size(), "one WARN: " + warnings());

        context.getExecutorServiceManager().setShutdownAwaitTermination(HEALTHY_BOUND);
        context.getRouteController().stopRoute("orders");
        assertTrue(destroyed.contains(current));
        assertEquals(connectionListeners, removedConnectionListeners);
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void resumeAfterASuspendThatDidNotWaitConsumesTheGeneratedQueue(String type) throws Exception {
        // no queues: the consumer consumes a queue with a generated name, which only its listener container knows
        startRouteFrom("spring-rabbitmq:" + name + "?connectionFactory=#cf&routingKey=new"
                       + "&messageListenerContainerType=" + type);
        FakeChannel channel = subscribed(0);
        String generated = channel.queue;
        assertTrue(generated.startsWith("spring.gen-"), generated);
        channel.stopAnswering();
        suspendThatDoesNotWait();

        context.getRouteController().resumeRoute("orders");

        // the new listener container consumes the same queue, rather than a new one
        assertEquals(generated, subscribed(1).queue);
        assertArrayEquals(new String[] { generated }, containers.get(1).getQueueNames());
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void startAfterASuspendThatDidNotWaitReplacesTheConnectionListener(String type) throws Exception {
        startRoute(type);
        subscribed(0).stopAnswering();
        suspendThatDoesNotWait();
        SpringRabbitMQConsumer consumer = (SpringRabbitMQConsumer) context.getRoute("orders").getConsumer();

        // a start of the suspended consumer, rather than a resume
        consumer.start();

        assertTrue(consumer.isStarted());
        assertEquals(2, containers.size());
        assertTrue(containers.get(1).isRunning());
        assertEquals(2, connectionListeners.size());
        assertEquals(List.of(connectionListeners.get(0)), removedConnectionListeners);

        answer.countDown();
        context.getExecutorServiceManager().setShutdownAwaitTermination(HEALTHY_BOUND);
        context.getRouteController().stopRoute("orders");
        assertEquals(connectionListeners, removedConnectionListeners);
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void consumerStopDoesNotWaitForTheBrokerToAnswerTheCancel(String type) throws Exception {
        startRoute(type);
        subscribed(0).stopAnswering();
        SpringRabbitMQConsumer consumer = (SpringRabbitMQConsumer) context.getRoute("orders").getConsumer();

        CompletableFuture<Void> stop = inAnotherThread(consumer::stop);

        await("the consumer stops").atMost(STOP_TIME, TimeUnit.MILLISECONDS).until(stop::isDone);
        stop.join();
        assertTrue(consumer.isStopped());
        assertEquals(1, warnings().size(), "one WARN: " + warnings());
        assertEquals(connectionListeners, removedConnectionListeners);

        // it starts again with a new listener container, while the previous one still waits for the broker
        consumer.start();
        assertEquals(2, containers.size());
        AbstractMessageListenerContainer previous = containers.get(0);
        AbstractMessageListenerContainer current = containers.get(1);
        MockEndpoint mock = context.getEndpoint("mock:orders", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello");
        subscribed(1).deliver("Hello");
        mock.assertIsSatisfied();

        answer.countDown();
        await("the previous listener container is destroyed").atMost(10, TimeUnit.SECONDS)
                .until(() -> destroyed.contains(previous));
        assertFalse(destroyed.contains(current));
        assertTrue(current.isRunning());
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    void suspendResumeAndStopWhileTheBrokerAnswers(String type) throws Exception {
        context.getExecutorServiceManager().setShutdownAwaitTermination(HEALTHY_BOUND);
        startRoute(type);
        subscribed(0);
        AbstractMessageListenerContainer container = containers.get(0);

        context.getRouteController().suspendRoute("orders");

        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("orders"));
        assertFalse(container.isRunning());
        assertFalse(destroyed.contains(container));

        context.getRouteController().resumeRoute("orders");

        // the same listener container consumes again
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("orders"));
        assertEquals(List.of(container), containers);
        assertTrue(container.isRunning());
        assertEquals(1, connectionListeners.size());
        assertTrue(removedConnectionListeners.isEmpty());
        MockEndpoint mock = context.getEndpoint("mock:orders", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello");
        subscribed(1).deliver("Hello");
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("orders");

        assertTrue(destroyed.contains(container));
        assertEquals(connectionListeners, removedConnectionListeners);
        assertTrue(warnings().isEmpty(), "no WARN: " + warnings());
    }

    private void startRoute(String type) throws Exception {
        startRouteFrom("spring-rabbitmq:" + name + "?connectionFactory=#cf&queues=" + name + "&routingKey=new"
                       + "&messageListenerContainerType=" + type);
    }

    private void startRouteFrom(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("orders").to("mock:orders");
            }
        });
        context.start();
    }

    private void suspendThatDoesNotWait() {
        CompletableFuture<Void> suspend = inAnotherThread(() -> context.getRouteController().suspendRoute("orders"));
        await("the suspend returns").atMost(STOP_TIME, TimeUnit.MILLISECONDS).until(suspend::isDone);
        suspend.join();
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("orders"));
    }

    /**
     * Creates the listener container of the given type, as the {@link DefaultListenerContainerFactory} does for what
     * the test needs, and keeps track of it and of its destroy.
     */
    private AbstractMessageListenerContainer createListenerContainer(SpringRabbitMQEndpoint endpoint) {
        AbstractMessageListenerContainer container;
        if (SpringRabbitMQConstants.SIMPLE_MESSAGE_LISTENER_CONTAINER.equals(endpoint.getMessageListenerContainerType())) {
            CamelSimpleMessageListenerContainer simple = new CamelSimpleMessageListenerContainer(endpoint) {
                @Override
                public void stop() {
                    super.stop();
                    failStop();
                }

                @Override
                public void destroy() {
                    destroyed.add(this);
                    super.destroy();
                }
            };
            // the consumer thread notices a cancel sooner
            simple.setReceiveTimeout(50);
            container = simple;
        } else {
            container = new CamelDirectMessageListenerContainer(endpoint) {
                @Override
                public void stop() {
                    super.stop();
                    failStop();
                }

                @Override
                public void destroy() {
                    destroyed.add(this);
                    super.destroy();
                }
            };
        }
        if (endpoint.getQueues() != null) {
            container.setQueueNames(endpoint.getQueues());
        }
        container.setAmqpAdmin(endpoint.getComponent().getAmqpAdmin());
        container.setAutoDeclare(endpoint.isAutoDeclare());
        container.setShutdownTimeout(endpoint.getComponent().getShutdownTimeout());
        containers.add(container);
        return container;
    }

    private void failStop() {
        Error error = stopError.getAndSet(null);
        if (error != null) {
            throw error;
        }
    }

    private ConnectionFactory connectionFactory() {
        ConnectionFactory cf = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel(anyBoolean())).thenAnswer(invocation -> new FakeChannel().channel);
        when(cf.createConnection()).thenReturn(connection);
        when(cf.getHost()).thenReturn("localhost");
        doAnswer(invocation -> connectionListeners.add(invocation.getArgument(0)))
                .when(cf).addConnectionListener(any(ConnectionListener.class));
        when(cf.removeConnectionListener(any(ConnectionListener.class)))
                .thenAnswer(invocation -> removedConnectionListeners.add(invocation.getArgument(0)));
        return cf;
    }

    private FakeChannel subscribed(int index) {
        await("a consumer subscribes").atMost(10, TimeUnit.SECONDS).until(() -> subscribed.size() > index);
        return subscribed.get(index);
    }

    /**
     * The WARNs about the consumer of this test: the logger is shared with the other tests of the JVM.
     */
    private List<String> warnings() {
        return events.stream().filter(e -> e.getLevel() == Level.WARN).map(e -> e.getMessage().getFormattedMessage())
                .filter(message -> message.contains(name)).toList();
    }

    private CompletableFuture<Void> inAnotherThread(Action action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, testThreads);
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /**
     * A channel of the broker, on which a consumer subscribes. Once it stopped answering, a cancel waits for the broker
     * to answer, as {@code ChannelN.basicCancel} does, and ignores an interrupt.
     */
    private final class FakeChannel {

        private final Channel channel = mock(Channel.class);
        private final Map<String, Consumer> consumers = new ConcurrentHashMap<>();
        private final CountDownLatch cancelling = new CountDownLatch(1);
        private final AtomicInteger deliveryTags = new AtomicInteger();
        private volatile boolean answering = true;
        private volatile String queue;

        private FakeChannel() throws IOException {
            when(channel.isOpen()).thenReturn(true);
            when(channel.queueDeclarePassive(anyString())).thenReturn(mock(AMQP.Queue.DeclareOk.class));
            when(channel.basicConsume(anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean(), any(),
                    any(Consumer.class))).thenAnswer(invocation -> {
                        String tag = "amq.ctag-" + consumerTags.incrementAndGet();
                        queue = invocation.getArgument(0);
                        Consumer consumer = invocation.getArgument(6);
                        consumers.put(tag, consumer);
                        consumer.handleConsumeOk(tag);
                        subscribed.add(this);
                        return tag;
                    });
            doAnswer(invocation -> {
                cancel(invocation.getArgument(0));
                return null;
            }).when(channel).basicCancel(anyString());
        }

        private void stopAnswering() {
            answering = false;
        }

        private boolean isCancelling() {
            return cancelling.getCount() == 0;
        }

        private void cancel(String tag) {
            Consumer consumer = consumers.remove(tag);
            if (!answering) {
                cancelling.countDown();
                awaitAnswer();
            }
            consumer.handleCancelOk(tag);
        }

        private void awaitAnswer() {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        // a safety net, so that a test that does not let the broker answer cannot hang the build
                        if (!answer.await(120, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("the test did not let the broker answer");
                        }
                        return;
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

        private void deliver(String body) throws IOException {
            Map.Entry<String, Consumer> consumer = consumers.entrySet().iterator().next();
            consumer.getValue().handleDelivery(consumer.getKey(),
                    new Envelope(deliveryTags.incrementAndGet(), false, name, "new"),
                    new AMQP.BasicProperties.Builder().contentType("text/plain").build(),
                    body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
