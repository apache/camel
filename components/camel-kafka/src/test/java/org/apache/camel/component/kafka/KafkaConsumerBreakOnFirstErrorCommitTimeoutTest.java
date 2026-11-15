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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.spi.ExceptionHandler;
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
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With breakOnFirstError and synchronous manual commits, the consumer commits the records before the failed one and
 * reconnects, so the failed record and the records after it in the same poll are polled again. The commitSync of that
 * break can fail: it throws a TimeoutException when the timeout expires first, and the commit may or may not have been
 * applied (KafkaConsumer#commitSync(Map, Duration)). The consumer must still poll the failed record again, and must not
 * go on after the records of the batch that it did not route. A {@link MockConsumer} on a log and committed offsets
 * replaces the broker: each Kafka consumer of the fetcher is a new instance that starts from the committed offset.
 */
class KafkaConsumerBreakOnFirstErrorCommitTimeoutTest extends CamelTestSupport {

    private static final String TOPIC = "break-commit-timeout";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    // the records of the partition (the broker): f is stored after c failed
    private final List<String> log = new CopyOnWriteArrayList<>(List.of("a", "b", "c", "d", "e"));
    // the committed offsets of the consumer group (the broker)
    private final Map<TopicPartition, OffsetAndMetadata> committed = new ConcurrentHashMap<>();
    private final List<String> routed = new CopyOnWriteArrayList<>();
    private final AtomicInteger consumersCreated = new AtomicInteger();
    private final AtomicInteger commitTimeouts = new AtomicInteger();
    private final AtomicBoolean failC = new AtomicBoolean(true);
    private final AtomicBoolean timeoutNextCommit = new AtomicBoolean();

    // the exception handler of the consumer
    private final List<Throwable> handled = new CopyOnWriteArrayList<>();
    @BindToRegistry("commitErrors")
    private final ExceptionHandler exceptionHandler = new ExceptionHandler() {
        @Override
        public void handleException(Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handled.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handled.add(exception);
        }
    };

    @BindToRegistry("logFactory")
    private final KafkaClientFactory factory = new KafkaClientFactory() {
        @Override
        public Producer getProducer(Properties kafkaProps) {
            throw new UnsupportedOperationException("The test has no producer");
        }

        @Override
        public Consumer getConsumer(Properties kafkaProps) {
            consumersCreated.incrementAndGet();
            LogMockConsumer consumer = new LogMockConsumer();
            // the group coordinator assigns the partition in the first poll
            consumer.schedulePollTask(() -> consumer.rebalance(List.of(PARTITION)));
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

    // ERROR_HANDLER (the default), DISCARD and RETRY go on polling with the same Kafka consumer after the exception;
    // RECONNECT is a control: it reconnects after any exception of the poll loop
    @ParameterizedTest
    @EnumSource(value = PollOnError.class, names = { "ERROR_HANDLER", "DISCARD", "RETRY", "RECONNECT" })
    void testFailedRecordIsPolledAgainWhenTheCommitOfTheBreakTimesOut(PollOnError pollOnError) throws Exception {
        startRoute(pollOnError);

        // f is the last record: once it is committed, the consumer has gone past every record of the log
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(6L, committedOffset(), "The consumer must commit every record of the log"));

        assertEquals(1, commitTimeouts.get(), "The commitSync of the break must have timed out");
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), routed,
                "The failed record and the records after it must be polled again, not skipped");
        assertTrue(consumersCreated.get() >= 2, "The break must create a new Kafka consumer");
    }

    @Test
    void testFailedCommitOfTheBreakIsReportedToTheExceptionHandler() throws Exception {
        // RECONNECT polls the failed record again also without the change: this test is about the report only
        startRoute(PollOnError.RECONNECT);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(6L, committedOffset(), "The consumer must commit every record of the log"));

        assertEquals(1, commitTimeouts.get(), "The commitSync of the break must have timed out");
        assertTrue(handled.stream().anyMatch(TimeoutException.class::isInstance),
                "The failed commit must be reported to the exception handler of the consumer, got " + handled);
    }

    private void startRoute(PollOnError pollOnError) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#logFactory"
                     + "&groupId=break-commit-timeout&pollTimeoutMs=10&autoOffsetReset=earliest"
                     + "&autoCommitEnable=false&breakOnFirstError=true&pollOnError=" + pollOnError
                     + "&exceptionHandler=#commitErrors"
                     + "&allowManualCommit=true&kafkaManualCommitFactory=#class:"
                     + "org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory")
                        .routeId("kafka")
                        .process(KafkaConsumerBreakOnFirstErrorCommitTimeoutTest.this::process)
                        .process(exchange -> exchange.getMessage()
                                .getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class).commit());
            }
        });
        context.start();
    }

    private void process(Exchange exchange) {
        String body = exchange.getMessage().getBody(String.class);
        if ("c".equals(body) && failC.compareAndSet(true, false)) {
            // the commit of the break times out, and a new record is stored after the failed one
            timeoutNextCommit.set(true);
            log.add("f");
            throw new IllegalStateException("Simulated failure of record " + body);
        }
        routed.add(body);
    }

    private long committedOffset() {
        OffsetAndMetadata offset = committed.get(PARTITION);
        return offset != null ? offset.offset() : -1;
    }

    /**
     * A Kafka consumer of the group: it polls the records of {@link #log} from its position, which a new consumer takes
     * from the committed offset of the group, or else from the beginning (autoOffsetReset=earliest).
     */
    private final class LogMockConsumer extends MockConsumer<Object, Object> {
        private final Set<TopicPartition> positioned = new HashSet<>();

        LogMockConsumer() {
            super("earliest");
            updateBeginningOffsets(Map.of(PARTITION, 0L));
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            updateEndOffsets(Map.of(PARTITION, (long) log.size()));
            // runs the poll tasks (the assignment), and sets the position of a newly assigned partition
            super.poll(timeout);
            for (TopicPartition partition : assignment()) {
                OffsetAndMetadata offset = committed.get(partition);
                if (positioned.add(partition) && offset != null) {
                    // a newly assigned partition starts from the committed offset of the group
                    super.seek(partition, offset.offset());
                }
            }
            Map<TopicPartition, List<ConsumerRecord<Object, Object>>> records = new HashMap<>();
            Map<TopicPartition, OffsetAndMetadata> nextOffsets = new HashMap<>();
            int end = log.size();
            for (TopicPartition partition : assignment()) {
                List<ConsumerRecord<Object, Object>> fetched = new ArrayList<>();
                for (long offset = position(partition); offset < end; offset++) {
                    fetched.add(new ConsumerRecord<>(TOPIC, 0, offset, null, log.get((int) offset)));
                }
                if (!fetched.isEmpty()) {
                    records.put(partition, fetched);
                    nextOffsets.put(partition, new OffsetAndMetadata(end));
                    super.seek(partition, end);
                }
            }
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return new ConsumerRecords<>(records, nextOffsets);
        }

        @Override
        public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
            if (timeoutNextCommit.compareAndSet(true, false)) {
                // as KafkaConsumer when the timeout expires before the commit completed (here it was not applied)
                commitTimeouts.incrementAndGet();
                throw new TimeoutException(
                        "Timeout of " + timeout.toMillis() + "ms expired before successfully committing offsets "
                                           + offsets);
            }
            super.commitSync(offsets, timeout);
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
}
