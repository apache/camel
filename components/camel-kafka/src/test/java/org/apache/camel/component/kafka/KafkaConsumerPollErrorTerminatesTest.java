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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Kafka client hands only a RuntimeException of a deserializer to the consumer as a RecordDeserializationException:
 * an Error, such as the ExceptionInInitializerError of a generated protobuf class that cannot be initialised, is thrown
 * out of poll as is. The consumer must then not stop silently: it terminates, closes the Kafka consumer and reports the
 * error in its health check. Uses a {@link MockConsumer} instead of a broker.
 */
class KafkaConsumerPollErrorTerminatesTest extends CamelTestSupport {

    private static final String TOPIC = "poll-error-terminates";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final ErrorThrowingMockConsumer mockConsumer = new ErrorThrowingMockConsumer();
    private final List<String> routed = new CopyOnWriteArrayList<>();

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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test")
                        .routeId("kafka")
                        .process(exchange -> routed.add(exchange.getMessage().getBody(String.class)));
            }
        };
    }

    @Override
    protected void setupResources() {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
    }

    @Test
    void testErrorFromPollTerminatesTheConsumerVisibly() {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, null, "a"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.contains("a"));

        ExceptionInInitializerError error = new ExceptionInInitializerError(
                new IllegalStateException("Simulated failure initialising a generated class"));
        mockConsumer.pollError.set(error);
        await().atMost(20, TimeUnit.SECONDS).until(mockConsumer.closed::get);

        KafkaConsumer consumer = (KafkaConsumer) context.getRoute("kafka").getConsumer();
        List<TaskHealthState> states = consumer.healthStates();
        assertEquals(1, states.size());
        TaskHealthState state = states.get(0);
        assertTrue(state.isTerminated(), "The consumer should be terminated");
        assertFalse(state.isReady(), "The consumer should not be ready");
        assertFalse(state.isRecoverable(), "The consumer should not be recoverable");
        KafkaConsumerFatalException fatal = assertInstanceOf(KafkaConsumerFatalException.class, state.getLastError());
        assertSame(error, fatal.getCause());

        KafkaConsumerHealthCheck check = new KafkaConsumerHealthCheck(consumer, "kafka");
        check.setCamelContext(context);
        HealthCheck.Result result = check.call();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertTrue(result.getMessage().orElseThrow().contains("terminated the kafka consumer"),
                result.getMessage().orElseThrow());
        assertSame(fatal, result.getError().orElseThrow());
    }

    private static final class ErrorThrowingMockConsumer extends MockConsumer<Object, Object> {
        final AtomicReference<Error> pollError = new AtomicReference<>();
        final AtomicBoolean closed = new AtomicBoolean();

        ErrorThrowingMockConsumer() {
            super("earliest");
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            // as the Kafka client does with an Error that a deserializer throws while it parses the fetched records
            Error error = pollError.getAndSet(null);
            if (error != null) {
                throw error;
            }
            ConsumerRecords<Object, Object> records = super.poll(timeout);
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return records;
        }

        @Override
        public synchronized void close() {
            closed.set(true);
            // keep the mock usable: a stopped route closes the consumer
        }
    }
}
