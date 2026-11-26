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
package org.apache.camel.component.aws2.kinesis;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.ScheduledPollConsumerHealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.AccessDeniedException;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer reads the shards of the list of its shard monitor. While the monitor has not listed the shards
 * (ListShards fails, for example without the kinesis:ListShards permission or for a stream that does not exist), a poll
 * reads nothing: the consumer must not be seen ready and healthy then. The polls and the monitor runs are driven by the
 * test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KinesisConsumerShardListHealthTest {

    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);
    private final AtomicInteger listShardsCalls = new AtomicInteger();
    // what the next ListShards does: answers this response, or throws this exception
    private final AtomicReference<Object> listShards = new AtomicReference<>();
    // when set, ListShards waits for it before it answers
    private volatile CountDownLatch listShardsGate;
    private Kinesis2Consumer consumer;
    private ScheduledPollConsumerHealthCheck healthCheck;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @Test
    void readinessIsDownWithTheFailureUntilTheShardsAreListed() {
        AccessDeniedException denied = AccessDeniedException.builder()
                .message("User is not authorized to perform: kinesis:ListShards").statusCode(400).build();
        listShards.set(denied);
        startConsumer("");
        // the run of the monitor at start, then one run of the test
        await().atMost(10, TimeUnit.SECONDS).until(() -> listShardsCalls.get() >= 1);
        consumer.new ShardMonitor().run();

        consumer.run();
        consumer.run();

        HealthCheck.Result result = readiness();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertEquals(2L, consumer.getErrorCounter());
        Throwable error = result.getError().orElseThrow();
        assertInstanceOf(IllegalStateException.class, error);
        assertSame(denied, error.getCause());
        assertTrue(error.getMessage().contains("Cannot list the shards of stream orders"), error.getMessage());
        // a readiness check only (as for every polling consumer): liveness is not affected
        assertFalse(healthCheck.isLiveness());
        verify(kinesisClient, never()).getRecords(any(GetRecordsRequest.class));

        // ListShards succeeds again: the next poll reads the shard, and the consumer is ready
        listShards.set(ListShardsResponse.builder().shards(Shard.builder().shardId("s0").build()).build());
        consumer.new ShardMonitor().run();
        consumer.run();

        assertEquals(HealthCheck.State.UP, readiness().getState());
        assertEquals(0L, consumer.getErrorCounter());
        verify(kinesisClient).getRecords(any(GetRecordsRequest.class));
    }

    @Test
    void readinessIsDownWithTheFailureForAConfiguredShardId() {
        ResourceNotFoundException notFound = ResourceNotFoundException.builder()
                .message("Stream orders under account 123456789012 not found.").statusCode(400).build();
        listShards.set(notFound);
        startConsumer("s0");
        await().atMost(10, TimeUnit.SECONDS).until(() -> listShardsCalls.get() >= 1);
        consumer.new ShardMonitor().run();

        consumer.run();

        HealthCheck.Result result = readiness();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertSame(notFound, result.getError().orElseThrow().getCause());
        verify(kinesisClient, never()).getRecords(any(GetRecordsRequest.class));
    }

    @Test
    void notReadyWhileTheFirstListShardsIsInProgress() {
        // a slow first ListShards: the first poll comes before the monitor has listed the shards
        CountDownLatch listed = new CountDownLatch(1);
        listShardsGate = listed;
        listShards.set(ListShardsResponse.builder().shards(Shard.builder().shardId("s0").build()).build());
        startConsumer("");
        await().atMost(10, TimeUnit.SECONDS).until(() -> listShardsCalls.get() >= 1);

        consumer.run();

        assertEquals(HealthCheck.State.DOWN, readiness().getState());
        assertEquals(0L, consumer.getErrorCounter());

        listed.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getCurrentShardList().isEmpty());
        consumer.run();

        assertEquals(HealthCheck.State.UP, readiness().getState());
        verify(kinesisClient).getRecords(any(GetRecordsRequest.class));
    }

    private void startConsumer(String shardId) {
        when(kinesisClient.listShards(any(ListShardsRequest.class))).thenAnswer(invocation -> {
            listShardsCalls.incrementAndGet();
            CountDownLatch gate = listShardsGate;
            if (gate != null) {
                gate.await(10, TimeUnit.SECONDS);
            }
            Object next = listShards.get();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return next;
        });
        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("it").build());
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder()
                .nextShardIterator("next")
                .records(Record.builder().sequenceNumber("1").partitionKey("p")
                        .data(SdkBytes.fromString("x", StandardCharsets.UTF_8)).build())
                .build());

        component.start();
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(kinesisClient);
        configuration.setStreamName("orders");
        configuration.setShardId(shardId);
        configuration.setIteratorType(ShardIteratorType.TRIM_HORIZON);
        // the monitor runs once at start; the test runs it after that
        configuration.setShardMonitorInterval(TimeUnit.DAYS.toMillis(1));
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:orders", configuration, component);
        endpoint.start();
        consumer = new Kinesis2Consumer(endpoint, processor);
        consumer.setConnection(component.getConnection());
        // the test polls with run(), which updates the state the health check reads as a scheduled poll does
        consumer.setStartScheduler(false);
        consumer.start();
        healthCheck = new ScheduledPollConsumerHealthCheck(consumer, "consumer:orders");
    }

    private HealthCheck.Result readiness() {
        return healthCheck.call(Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS));
    }
}
