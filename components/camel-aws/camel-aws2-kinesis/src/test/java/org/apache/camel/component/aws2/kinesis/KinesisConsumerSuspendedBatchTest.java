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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.camel.BindToRegistry;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
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
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A route that is stopped or suspended while the polling consumer routes the records of a GetRecords response: the
 * consumer stops after the record it is routing (with the default shutdownRunningTask CompleteCurrentTaskOnly), and
 * after a resume it routes the records of that response that it did not route, once.
 */
class KinesisConsumerSuspendedBatchTest extends CamelTestSupport {

    static final String ROUTE_ID = "kinesis";

    final FakeStream stream = new FakeStream();

    // the records routed, as shard/sequence number
    final List<String> routed = new CopyOnWriteArrayList<>();
    // the record whose processor stops or suspends the route (once), as shard/sequence number
    volatile String interruptAtRecord;
    // the record whose processor waits until the consumer stops its batch, as shard/sequence number
    volatile String waitAtRecord;
    volatile boolean stopInsteadOfSuspend;

    private final AtomicBoolean requested = new AtomicBoolean();
    private final AtomicReference<Exception> controlFailure = new AtomicReference<>();
    private volatile Thread controller;

    @BindToRegistry("kinesis")
    final KinesisClient kinesisClient = stream.client();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-kinesis://stream?amazonKinesisClient=#kinesis&iteratorType=TRIM_HORIZON"
                     + "&initialDelay=0&delay=10&shardMonitorInterval=100")
                        .routeId(ROUTE_ID).autoStartup(false)
                        .process(exchange -> {
                            String shardId = exchange.getMessage().getHeader(Kinesis2Constants.SHARD_ID, String.class);
                            String sequenceNumber
                                    = exchange.getMessage().getHeader(Kinesis2Constants.SEQUENCE_NUMBER, String.class);
                            String record = shardId + "/" + sequenceNumber;
                            if (record.equals(interruptAtRecord)) {
                                stopOrSuspendRouteDuringThePoll();
                            } else if (record.equals(waitAtRecord)) {
                                awaitBatchEnded();
                            }
                            routed.add(record);
                        });
            }
        };
    }

    @Test
    void recordsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        stream.addShard("shard-0");
        stream.put("shard-0", 4);
        interruptAtRecord = "shard-0/2";

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteStatus(ServiceStatus.Suspended);
        assertEquals(List.of("shard-0/1", "shard-0/2"), routed,
                "the suspend ends the batch after the record that is being routed");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("shard-0/1", "shard-0/2", "shard-0/3", "shard-0/4"), routed,
                        "records routed after the resume"));

        // the consumer continues after the last record it routed
        GetShardIteratorRequest last = stream.lastIteratorRequest();
        assertEquals(ShardIteratorType.AFTER_SEQUENCE_NUMBER, last.shardIteratorType());
        assertEquals("2", last.startingSequenceNumber());
        assertNoMoreRecordsRouted(4);
    }

    @Test
    void recordsOfAResponseThatArrivesWhileSuspendingAreRoutedAfterTheResume() throws Exception {
        stream.addShard("shard-0");
        stream.put("shard-0", 2);
        // the route is suspended while the GetRecords request is in flight: none of its records is routed
        stream.onRecordsReturned = shardId -> stopOrSuspendRouteDuringThePoll();

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteStatus(ServiceStatus.Suspended);
        assertEquals(List.of(), routed, "nothing is routed by the suspending consumer");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("shard-0/1", "shard-0/2"), routed, "records routed after the resume"));

        // nothing was routed from the shard: the consumer starts at the first record that was not routed, and not
        // at the configured iterator type again
        GetShardIteratorRequest last = stream.lastIteratorRequest();
        assertEquals(ShardIteratorType.AT_SEQUENCE_NUMBER, last.shardIteratorType());
        assertEquals("1", last.startingSequenceNumber());
        assertNoMoreRecordsRouted(2);
    }

    @Test
    void closedShardWhoseLastResponseWasCutShortIsReadToItsEnd() throws Exception {
        stream.addShard("shard-0");
        stream.put("shard-0", 3);
        // the last response of the shard has no next shard iterator
        stream.close("shard-0");
        interruptAtRecord = "shard-0/1";

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteStatus(ServiceStatus.Suspended);
        Kinesis2Consumer consumer = (Kinesis2Consumer) context.getRoute(ROUTE_ID).getConsumer();
        assertFalse(consumer.isShardClosed("shard-0"), "records of the shard are not routed yet");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("shard-0/1", "shard-0/2", "shard-0/3"), routed,
                        "records routed after the resume"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.isShardClosed("shard-0"));
    }

    @Test
    void eachShardEndsItsBatchAtItsOwnRecordAndContinuesThereAfterTheResume() throws Exception {
        stream.addShard("shard-0");
        stream.addShard("shard-1");
        stream.put("shard-0", 5);
        stream.put("shard-1", 5);
        interruptAtRecord = "shard-0/2";
        // routed while the consumer is being suspended, if the shard is read at the same time as shard-0
        waitAtRecord = "shard-1/3";

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteStatus(ServiceStatus.Suspended);
        assertEquals(List.of("shard-0/1", "shard-0/2"), routedOf("shard-0"));
        assertTrue(routedOf("shard-1").size() <= 3, "shard-1 ends its batch too: " + routedOf("shard-1"));

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(List.of("shard-0/1", "shard-0/2", "shard-0/3", "shard-0/4", "shard-0/5"), routedOf("shard-0"));
            assertEquals(List.of("shard-1/1", "shard-1/2", "shard-1/3", "shard-1/4", "shard-1/5"), routedOf("shard-1"));
        });
        assertNoMoreRecordsRouted(10);
    }

    @Test
    void stopEndsThePollAfterTheRecordBeingRouted() throws Exception {
        stream.addShard("shard-0");
        // one response of 1000 records (maxResultsPerRequest)
        stream.put("shard-0", 1000);
        interruptAtRecord = "shard-0/2";
        stopInsteadOfSuspend = true;
        context.getShutdownStrategy().setTimeout(60);

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteStatus(ServiceStatus.Stopped);
        assertEquals(List.of("shard-0/1", "shard-0/2"), routed,
                "the stop ends the batch after the record that is being routed");
        assertFalse(context.getShutdownStrategy().hasTimeoutOccurred(), "the graceful stop did not time out");
    }

    List<String> routedOf(String shardId) {
        return routed.stream().filter(r -> r.startsWith(shardId + "/")).toList();
    }

    private void assertNoMoreRecordsRouted(int expected) {
        // a few more polls: nothing is routed twice
        int requests = stream.recordRequests();
        await().atMost(20, TimeUnit.SECONDS).until(() -> stream.recordRequests() >= requests + 3);
        assertEquals(expected, routed.size(), "records routed: " + routed);
    }

    /**
     * Called by the thread that routes a record: stops or suspends the route from another thread, as a route must not
     * stop itself, and returns when the stop has reached the consumer (it no longer routes the following records of the
     * batch).
     */
    private void stopOrSuspendRouteDuringThePoll() {
        if (!requested.compareAndSet(false, true)) {
            return;
        }
        controller = new Thread(() -> {
            try {
                if (stopInsteadOfSuspend) {
                    context.getRouteController().stopRoute(ROUTE_ID);
                } else {
                    context.getRouteController().suspendRoute(ROUTE_ID);
                }
            } catch (Exception e) {
                controlFailure.set(e);
            }
        }, "control-route");
        controller.start();
        awaitBatchEnded();
    }

    /**
     * Waits until the consumer no longer allows its batch to go on. An interrupt of the thread must not end this wait.
     */
    private void awaitBatchEnded() {
        Kinesis2Consumer consumer = (Kinesis2Consumer) context.getRoute(ROUTE_ID).getConsumer();
        CountDownLatch batchEnded = new CountDownLatch(1);
        Thread watcher = new Thread(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
            batchEnded.countDown();
        }, "watch-consumer");
        watcher.start();

        boolean interrupted = false;
        boolean ended = false;
        long remaining = TimeUnit.SECONDS.toNanos(30);
        long deadline = System.nanoTime() + remaining;
        while (!ended && remaining > 0) {
            try {
                ended = batchEnded.await(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            remaining = deadline - System.nanoTime();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    void awaitRouteStatus(ServiceStatus status) throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS).until(() -> controller != null);
        controller.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(controller.isAlive(), "the route stop or suspend has returned");
        assertNull(controlFailure.get());
        assertEquals(status, context.getRouteController().getRouteStatus(ROUTE_ID));
    }

    /**
     * Shards with records numbered from 1, and shard iterators that point between two records of a shard.
     */
    static final class FakeStream {
        volatile Consumer<String> onRecordsReturned;
        private final List<GetShardIteratorRequest> iteratorRequests = new CopyOnWriteArrayList<>();
        private final Map<String, List<Record>> records = new LinkedHashMap<>();
        private final Set<String> closed = new HashSet<>();
        private final Map<String, String> iteratorShard = new HashMap<>();
        private final Map<String, Integer> iteratorPosition = new HashMap<>();
        private int iterators;
        private int recordRequests;

        synchronized void addShard(String shardId) {
            records.put(shardId, new ArrayList<>());
        }

        synchronized void put(String shardId, int count) {
            List<Record> list = records.get(shardId);
            for (int i = 0; i < count; i++) {
                String sequenceNumber = String.valueOf(list.size() + 1);
                list.add(Record.builder().sequenceNumber(sequenceNumber).partitionKey("key")
                        .data(SdkBytes.fromString("record-" + sequenceNumber, StandardCharsets.UTF_8)).build());
            }
        }

        synchronized void close(String shardId) {
            closed.add(shardId);
        }

        synchronized int recordRequests() {
            return recordRequests;
        }

        GetShardIteratorRequest lastIteratorRequest() {
            return iteratorRequests.get(iteratorRequests.size() - 1);
        }

        KinesisClient client() {
            KinesisClient client = Mockito.mock(KinesisClient.class);
            when(client.listShards(any(ListShardsRequest.class)))
                    .thenAnswer(inv -> listShards());
            when(client.getShardIterator(any(GetShardIteratorRequest.class)))
                    .thenAnswer(inv -> getShardIterator(inv.getArgument(0)));
            when(client.getRecords(any(GetRecordsRequest.class)))
                    .thenAnswer(inv -> getRecords(inv.getArgument(0)));
            return client;
        }

        private synchronized ListShardsResponse listShards() {
            List<Shard> shards = new ArrayList<>();
            for (String shardId : records.keySet()) {
                shards.add(Shard.builder().shardId(shardId).build());
            }
            return ListShardsResponse.builder().shards(shards).build();
        }

        private synchronized GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
            iteratorRequests.add(request);
            List<Record> list = records.get(request.shardId());
            int position = switch (request.shardIteratorType()) {
                case TRIM_HORIZON -> 0;
                case LATEST -> list.size();
                case AT_SEQUENCE_NUMBER -> Integer.parseInt(request.startingSequenceNumber()) - 1;
                case AFTER_SEQUENCE_NUMBER -> Integer.parseInt(request.startingSequenceNumber());
                default -> throw new IllegalArgumentException("Not expected in this test: " + request.shardIteratorType());
            };
            return GetShardIteratorResponse.builder().shardIterator(newIterator(request.shardId(), position)).build();
        }

        private GetRecordsResponse getRecords(GetRecordsRequest request) {
            GetRecordsResponse response;
            String shardId;
            synchronized (this) {
                recordRequests++;
                String iterator = request.shardIterator();
                shardId = iteratorShard.get(iterator);
                List<Record> list = records.get(shardId);
                int from = iteratorPosition.get(iterator);
                int to = Math.min(list.size(), from + request.limit());
                String next = closed.contains(shardId) && to == list.size() ? null : newIterator(shardId, to);
                response = GetRecordsResponse.builder().records(new ArrayList<>(list.subList(from, to)))
                        .nextShardIterator(next).build();
            }
            Consumer<String> hook = onRecordsReturned;
            if (hook != null && !response.records().isEmpty()) {
                hook.accept(shardId);
            }
            return response;
        }

        private String newIterator(String shardId, int position) {
            String iterator = "iterator-" + (++iterators);
            iteratorShard.put(iterator, shardId);
            iteratorPosition.put(iterator, position);
            return iterator;
        }
    }
}
