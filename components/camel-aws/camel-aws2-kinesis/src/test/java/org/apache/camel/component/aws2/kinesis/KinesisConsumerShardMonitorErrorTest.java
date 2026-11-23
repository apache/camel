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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shard monitor lists the shards at a fixed rate, and finds the shards created by a split or a merge. An
 * {@link Error} of one of its runs must not end the scheduled task (the executor would not run it again, and nothing
 * would be logged); a {@link VirtualMachineError} is thrown on and ends it, while the consumer goes on reading the
 * shards it knows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KinesisConsumerShardMonitorErrorTest {

    private static final Shard PARENT = Shard.builder().shardId("s0").build();
    private static final List<Shard> AFTER_SPLIT = List.of(PARENT,
            Shard.builder().shardId("s1").parentShardId("s0").build(),
            Shard.builder().shardId("s2").parentShardId("s0").build());

    @Mock
    private KinesisClient kinesisClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final Kinesis2Component component = new Kinesis2Component(context);
    private final AtomicInteger listShardsCalls = new AtomicInteger();
    private Kinesis2Consumer consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @Test
    void anErrorOfOneRunDoesNotStopTheMonitor() {
        when(kinesisClient.listShards(any(ListShardsRequest.class))).thenAnswer(invocation -> {
            int call = listShardsCalls.incrementAndGet();
            if (call == 1) {
                return ListShardsResponse.builder().shards(PARENT).build();
            }
            if (call == 2) {
                throw new NoClassDefFoundError("simulated");
            }
            // the stream was split after the failed run
            return ListShardsResponse.builder().shards(AFTER_SPLIT).build();
        });
        // the scheduled monitor, every 10 ms
        startConsumer(10);

        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 3);
        consumer.run();

        // the children are read from their first record
        verify(kinesisClient).getShardIterator(argThat((GetShardIteratorRequest r) -> "s1".equals(r.shardId())
                && r.shardIteratorType() == ShardIteratorType.TRIM_HORIZON));
        verify(kinesisClient).getShardIterator(argThat((GetShardIteratorRequest r) -> "s2".equals(r.shardId())
                && r.shardIteratorType() == ShardIteratorType.TRIM_HORIZON));
        assertEquals(0L, consumer.getErrorCounter());
    }

    @Test
    void aVirtualMachineErrorStopsOnlyTheMonitor() {
        OutOfMemoryError error = new OutOfMemoryError("simulated");
        when(kinesisClient.listShards(any(ListShardsRequest.class))).thenAnswer(invocation -> {
            listShardsCalls.incrementAndGet();
            return ListShardsResponse.builder().shards(PARENT).build();
        });
        // the monitor runs once at start; the test runs it after that
        startConsumer(TimeUnit.DAYS.toMillis(1));
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 1);
        ScheduledPollConsumerHealthCheck healthCheck = new ScheduledPollConsumerHealthCheck(consumer, "consumer:orders");
        consumer.run();
        assertEquals(HealthCheck.State.UP, readiness(healthCheck).getState());

        when(kinesisClient.listShards(any(ListShardsRequest.class))).thenThrow(error);
        // thrown on: the executor does not run the task again
        assertSame(error, assertThrows(OutOfMemoryError.class, () -> consumer.new ShardMonitor().run()));

        // the consumer goes on reading the shard it knows
        consumer.run();
        verify(kinesisClient, times(2)).getRecords(any(GetRecordsRequest.class));
        assertEquals(0L, consumer.getErrorCounter());
        assertEquals(HealthCheck.State.UP, readiness(healthCheck).getState());

        // a restart starts a new monitor
        when(kinesisClient.listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(AFTER_SPLIT).build());
        consumer.stop();
        consumer.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 3);
        consumer.run();
        assertEquals(HealthCheck.State.UP, readiness(healthCheck).getState());
    }

    private void startConsumer(long shardMonitorInterval) {
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
        configuration.setIteratorType(ShardIteratorType.LATEST);
        configuration.setShardMonitorInterval(shardMonitorInterval);
        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:orders", configuration, component);
        endpoint.start();
        consumer = new Kinesis2Consumer(endpoint, processor);
        consumer.setConnection(component.getConnection());
        // the test polls with run(), which updates the state the health check reads as a scheduled poll does
        consumer.setStartScheduler(false);
        consumer.start();
    }

    private static HealthCheck.Result readiness(ScheduledPollConsumerHealthCheck healthCheck) {
        return healthCheck.call(Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS));
    }
}
