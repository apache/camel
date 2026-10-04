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
package org.apache.camel.component.kafka;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suspending and resuming a route with a Kafka consumer (route controller, JMX, route policies) against its record
 * fetcher thread, with a {@link MockConsumer} instead of a broker.
 */
class KafkaConsumerSuspendResumeTest extends CamelTestSupport {

    private static final String TOPIC = "suspend-resume";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final GatedMockConsumer mockConsumer = new GatedMockConsumer();
    private final ThrottlingExceptionRoutePolicy keepOpenPolicy
            = new ThrottlingExceptionRoutePolicy(1, 60000, 100, null, true);
    private long offset;

    @BindToRegistry("mockFactory")
    private final KafkaClientFactory factory = new KafkaClientFactory() {
        @Override
        public Producer getProducer(Properties kafkaProps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer getConsumer(Properties kafkaProps) {
            return mockConsumer;
        }

        @Override
        public String getBrokers(KafkaConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testResumeWhileThePauseIsApplied() throws Exception {
        startRoute(false);
        KafkaConsumer consumer = kafkaConsumer();

        // the fetcher thread is inside consumer.pause() when the route is resumed
        Gate gate = new Gate();
        mockConsumer.pauseGate = gate;
        context.getRouteController().suspendRoute("kafka");
        assertTrue(gate.entered.await(20, TimeUnit.SECONDS));
        context.getRouteController().resumeRoute("kafka");
        gate.proceed.countDown();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("b");
        addRecord("b");
        mock.assertIsSatisfied();
        assertFalse(consumer.isKafkaPaused());
    }

    @Test
    void testSuspendWhileTheResumeIsApplied() throws Exception {
        startRoute(false);
        KafkaConsumer consumer = kafkaConsumer();
        context.getRouteController().suspendRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        // the fetcher thread is inside consumer.resume() when the route is suspended again
        Gate gate = new Gate();
        mockConsumer.resumeGate = gate;
        context.getRouteController().resumeRoute("kafka");
        assertTrue(gate.entered.await(20, TimeUnit.SECONDS));
        context.getRouteController().suspendRoute("kafka");
        gate.proceed.countDown();

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(consumer.isKafkaPaused(), "The Kafka consumer must be paused while the route is suspended"));
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        addRecord("b");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("b");
        context.getRouteController().resumeRoute("kafka");
        mock.assertIsSatisfied();
    }

    @Test
    void testPollErrorWhileSuspended() throws Exception {
        startRoute(false);
        KafkaConsumer consumer = kafkaConsumer();
        context.getRouteController().suspendRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        // one poll fails while the route is suspended (handled by the default pollOnError=ERROR_HANDLER)
        mockConsumer.setPollException(new KafkaException("Simulated poll failure"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.pollErrors.get() == 1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("b");
        context.getRouteController().resumeRoute("kafka");
        addRecord("b");
        mock.assertIsSatisfied();
    }

    @Test
    void testStartedWithOpenCircuit() throws Exception {
        // keepOpen suspends the consumer when the route starts, before the fetcher thread runs
        startRoute(true);

        // the record is added by the poll after the one that assigns the partition
        long next = offset++;
        mockConsumer.schedulePollTask(() -> mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, next, null, "b")));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("b");
        keepOpenPolicy.setKeepOpen(false);
        mock.assertIsSatisfied();
    }

    @Test
    void testStartedWithOpenCircuitConsumesNothing() throws Exception {
        // a record is on the topic when the route starts with the circuit open: the poll that assigns the partition
        // must not return it
        long first = offset++;
        startRoute(true, () -> mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, first, null, "a")));
        KafkaConsumer consumer = kafkaConsumer();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("a");
        keepOpenPolicy.setKeepOpen(false);
        mock.assertIsSatisfied();
    }

    private void startRoute(boolean keepOpen) throws Exception {
        startRoute(keepOpen, () -> {
        });
    }

    private void startRoute(boolean keepOpen, Runnable afterAssignment) throws Exception {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(List.of(PARTITION));
            afterAssignment.run();
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test&pollTimeoutMs=10")
                        .routeId("kafka").routePolicy(keepOpenPolicy)
                        .to("mock:result");
            }
        });
        if (!keepOpen) {
            keepOpenPolicy.setKeepOpen(false);
        }
        context.start();
        if (!keepOpen) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("a");
            addRecord("a");
            mock.assertIsSatisfied();
            mock.reset();
        }
    }

    private KafkaConsumer kafkaConsumer() {
        return (KafkaConsumer) context.getRoute("kafka").getConsumer();
    }

    private void addRecord(String value) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, offset++, null, value));
    }

    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        void pass() {
            entered.countDown();
            try {
                proceed.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class GatedMockConsumer extends MockConsumer<Object, Object> {
        volatile Gate pauseGate;
        volatile Gate resumeGate;
        final AtomicInteger pollErrors = new AtomicInteger();

        GatedMockConsumer() {
            super("earliest");
        }

        @Override
        public ConsumerRecords<Object, Object> poll(Duration timeout) {
            ConsumerRecords<Object, Object> records;
            try {
                records = super.poll(timeout);
            } catch (KafkaException e) {
                pollErrors.incrementAndGet();
                throw e;
            }
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return records;
        }

        @Override
        public void pause(Collection<TopicPartition> partitions) {
            Gate gate = pauseGate;
            pauseGate = null;
            if (gate != null) {
                gate.pass();
            }
            super.pause(partitions);
        }

        @Override
        public void resume(Collection<TopicPartition> partitions) {
            Gate gate = resumeGate;
            resumeGate = null;
            if (gate != null) {
                gate.pass();
            }
            super.resume(partitions);
        }

        @Override
        public synchronized void close() {
            // keep the mock usable: a stopped route closes the consumer
        }
    }
}
