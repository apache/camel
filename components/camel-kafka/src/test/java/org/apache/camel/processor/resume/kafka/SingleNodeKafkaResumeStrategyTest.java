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
package org.apache.camel.processor.resume.kafka;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SingleNodeKafkaResumeStrategy}.
 */
public class SingleNodeKafkaResumeStrategyTest {

    @Test
    void testStopDoesNotThrowWhenLockNotAcquired() throws Exception {
        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy();

        ReentrantLock lock = getField(strategy, "writeLock");
        // Acquire the lock from another thread so tryLock fails in stop()
        Thread holder = new Thread(lock::lock);
        holder.start();
        holder.join();

        // stop() should not throw IllegalMonitorStateException
        assertDoesNotThrow(strategy::stop);
    }

    @Test
    void testGetAdapterReturnsAdapterWithoutLoadCache() {
        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy();

        ResumeAdapter adapter = new TestAdapter();
        strategy.setAdapter(adapter);

        assertSame(adapter, strategy.getAdapter());
    }

    @Test
    void testGetAdapterWaitsForInitializationWhenLatchIsSet() throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        KafkaResumeStrategyConfiguration config
                = new KafkaResumeStrategyConfigurationBuilder(props, props)
                        .withTopic("test-topic")
                        .build();

        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy(config);

        ResumeAdapter adapter = new TestAdapter();
        strategy.setAdapter(adapter);

        CountDownLatch latch = new CountDownLatch(1);
        setField(strategy, "initLatch", latch);

        // Count down so waitForInitialization() completes immediately
        latch.countDown();

        assertSame(adapter, strategy.getAdapter());
    }

    @Test
    void testStrategyCanBeStartedAgainAfterStop() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();

            // nothing listens on port 9: no broker is needed to create, subscribe and close the clients
            KafkaResumeStrategyConfiguration config
                    = new KafkaResumeStrategyConfigurationBuilder(
                            KafkaResumeStrategyConfigurationBuilder.createProducerProperties(),
                            KafkaResumeStrategyConfigurationBuilder.createConsumerProperties())
                            .withBootstrapServers("localhost:9")
                            .withGroupId("test-group")
                            .withEnableAutoCommit(false)
                            .withTopic("test-topic")
                            .build();

            SubscriptionRecordingStrategy strategy = new SubscriptionRecordingStrategy(config);
            strategy.setCamelContext(context);
            strategy.setAdapter(new TestDeserializableAdapter());

            try {
                strategy.start();
                strategy.loadCache();
                Producer<byte[], byte[]> firstProducer = strategy.getProducer();
                ExecutorService firstExecutor = strategy.getExecutorService();
                assertTrue(executorsOf(context).contains(firstExecutor));
                await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.subscriptions.size() == 1);
                strategy.stop();
                // the executor is shut down, and removed from the manager of the context (and so from JMX)
                assertTrue(firstExecutor.isShutdown());
                assertFalse(executorsOf(context).contains(firstExecutor), "the executor stays registered");

                // a route stop/start stops and starts the strategy again
                strategy.start();
                strategy.loadCache();
                ExecutorService executor = strategy.getExecutorService();
                assertAll(
                        () -> assertNotNull(strategy.getProducer()),
                        () -> assertNotSame(firstProducer, strategy.getProducer(), "the closed producer is used again"),
                        () -> assertFalse(executor.isShutdown(), "the executor that was shut down is used again"));

                // the new consumer subscribes to the topic again
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                        () -> assertEquals(List.of(Set.of("test-topic"), Set.of("test-topic")), strategy.subscriptions));
            } finally {
                strategy.stop();
            }
        }
    }

    @Test
    void testRefreshTaskThatOutlivesStopClosesOnlyItsOwnConsumer() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            // the producer is created, and never sends: nothing listens on port 9
            KafkaResumeStrategyConfiguration config
                    = new KafkaResumeStrategyConfigurationBuilder(
                            KafkaResumeStrategyConfigurationBuilder.createProducerProperties(),
                            KafkaResumeStrategyConfigurationBuilder.createConsumerProperties())
                            .withBootstrapServers("localhost:9")
                            .withTopic("test-topic")
                            .build();

            // each refresh task gets its own MockConsumer, and blocks in its first poll until released, ignoring the
            // wakeup and the interrupt of stop(), so the first task outlives stop()
            List<MockConsumer<byte[], byte[]>> consumers = new CopyOnWriteArrayList<>();
            List<CountDownLatch> polling = List.of(new CountDownLatch(1), new CountDownLatch(1));
            List<CountDownLatch> release = List.of(new CountDownLatch(1), new CountDownLatch(1));
            SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy(config) {
                @Override
                Consumer<byte[], byte[]> createConsumer() {
                    MockConsumer<byte[], byte[]> mock = new MockConsumer<>("earliest");
                    consumers.add(mock);
                    return mock;
                }

                @Override
                protected ConsumerRecords<byte[], byte[]> consume(Consumer<byte[], byte[]> consumer) {
                    int task = consumers.indexOf(consumer);
                    polling.get(task).countDown();
                    awaitUninterruptibly(release.get(task));
                    // ends the polling loop, as a wakeup does
                    throw new WakeupException();
                }
            };
            strategy.setCamelContext(context);
            strategy.setAdapter(new TestDeserializableAdapter());

            try {
                strategy.start();
                strategy.loadCache();
                assertTrue(polling.get(0).await(10, TimeUnit.SECONDS));
                // the first task does not end within the shutdown wait of stop()
                strategy.stop();

                strategy.start();
                strategy.loadCache();
                assertTrue(polling.get(1).await(10, TimeUnit.SECONDS));

                // the first task ends now, and closes its consumer
                release.get(0).countDown();
                await().atMost(10, TimeUnit.SECONDS).until(() -> consumers.get(0).closed());
                assertFalse(consumers.get(1).closed(), "the old refresh task closed the consumer of the new one");
            } finally {
                release.get(0).countDown();
                release.get(1).countDown();
                strategy.stop();
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> consumers.get(1).closed());
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    if (latch.await(10, TimeUnit.SECONDS)) {
                        return;
                    }
                    throw new IllegalStateException("not released");
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

    @SuppressWarnings("unchecked")
    private static List<ExecutorService> executorsOf(CamelContext context) throws Exception {
        Class<?> type = context.getExecutorServiceManager().getClass();
        while (type.getSuperclass() != null) {
            try {
                Field field = type.getDeclaredField("executorServices");
                field.setAccessible(true);
                return (List<ExecutorService>) field.get(context.getExecutorServiceManager());
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("executorServices");
    }

    @Test
    void testRecordThatCannotBeDeserializedIsSkipped() {
        TestDeserializableAdapter adapter = new TestDeserializableAdapter();
        SingleNodeKafkaResumeStrategy strategy = new SingleNodeKafkaResumeStrategy() {
            private int polls;

            @Override
            protected ConsumerRecords<byte[], byte[]> consume(Consumer<byte[], byte[]> consumer) {
                if (polls++ > 0) {
                    // ends the polling loop, as stop() does
                    throw new WakeupException();
                }
                byte[] data = "data".getBytes(StandardCharsets.UTF_8);
                return new ConsumerRecords<>(
                        Map.of(new TopicPartition("test-topic", 0),
                                List.of(new ConsumerRecord<>("test-topic", 0, 0L, null, data),
                                        // a tombstone: Camel does not write them, so it is skipped
                                        new ConsumerRecord<>("test-topic", 0, 1L, data, null),
                                        new ConsumerRecord<>("test-topic", 0, 2L, data, data))),
                        Map.of());
            }
        };
        strategy.setAdapter(adapter);

        assertThrows(WakeupException.class, () -> strategy.poll(null, new CountDownLatch(1)));
        // the record without a key and the tombstone are skipped, the next record is still loaded
        assertEquals(1, adapter.deserialized.get());
    }

    static class SubscriptionRecordingStrategy extends SingleNodeKafkaResumeStrategy {
        final List<Set<String>> subscriptions = new CopyOnWriteArrayList<>();

        SubscriptionRecordingStrategy(KafkaResumeStrategyConfiguration config) {
            super(config);
        }

        @Override
        protected void checkAndSubscribe(Consumer<byte[], byte[]> consumer, String topic) {
            super.checkAndSubscribe(consumer, topic);
            subscriptions.add(new HashSet<>(consumer.subscription()));
        }
    }

    static class TestDeserializableAdapter implements ResumeAdapter, Deserializable {
        final AtomicInteger deserialized = new AtomicInteger();

        @Override
        public void resume() {
        }

        @Override
        public boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
            deserialized.incrementAndGet();
            return true;
        }
    }

    static class TestAdapter implements ResumeAdapter {
        @Override
        public void resume() {
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
