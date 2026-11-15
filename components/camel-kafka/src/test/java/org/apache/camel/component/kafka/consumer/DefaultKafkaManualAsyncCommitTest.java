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
package org.apache.camel.component.kafka.consumer;

import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Processor;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaClientFactory;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * An asynchronous manual commit is done by the thread of the Kafka consumer, so it can be called from another thread,
 * such as the one of an aggregator. A {@link MockConsumer} instead of a broker, which, as a real Kafka consumer, can
 * only be used by the thread that polls it.
 */
class DefaultKafkaManualAsyncCommitTest extends CamelTestSupport {

    private static final String TOPIC = "async-manual-commit";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);
    private static final Processor COMMIT
            = e -> e.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class).commit();

    private final SingleThreadMockConsumer mockConsumer = new SingleThreadMockConsumer();

    @BindToRegistry("asyncCommitFactory")
    private final KafkaManualCommitFactory asyncCommitFactory = new DefaultKafkaManualAsyncCommitFactory();

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
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void testCommitAfterAggregator() throws Exception {
        startRoute(true, COMMIT);
        MockEndpoint mock = getMockEndpoint("mock:committed");
        mock.expectedBodiesReceived("b");

        sendTwoRecords();

        // the aggregator completes the group on its own thread, which commits the last record
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, committedOffset()));
    }

    @Test
    void testCommitInKafkaRoute() throws Exception {
        startRoute(false, COMMIT);
        MockEndpoint mock = getMockEndpoint("mock:committed");
        mock.expectedBodiesReceived("a", "b");

        sendTwoRecords();

        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, committedOffset()));
    }

    @Test
    void testCommitAfterRevokeIsNotCommitted() throws Exception {
        List<KafkaManualCommit> manualCommits = new CopyOnWriteArrayList<>();
        startRoute(false,
                e -> manualCommits.add(e.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class)));
        MockEndpoint mock = getMockEndpoint("mock:committed");
        mock.expectedBodiesReceived("a", "b");

        sendTwoRecords();
        MockEndpoint.assertIsSatisfied(context);

        // the partition is revoked: the last processed offset is committed (offset 2)
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of()));
        await().atMost(10, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().isEmpty());
        assertEquals(2, lastCommittedOffset());
        int commitsAtRevoke = mockConsumer.commits.size();

        // the route commits the first record after the revoke, from another thread than the consumer
        manualCommits.get(0).commit();

        // two polls later, the consumer thread has gone through the requested commits at least once
        int polls = mockConsumer.polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> mockConsumer.polls.get() >= polls + 2);

        // the commit is not made: it would move back the offset the new owner of the partition committed
        assertFalse(mockConsumer.commits.subList(commitsAtRevoke, mockConsumer.commits.size()).stream()
                .anyMatch(offsets -> offsets.containsKey(PARTITION)));
        assertEquals(2, lastCommittedOffset());
    }

    private void sendTwoRecords() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0, null, "a"));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1, null, "b"));
    }

    private long committedOffset() {
        OffsetAndMetadata committed = mockConsumer.committed(Set.of(PARTITION)).get(PARTITION);
        return committed != null ? committed.offset() : -1;
    }

    /**
     * The last offset committed on the partition, also when it is no longer assigned (unlike
     * {@link MockConsumer#committed(Set)}).
     */
    private long lastCommittedOffset() {
        long offset = -1;
        for (Map<TopicPartition, OffsetAndMetadata> offsets : mockConsumer.commits) {
            OffsetAndMetadata committed = offsets.get(PARTITION);
            if (committed != null) {
                offset = committed.offset();
            }
        }
        return offset;
    }

    private void startRoute(boolean aggregate, Processor processor) throws Exception {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                             + "&autoCommitEnable=false&allowManualCommit=true&kafkaManualCommitFactory=#asyncCommitFactory";
                if (aggregate) {
                    from(uri).routeId("kafka")
                            .aggregate(constant(true), AggregationStrategies.useLatest()).completionSize(2)
                            .process(processor)
                            .to("mock:committed");
                } else {
                    from(uri).routeId("kafka")
                            .process(processor)
                            .to("mock:committed");
                }
            }
        });
        context.start();
    }

    /**
     * Fails a commit from another thread than the one that polls, as a real Kafka consumer fails it while that thread
     * is in poll.
     */
    private static final class SingleThreadMockConsumer extends MockConsumer<Object, Object> {
        private final List<Map<TopicPartition, OffsetAndMetadata>> commits = new CopyOnWriteArrayList<>();
        private final AtomicInteger polls = new AtomicInteger();
        private volatile Thread pollThread;

        SingleThreadMockConsumer() {
            super("earliest");
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            pollThread = Thread.currentThread();
            polls.incrementAndGet();
            return super.poll(timeout);
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            if (Thread.currentThread() != pollThread) {
                throw new ConcurrentModificationException("KafkaConsumer is not safe for multi-threaded access");
            }
            commits.add(Map.copyOf(offsets));
            super.commitAsync(offsets, callback);
        }
    }
}
