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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaClientFactory;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With manual commits, a rebalance or a stop commits only the records that the route committed, not the last processed
 * record: the route may still hold the records it processed after that, for example in an aggregation group that is not
 * complete (CAMEL-26993). A {@link MockConsumer} instead of a broker.
 */
class KafkaManualCommitRevokeTest extends CamelTestSupport {

    private static final String TOPIC = "manual-commit-revoke";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final RevokingMockConsumer mockConsumer = new RevokingMockConsumer();

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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testRevokeCommitsOnlyWhatTheRouteCommitted(boolean async) throws Exception {
        startRouteAndConsumeThreeRecords(async);

        // the partition is revoked: the records b and c, which the route did not commit, are not committed
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of()));
        await().atMost(10, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().isEmpty());
        assertEquals(1, lastCommittedOffset());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testStopCommitsOnlyWhatTheRouteCommitted(boolean async) throws Exception {
        startRouteAndConsumeThreeRecords(async);

        // the route stops: the consumer unsubscribes, which revokes the partition
        context.getRouteController().stopRoute("kafka");
        assertTrue(mockConsumer.revokedOnUnsubscribe.get(), "the stop must revoke the partition");
        assertEquals(1, lastCommittedOffset());
    }

    private void startRouteAndConsumeThreeRecords(boolean async) throws Exception {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String factoryClass = async
                        ? DefaultKafkaManualAsyncCommitFactory.class.getName()
                        : DefaultKafkaManualCommitFactory.class.getName();
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                     + "&autoCommitEnable=false&allowManualCommit=true&kafkaManualCommitFactory=#class:" + factoryClass)
                        .routeId("kafka")
                        // the route commits the record a, and holds the records b and c (as an aggregator would)
                        .filter(body().isEqualTo("a"))
                        .process(KafkaManualCommitRevokeTest::commit)
                        .end()
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");

        await().atMost(10, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0, null, "a"));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1, null, "b"));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 2, null, "c"));

        MockEndpoint.assertIsSatisfied(context);
        // an asynchronous commit is done by the consumer thread before its next poll
        await().atMost(10, TimeUnit.SECONDS).until(() -> lastCommittedOffset() == 1);
    }

    private static void commit(Exchange exchange) {
        exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class).commit();
    }

    /**
     * The last offset committed on the partition, also when it is no longer assigned (unlike
     * {@link MockConsumer#committed(java.util.Set)}).
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

    /**
     * Records the commits (MockConsumer#commitSync calls commitAsync), and revokes the assigned partitions on
     * unsubscribe, as the Kafka consumer does.
     */
    private static final class RevokingMockConsumer extends MockConsumer<Object, Object> {
        private final List<Map<TopicPartition, OffsetAndMetadata>> commits = new CopyOnWriteArrayList<>();
        private final AtomicBoolean revokedOnUnsubscribe = new AtomicBoolean();

        RevokingMockConsumer() {
            super("earliest");
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            commits.add(Map.copyOf(offsets));
            super.commitAsync(offsets, callback);
        }

        @Override
        public synchronized void unsubscribe() {
            if (!assignment().isEmpty()) {
                rebalance(List.of());
                revokedOnUnsubscribe.set(true);
            }
            super.unsubscribe();
        }
    }
}
