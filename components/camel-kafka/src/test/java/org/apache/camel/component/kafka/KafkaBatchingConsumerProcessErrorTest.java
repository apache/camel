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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A batch whose processing throws (here the exception handler of the consumer throws) must still be cleared: the batch
 * is kept while the Kafka consumer is connected, and a full batch would refuse every record of the next polls. Uses a
 * {@link MockConsumer} instead of a broker.
 */
class KafkaBatchingConsumerProcessErrorTest extends CamelTestSupport {

    private static final String TOPIC = "batching-process-error";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final MockConsumer<Object, Object> mockConsumer = new MockConsumer<>("earliest") {
        @Override
        public synchronized void close() {
            // keep the mock usable: a stopped route closes the consumer
        }
    };
    private final List<String> routed = new CopyOnWriteArrayList<>();
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

    @BindToRegistry("throwingHandler")
    private final ExceptionHandler throwingHandler = new ExceptionHandler() {
        @Override
        public void handleException(Throwable exception) {
            throw new IllegalStateException("Simulated exception handler failure", exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handleException(exception);
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a batch of 2 records; with manual commit a failed batch is passed to the exception handler, which throws
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                     + "&batching=true&maxPollRecords=2&pollTimeoutMs=60000&allowManualCommit=true"
                     + "&exceptionHandler=#throwingHandler")
                        .routeId("kafka")
                        .process(exchange -> {
                            boolean fail = false;
                            for (Object record : exchange.getMessage().getBody(List.class)) {
                                String body = ((Exchange) record).getMessage().getBody(String.class);
                                routed.add(body);
                                fail |= body.startsWith("a");
                            }
                            if (fail) {
                                throw new IllegalArgumentException("Simulated processing failure");
                            }
                        });
            }
        };
    }

    @Override
    protected void setupResources() {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
    }

    @Test
    void testFailedBatchIsCleared() {
        addRecords("a", 2);
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.contains("a1"));

        addRecords("b", 2);
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.contains("b1"));

        assertEquals(List.of("a0", "a1", "b0", "b1"), routed);
    }

    private void addRecords(String prefix, int count) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        List<ConsumerRecord<Object, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ConsumerRecord<>(TOPIC, 0, offset++, null, prefix + i));
        }
        records.forEach(mockConsumer::addRecord);
    }
}
