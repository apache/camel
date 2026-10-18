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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping a route with a Kafka consumer (CAMEL-25753): a consumer waiting in poll is woken up at once instead of after
 * the poll timeout, and a consumer processing records finishes them first. A {@link MockConsumer} instead of a broker,
 * whose poll waits like a real one.
 */
class KafkaConsumerStopTest extends CamelTestSupport {

    private static final String TOPIC = "stop";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final BlockingMockConsumer mockConsumer = new BlockingMockConsumer();
    private final CountDownLatch processing = new CountDownLatch(1);

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
    void testStopWhileWaitingInPoll() throws Exception {
        startRoute(0);
        // the fetcher thread is in a poll that waits up to 10 seconds
        await().atMost(20, TimeUnit.SECONDS).until(mockConsumer::isWaiting);

        long start = System.nanoTime();
        context.getRouteController().stopRoute("kafka");
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(millis < 3000, "The route stopped after " + millis + " ms, it should not wait for the 10 s poll timeout");
    }

    @Test
    void testStopWhileProcessing() throws Exception {
        startRoute(1000);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0, null, "a"));
        assertTrue(processing.await(20, TimeUnit.SECONDS));

        // the record takes a second: the stop waits for it, and the wakeup does not interrupt it
        long start = System.nanoTime();
        context.getRouteController().stopRoute("kafka");
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        mock.assertIsSatisfied();
        assertTrue(millis >= 500, "The route stopped after " + millis + " ms, before the record was processed");
    }

    private void startRoute(long processingMillis) throws Exception {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test&pollTimeoutMs=10000")
                        .routeId("kafka")
                        .process(e -> {
                            processing.countDown();
                            Thread.sleep(processingMillis);
                        })
                        .to("mock:result");
            }
        });
        context.start();
    }

    /**
     * A poll that waits up to its timeout for records, as a real consumer on an idle topic, unless it is woken up. The
     * wait is outside the monitor of {@link MockConsumer}, whose poll and wakeup are synchronized.
     */
    private static final class BlockingMockConsumer extends MockConsumer<Object, Object> {
        private final Object signal = new Object();
        private boolean wokenUp;
        private boolean waiting;

        BlockingMockConsumer() {
            super("earliest");
        }

        @Override
        public ConsumerRecords<Object, Object> poll(Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                // throws a WakeupException when woken up
                ConsumerRecords<Object, Object> records = super.poll(Duration.ZERO);
                if (!records.isEmpty()) {
                    return records;
                }
                synchronized (signal) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        return records;
                    }
                    if (!wokenUp) {
                        waiting = true;
                        try {
                            signal.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return records;
                        } finally {
                            waiting = false;
                        }
                    }
                }
            }
        }

        @Override
        public void addRecord(ConsumerRecord<Object, Object> record) {
            super.addRecord(record);
            synchronized (signal) {
                signal.notifyAll();
            }
        }

        boolean isWaiting() {
            synchronized (signal) {
                return waiting;
            }
        }

        @Override
        public void wakeup() {
            super.wakeup();
            synchronized (signal) {
                wokenUp = true;
                signal.notifyAll();
            }
        }

        @Override
        public synchronized void close() {
            // keep the mock usable: a stopped route closes the consumer
        }
    }
}
