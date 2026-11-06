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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.support.subcription.SubscribeAdapter;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With breakOnFirstError, a failed record is polled again even when the consumer is paused (suspended) in the same poll
 * loop iteration, for example by a {@link ThrottlingExceptionRoutePolicy} that opens its circuit on the failed
 * exchange. When the partition has no committed offset, the record is only polled again by a new Kafka consumer, which
 * starts from autoOffsetReset (earliest here). A {@link MockConsumer} on a simulated log and committed offsets replaces
 * the broker: each Kafka consumer of the fetcher is a new instance that starts from the committed offset of the group.
 */
class KafkaConsumerBreakOnFirstErrorPausedTest extends CamelTestSupport {

    private static final String TOPIC = "break-paused";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);
    // the group of MockConsumer.groupMetadata(), which the exactly-once producer checks
    private static final String GROUP = "dummy.group.id";
    private static final List<String> RECORDS = List.of("a", "b", "c");
    private static final String SYNC_MANUAL_COMMIT = "&allowManualCommit=true&kafkaManualCommitFactory=#class:"
                                                     + "org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory";

    // the committed offsets of the consumer group (the broker)
    private final Map<TopicPartition, OffsetAndMetadata> committed = new ConcurrentHashMap<>();
    private final AtomicInteger consumersCreated = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final List<String> failOnce = new ArrayList<>();
    private volatile CountDownLatch failingRecordEntered;
    private volatile CountDownLatch failingRecordProceed;
    private volatile TransactionalMockProducer producer;
    private volatile boolean assignPartitions;

    @BindToRegistry("logFactory")
    private final KafkaClientFactory factory = new KafkaClientFactory() {
        @Override
        public Producer getProducer(Properties kafkaProps) {
            producer = new TransactionalMockProducer();
            return producer;
        }

        @Override
        public Consumer getConsumer(Properties kafkaProps) {
            consumersCreated.incrementAndGet();
            LogMockConsumer consumer = new LogMockConsumer();
            if (!assignPartitions) {
                // the group coordinator assigns the partition in the first poll
                consumer.schedulePollTask(() -> consumer.rebalance(List.of(PARTITION)));
            }
            return consumer;
        }

        @Override
        public String getBrokers(KafkaConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void testCircuitBreakerOpenedByTheFailedRecord() throws Exception {
        // the policy suspends the consumer from onExchangeDone of the failed exchange, on the fetcher thread, so the
        // consumer is paused in the same iteration as the break; it resumes the consumer when the circuit half opens
        failOnce.add("a");
        startRoute(SYNC_MANUAL_COMMIT, true, false);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(3L, committedOffset(), "The processed records must be committed"));
    }

    @Test
    void testPauseRequestedWhileTheFailedRecordIsProcessed() throws Exception {
        failOnce.add("a");
        failingRecordEntered = new CountDownLatch(1);
        failingRecordProceed = new CountDownLatch(1);
        startRoute("", false, false);
        KafkaConsumer consumer = kafkaConsumer();

        // the consumer is suspended while the record that fails is in the route
        assertTrue(failingRecordEntered.await(20, TimeUnit.SECONDS));
        consumer.suspend();
        failingRecordProceed.countDown();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");
        consumer.resume();
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
    }

    @Test
    void testPauseRequestedWhileTheFailedRecordIsProcessedWithAssignedPartitions() throws Exception {
        // a custom subscribe adapter assigns the partition: no rebalance listener pauses the new Kafka consumer
        assignPartitions = true;
        context.getRegistry().bind(KafkaConstants.KAFKA_SUBSCRIBE_ADAPTER,
                (SubscribeAdapter) (consumer, listener, topicInfo) -> consumer.assign(List.of(PARTITION)));
        failOnce.add("a");
        failingRecordEntered = new CountDownLatch(1);
        failingRecordProceed = new CountDownLatch(1);
        startRoute("", false, false);
        KafkaConsumer consumer = kafkaConsumer();

        assertTrue(failingRecordEntered.await(20, TimeUnit.SECONDS));
        consumer.suspend();
        failingRecordProceed.countDown();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        // nothing is routed while the consumer is suspended
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("a", "b", "c");
        consumer.resume();
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
    }

    @Test
    void testCircuitBreakerOpenedByTheFailedRecordExactlyOnce() throws Exception {
        failOnce.add("a");
        startRoute("&allowManualCommit=true&exactlyOnce=true", true, true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
        // the output of each record and its source offset are committed in one transaction
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(3L, committedOffset(), "The source offsets must be committed"));
        assertEquals(List.of("a", "b", "c"), producer.committedValues());
    }

    @Test
    void testFailedRecordWithoutPause() throws Exception {
        // control: without a pause, the break reconnects and the failed record is polled again
        failOnce.add("a");
        startRoute("", false, false);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
        assertTrue(consumersCreated.get() >= 2, "The break must create a new Kafka consumer");
    }

    @Test
    void testCircuitBreakerOpenedOnPartitionWithCommittedOffset() throws Exception {
        // control: the record before the failed one is committed, so the failed record is polled again from the
        // committed offset of the partition
        failOnce.add("b");
        startRoute(SYNC_MANUAL_COMMIT, true, false);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(1, failures.get());
    }

    private void startRoute(String options, boolean circuitBreaker, boolean exactlyOnce) throws Exception {
        boolean manualCommit = options.contains("kafkaManualCommitFactory");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#logFactory"
                                             + "&groupId=" + GROUP + "&pollTimeoutMs=10&autoOffsetReset=earliest"
                                             + "&autoCommitEnable=false&breakOnFirstError=true" + options)
                        .routeId("kafka");
                if (circuitBreaker) {
                    // opens on the first failure and half opens after 100 millis
                    route.routePolicy(new ThrottlingExceptionRoutePolicy(1, 60000, 100, null, false));
                }
                route.process(KafkaConsumerBreakOnFirstErrorPausedTest.this::process);
                if (manualCommit) {
                    route.process(exchange -> exchange.getMessage()
                            .getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class).commit());
                }
                if (exactlyOnce) {
                    route.to("kafka:out?brokers=localhost:9092&kafkaClientFactory=#logFactory&exactlyOnce=true"
                             + "&transactionalId=break-paused-tx");
                }
                route.to("mock:result");
            }
        });
        context.start();
    }

    private void process(Exchange exchange) throws Exception {
        String body = exchange.getMessage().getBody(String.class);
        boolean fail;
        synchronized (failOnce) {
            fail = failOnce.remove(body);
        }
        if (fail) {
            CountDownLatch entered = failingRecordEntered;
            if (entered != null) {
                entered.countDown();
                if (!failingRecordProceed.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The test did not let the failing record proceed");
                }
            }
            failures.incrementAndGet();
            throw new IllegalStateException("Simulated failure of record " + body);
        }
    }

    private long committedOffset() {
        OffsetAndMetadata offset = committed.get(PARTITION);
        return offset != null ? offset.offset() : -1;
    }

    private KafkaConsumer kafkaConsumer() {
        return (KafkaConsumer) context.getRoute("kafka").getConsumer();
    }

    /**
     * A Kafka consumer of the group: it polls the records of {@link #RECORDS} from its position, which a new consumer
     * takes from the committed offset of the group, or else from the beginning (autoOffsetReset=earliest).
     */
    private final class LogMockConsumer extends MockConsumer<Object, Object> {

        LogMockConsumer() {
            super("earliest");
            updateBeginningOffsets(Map.of(PARTITION, 0L));
            updateEndOffsets(Map.of(PARTITION, (long) RECORDS.size()));
            if (!committed.isEmpty()) {
                // the committed offsets the new consumer starts from
                super.commitAsync(new HashMap<>(committed), null);
            }
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            // runs the poll tasks (the assignment), and sets the position of a newly assigned partition
            super.poll(timeout);
            Map<TopicPartition, List<ConsumerRecord<Object, Object>>> records = new HashMap<>();
            Map<TopicPartition, OffsetAndMetadata> nextOffsets = new HashMap<>();
            for (TopicPartition partition : assignment()) {
                if (paused().contains(partition)) {
                    continue;
                }
                List<ConsumerRecord<Object, Object>> fetched = new ArrayList<>();
                for (long offset = position(partition); offset < RECORDS.size(); offset++) {
                    fetched.add(new ConsumerRecord<>(TOPIC, 0, offset, null, RECORDS.get((int) offset)));
                }
                if (!fetched.isEmpty()) {
                    records.put(partition, fetched);
                    nextOffsets.put(partition, new OffsetAndMetadata(RECORDS.size()));
                    super.seek(partition, RECORDS.size());
                }
            }
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return new ConsumerRecords<>(records, nextOffsets);
        }

        @Override
        public synchronized void commitAsync(
                Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            super.commitAsync(offsets, callback);
            committed.putAll(offsets);
        }

        @Override
        public synchronized Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions) {
            // as the Kafka consumer: a partition without a committed offset maps to null
            Map<TopicPartition, OffsetAndMetadata> answer = new HashMap<>();
            for (TopicPartition partition : partitions) {
                answer.put(partition, committed.get(partition));
            }
            return answer;
        }
    }

    /**
     * A transactional producer: a committed transaction commits the source offsets sent to it for the group.
     */
    private final class TransactionalMockProducer extends MockProducer<Object, Object> {
        private Map<TopicPartition, OffsetAndMetadata> pendingOffsets;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        TransactionalMockProducer() {
            super(true, null, (Serializer) new StringSerializer(), (Serializer) new StringSerializer());
        }

        @Override
        public synchronized void sendOffsetsToTransaction(
                Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
            super.sendOffsetsToTransaction(offsets, groupMetadata);
            pendingOffsets = offsets;
        }

        @Override
        public synchronized void commitTransaction() {
            super.commitTransaction();
            if (pendingOffsets != null) {
                committed.putAll(pendingOffsets);
                pendingOffsets = null;
            }
        }

        @Override
        public synchronized void abortTransaction() {
            super.abortTransaction();
            pendingOffsets = null;
        }

        /**
         * The values of the records of the committed transactions.
         */
        synchronized List<Object> committedValues() {
            return history().stream().map(ProducerRecord::value).toList();
        }
    }
}
