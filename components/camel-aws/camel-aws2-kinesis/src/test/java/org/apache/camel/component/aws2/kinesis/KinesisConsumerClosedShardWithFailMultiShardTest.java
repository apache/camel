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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * shardClosed=fail on a stream that was resharded: the closed parent shard stays in the shard list next to its child
 * shards. The closed shard is reported once to the exception handler of the consumer, and the polls go on reading the
 * child shards.
 */
class KinesisConsumerClosedShardWithFailMultiShardTest {

    private final CamelContext context = new DefaultCamelContext();
    private final List<String> routed = new CopyOnWriteArrayList<>();
    private final List<Throwable> handled = new CopyOnWriteArrayList<>();

    // set by a test to make GetRecords of child-2 fail, after it released the latch
    private volatile boolean child2Fails;
    private final CountDownLatch child2Failed = new CountDownLatch(1);
    private final ProvisionedThroughputExceededException child2Failure
            = ProvisionedThroughputExceededException.builder().message("Rate exceeded for shard child-2").build();

    private Kinesis2Consumer consumer;

    @AfterEach
    void stop() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @BeforeEach
    void startConsumer() {
        // an earlier split: the parent is closed, and its records and those of its two child shards are still readable
        List<Shard> shards = List.of(
                Shard.builder().shardId("parent").build(),
                Shard.builder().shardId("child-1").parentShardId("parent").build(),
                Shard.builder().shardId("child-2").parentShardId("parent").build());

        KinesisClient client = Mockito.mock(KinesisClient.class);
        when(client.listShards(any(ListShardsRequest.class)))
                .thenReturn(ListShardsResponse.builder().shards(shards).build());
        when(client.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenAnswer(inv -> GetShardIteratorResponse.builder()
                        .shardIterator("iterator-" + inv.<GetShardIteratorRequest> getArgument(0).shardId()).build());
        when(client.getRecords(any(GetRecordsRequest.class)))
                .thenAnswer(inv -> getRecords(inv.getArgument(0)));

        Kinesis2Component component = new Kinesis2Component(context);
        component.start();

        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(client);
        configuration.setIteratorType(ShardIteratorType.TRIM_HORIZON);
        configuration.setShardClosed(Kinesis2ShardClosedStrategyEnum.fail);
        configuration.setStreamName("stream");
        configuration.setShardMonitorInterval(50);

        Kinesis2Endpoint endpoint = new Kinesis2Endpoint("aws2-kinesis:stream", configuration, component);
        endpoint.start();
        consumer = new Kinesis2Consumer(endpoint, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                routed.add(exchange.getMessage().getBody(String.class));
                callback.done(true);
                return true;
            }
        });
        consumer.setConnection(component.getConnection());
        consumer.setExceptionHandler(new RecordingExceptionHandler());
        // the test calls poll()
        consumer.setStartScheduler(false);
        consumer.start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 3);
    }

    private GetRecordsResponse getRecords(GetRecordsRequest request) throws InterruptedException {
        String iterator = request.shardIterator();
        switch (iterator) {
            case "iterator-parent":
                // the last records of the parent: no next iterator, the shard is closed
                return GetRecordsResponse.builder().records(record("parent-a"), record("parent-b"))
                        .nextShardIterator(null).build();
            case "iterator-child-1":
                if (child2Fails) {
                    // still reading when the read of child-2 has failed (at most 5 seconds when the shards are read
                    // one after the other on one thread)
                    child2Failed.await(5, TimeUnit.SECONDS);
                }
                return GetRecordsResponse.builder().records(record("child-1")).nextShardIterator(iterator).build();
            case "iterator-child-2":
                if (child2Fails) {
                    child2Failed.countDown();
                    throw child2Failure;
                }
                return GetRecordsResponse.builder().records(record("child-2")).nextShardIterator(iterator).build();
            default:
                throw new IllegalArgumentException("Unknown iterator " + iterator);
        }
    }

    private static Record record(String body) {
        return Record.builder().sequenceNumber("1").data(SdkBytes.fromString(body, StandardCharsets.UTF_8)).build();
    }

    @Test
    void closedParentShardIsReportedOnceAndTheChildShardsAreRead() throws Exception {
        assertEquals(4, consumer.poll(), "the last records of the parent and a record of each child");
        assertTrue(handled.isEmpty(), "the parent is not closed yet");
        assertTrue(consumer.isShardClosed("parent"));

        // the parent stays in the shard list: later polls do not fail, and go on with the child shards
        assertEquals(2, consumer.poll());
        assertEquals(2, consumer.poll());
        assertEquals(2, consumer.poll());

        assertEquals(1, handled.size(), "the closed shard is reported once");
        ReachedClosedStatusException e = assertInstanceOf(ReachedClosedStatusException.class, handled.get(0));
        assertEquals("parent", e.getShardId());
        assertEquals("stream", e.getStreamName());

        List<String> sorted = new ArrayList<>(routed);
        Collections.sort(sorted);
        assertEquals(List.of("child-1", "child-1", "child-1", "child-1", "child-2", "child-2", "child-2", "child-2",
                "parent-a", "parent-b"), sorted);
    }

    @Test
    void failedShardFailsThePollAfterTheOtherShardsAreRead() throws Exception {
        assertEquals(4, consumer.poll());

        child2Fails = true;
        Exception e = assertThrows(Exception.class, consumer::poll);

        assertSame(child2Failure, e, "the failure of the shard, not wrapped by the parallel stream");
        // when poll() fails, the other shard of the poll has been read and routed
        List<String> sorted = new ArrayList<>(routed);
        Collections.sort(sorted);
        assertEquals(List.of("child-1", "child-1", "child-2", "parent-a", "parent-b"), sorted);
        // the closed parent is reported, and is not the failure of the poll
        assertEquals(1, handled.size());
        assertInstanceOf(ReachedClosedStatusException.class, handled.get(0));

        child2Fails = false;
        assertEquals(2, consumer.poll(), "the next poll reads both child shards again");
        assertEquals(1, handled.size());
    }

    private final class RecordingExceptionHandler implements ExceptionHandler {
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
    }
}
