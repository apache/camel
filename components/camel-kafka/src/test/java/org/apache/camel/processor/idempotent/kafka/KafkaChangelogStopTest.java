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
package org.apache.camel.processor.idempotent.kafka;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.keyvalue.kafka.KafkaKeyValueRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link KafkaChangelog#stop()} waits for the background thread to leave poll() before the consumer is
 * closed, as the Kafka consumer is not thread-safe, and that the background thread does not apply records once the
 * repository is stopping. The changelog starts as in the repositories (the read of the topic on start, then the
 * background thread) over a {@link MockConsumer}, and the test decides when the background thread leaves poll() or the
 * record handler.
 */
class KafkaChangelogStopTest {

    private static final String TOPIC = "test-topic";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final List<CountDownLatch> gates = new ArrayList<>();
    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        // a failed test must not leave a thread blocked
        gates.forEach(CountDownLatch::countDown);
        context.stop();
    }

    @Test
    void stopWaitsForThePollerToLeavePollAndClosesTheConsumerAndTheProducer() throws Exception {
        SingleThreadedConsumer<byte[]> consumer = new SingleThreadedConsumer<>();
        gates.add(consumer.leavePoll);
        MockProducer<String, byte[]> producer
                = new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        KafkaChangelog<byte[]> changelog = new KafkaChangelog<>(
                "KafkaKeyValueRepository", TOPIC, new Properties(), new Properties(), ByteArrayDeserializer.class,
                ByteArraySerializer.class, 10, false, record -> {
                });
        changelog.setProducer(producer);
        changelog.setConsumer(consumer);
        KafkaKeyValueRepository repository = new KafkaKeyValueRepository(TOPIC, "localhost:9092");
        setField(repository, "changelog", changelog);

        changelog.startWithClients(context, repository);
        assertTrue(consumer.inPoll.await(5, TimeUnit.SECONDS), "The background thread did not poll");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread stopper = new Thread(() -> {
            try {
                repository.stop();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "stopper");
        stopper.start();
        // stop() has returned, or it waits for the background thread, which is still in poll()
        await().atMost(5, TimeUnit.SECONDS).until(() -> returnedOrWaiting(stopper));
        consumer.leavePoll.countDown();
        stopper.join(10000);

        assertFalse(stopper.isAlive(), "stop() did not return");
        assertNull(failure.get(), () -> "stop() failed: " + failure.get());
        assertTrue(consumer.closed(), "The consumer was not closed");
        assertTrue(producer.closed(), "The producer was not closed");
    }

    @Test
    void pollerDoesNotApplyRecordsToTheCacheOfTheRestartedRepository() throws Exception {
        KafkaIdempotentRepository repository = new KafkaIdempotentRepository();
        repository.setTopic(TOPIC);
        Map<String, Object> cacheBeforeStop = new ConcurrentHashMap<>();
        setField(repository, "cache", cacheBeforeStop);
        Method addToCache = KafkaIdempotentRepository.class.getDeclaredMethod("addToCache", ConsumerRecord.class);
        addToCache.setAccessible(true);

        // two records written after the end offsets read on start: the background thread gets both in one poll
        MockConsumer<String, String> consumer = new MockConsumer<>("earliest");
        prepareTopic(consumer);
        consumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "key1", "add"));
        consumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "key2", "add"));

        CountDownLatch inHandler = new CountDownLatch(1);
        CountDownLatch leaveHandler = new CountDownLatch(1);
        gates.add(leaveHandler);
        AtomicReference<Thread> pollerThread = new AtomicReference<>();
        KafkaChangelog<String> changelog = new KafkaChangelog<>(
                "KafkaIdempotentRepository", TOPIC, new Properties(), new Properties(), StringDeserializer.class,
                StringSerializer.class, 10, false, record -> {
                    if ("key1".equals(record.key())) {
                        // the repository stops while the background thread applies the first record
                        pollerThread.set(Thread.currentThread());
                        inHandler.countDown();
                        awaitUninterruptibly(leaveHandler);
                    }
                    invoke(addToCache, repository, record);
                });
        changelog.setProducer(new MockProducer<>(true, null, new StringSerializer(), new StringSerializer()));
        changelog.setConsumer(consumer);
        setField(repository, "changelog", changelog);

        changelog.startWithClients(context, repository);
        assertTrue(inHandler.await(5, TimeUnit.SECONDS), "The background thread did not apply the first record");

        // stop the repository, and start it again: doStart() gives the repository a new cache
        Map<String, Object> cacheAfterRestart = new ConcurrentHashMap<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread restarter = new Thread(() -> {
            try {
                repository.stop();
                setField(repository, "cache", cacheAfterRestart);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "restarter");
        restarter.start();
        // the repository has restarted, or stop() waits for the background thread, which is still applying the record
        await().atMost(5, TimeUnit.SECONDS).until(() -> returnedOrWaiting(restarter));
        leaveHandler.countDown();
        restarter.join(10000);
        pollerThread.get().join(10000);

        assertFalse(restarter.isAlive(), "stop() did not return");
        assertFalse(pollerThread.get().isAlive(), "The background thread did not end");
        assertNull(failure.get(), () -> "stop() failed: " + failure.get());
        assertEquals(Map.of(), cacheAfterRestart,
                "The background thread of the stopped repository applied records to the cache after the restart");
        assertTrue(cacheBeforeStop.containsKey("key1"));
        assertFalse(cacheBeforeStop.containsKey("key2"), "The background thread applied a record after the stop");
    }

    /** One empty partition, assigned so that records can be added before the start. */
    private static void prepareTopic(MockConsumer<String, ?> consumer) {
        consumer.updatePartitions(TOPIC, List.of(new PartitionInfo(TOPIC, 0, null, null, null)));
        consumer.assign(List.of(PARTITION));
        consumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        consumer.updateEndOffsets(Map.of(PARTITION, 0L));
    }

    private static boolean returnedOrWaiting(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.TERMINATED || state == Thread.State.WAITING
                || state == Thread.State.TIMED_WAITING;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        // the stop() before the fix interrupts the background thread
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (latch.getCount() > 0 && System.nanoTime() < deadline) {
            try {
                latch.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void invoke(Method method, Object target, Object arg) {
        try {
            method.invoke(target, arg);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * A consumer that, like the KafkaConsumer, throws {@link ConcurrentModificationException} when a thread uses it
     * while another thread is in poll(). The first poll() does not return until {@link #leavePoll} is opened, as a
     * KafkaConsumer does not leave poll() at the moment wakeup() is called.
     */
    private static final class SingleThreadedConsumer<V> extends MockConsumer<String, V> {

        final CountDownLatch inPoll = new CountDownLatch(1);
        final CountDownLatch leavePoll = new CountDownLatch(1);
        private final AtomicReference<Thread> owner = new AtomicReference<>();

        SingleThreadedConsumer() {
            super("earliest");
            prepareTopic(this);
        }

        @Override
        public ConsumerRecords<String, V> poll(Duration timeout) {
            acquire();
            try {
                if (inPoll.getCount() > 0) {
                    inPoll.countDown();
                    awaitUninterruptibly(leavePoll);
                }
                return super.poll(timeout);
            } finally {
                owner.set(null);
            }
        }

        @Override
        public void close() {
            acquire();
            try {
                super.close();
            } finally {
                owner.set(null);
            }
        }

        private void acquire() {
            if (!owner.compareAndSet(null, Thread.currentThread())) {
                throw new ConcurrentModificationException("KafkaConsumer is not safe for multi-threaded access");
            }
        }
    }
}
