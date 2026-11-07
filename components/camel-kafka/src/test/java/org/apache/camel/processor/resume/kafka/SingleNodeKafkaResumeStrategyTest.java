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
                await().atMost(10, TimeUnit.SECONDS).until(() -> strategy.subscriptions.size() == 1);
                strategy.stop();

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
                                        new ConsumerRecord<>("test-topic", 0, 1L, data, data))),
                        Map.of());
            }
        };
        strategy.setAdapter(adapter);

        assertThrows(WakeupException.class, () -> strategy.poll(null, new CountDownLatch(1)));
        // the record without a key is skipped, the next record is still loaded
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
