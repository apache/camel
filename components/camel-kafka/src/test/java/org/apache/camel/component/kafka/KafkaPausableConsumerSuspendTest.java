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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * Suspending a route that uses the pausable EIP with a {@link KafkaConsumerListener}: the listener must not resume the
 * partitions that the suspend paused, with a {@link MockConsumer} instead of a broker.
 */
class KafkaPausableConsumerSuspendTest extends CamelTestSupport {

    private static final String TOPIC = "pausable-suspend";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final ParkingMockConsumer mockConsumer = new ParkingMockConsumer();
    private final ThrottlingExceptionRoutePolicy keepOpenPolicy
            = new ThrottlingExceptionRoutePolicy(1, 60000, 100, null, true);
    private final AtomicBoolean canContinue = new AtomicBoolean(true);
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
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void testSuspendedRouteConsumesNothing() throws Exception {
        startRoute(false, () -> {
        });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        addRecord("a");
        mock.assertIsSatisfied();

        KafkaConsumer consumer = kafkaConsumer();
        context.getRouteController().suspendRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        // the check of the pausable EIP holds, but the route is suspended
        mock.reset();
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
    void testResumedRouteWaitsForTheCheck() throws Exception {
        startRoute(false, () -> {
        });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        addRecord("a");
        mock.assertIsSatisfied();

        KafkaConsumer consumer = kafkaConsumer();
        context.getRouteController().suspendRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isKafkaPaused);

        // resuming the route does not override a failing check of the pausable EIP
        canContinue.set(false);
        context.getRouteController().resumeRoute("kafka");
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        addRecord("b");
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("b");
        canContinue.set(true);
        mock.assertIsSatisfied();
    }

    @Test
    void testStartedWithOpenCircuitConsumesNothing() throws Exception {
        // a record is on the topic when the route starts with the circuit open: the listener of the pausable EIP must
        // not resume the partition that the rebalance paused
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
                        .pausable(new KafkaConsumerListener(), o -> canContinue.get())
                        .to("mock:result");
            }
        });
        if (!keepOpen) {
            keepOpenPolicy.setKeepOpen(false);
        }
        context.start();
    }

    private KafkaConsumer kafkaConsumer() {
        return (KafkaConsumer) context.getRoute("kafka").getConsumer();
    }

    private void addRecord(String value) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, offset++, null, value));
    }

    private static final class ParkingMockConsumer extends MockConsumer<Object, Object> {

        ParkingMockConsumer() {
            super("earliest");
        }

        @Override
        public ConsumerRecords<Object, Object> poll(Duration timeout) {
            ConsumerRecords<Object, Object> records = super.poll(timeout);
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return records;
        }

        @Override
        public synchronized void close() {
            // keep the mock usable: a stopped route closes the consumer
        }
    }
}
