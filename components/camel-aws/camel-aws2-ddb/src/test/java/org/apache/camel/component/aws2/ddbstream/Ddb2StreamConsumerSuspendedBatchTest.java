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
package org.apache.camel.component.aws2.ddbstream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.BindToRegistry;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
import software.amazon.awssdk.services.dynamodb.model.ExpiredIteratorException;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsRequest;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsResponse;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsRequest;
import software.amazon.awssdk.services.dynamodb.model.ListStreamsResponse;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.SequenceNumberRange;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.model.Stream;
import software.amazon.awssdk.services.dynamodb.model.StreamDescription;
import software.amazon.awssdk.services.dynamodb.model.StreamRecord;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsServiceClientConfiguration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route that is suspended while the consumer routes the records of a GetRecords response, and resumed: the records of
 * that response that were not routed must be routed after the resume. (A route that is stopped and started gets a new
 * consumer, which starts from the configured iterator type.)
 */
class Ddb2StreamConsumerSuspendedBatchTest extends CamelTestSupport {

    static final String ROUTE_ID = "ddbstream";
    static final String LATEST_ROUTE_ID = "ddbstream-latest";

    @BindToRegistry("ddbStreams")
    final FakeStreamsClient client = new FakeStreamsClient();

    final List<String> routed = new CopyOnWriteArrayList<>();
    // the sequence number of the record whose processor suspends the route (once)
    volatile String suspendAtRecord;
    private final AtomicBoolean suspendRequested = new AtomicBoolean();
    private final AtomicReference<Exception> suspendFailure = new AtomicReference<>();
    private volatile Thread suspender;
    // the route that the test starts, suspends and resumes
    private volatile String routeId = ROUTE_ID;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-ddbstream://table?amazonDynamoDbStreamsClient=#ddbStreams&streamIteratorType=FROM_START"
                     + "&initialDelay=0&delay=10")
                        .routeId(ROUTE_ID).autoStartup(false)
                        .to("direct:records");

                from("aws2-ddbstream://table?amazonDynamoDbStreamsClient=#ddbStreams&streamIteratorType=FROM_LATEST"
                     + "&initialDelay=0&delay=10")
                        .routeId(LATEST_ROUTE_ID).autoStartup(false)
                        .to("direct:records");

                from("direct:records")
                        .process(exchange -> {
                            String sequenceNumber
                                    = exchange.getMessage().getHeader(Ddb2StreamConstants.SEQUENCE_NUMBER, String.class);
                            if (sequenceNumber.equals(suspendAtRecord)) {
                                suspendRouteDuringThePoll();
                            }
                            routed.add(sequenceNumber);
                        });
            }
        };
    }

    @Test
    void recordsNotRoutedByASuspendingConsumerAreRoutedAfterTheResume() throws Exception {
        client.put("1", "2", "3", "4");
        suspendAtRecord = "2";

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteSuspended();
        assertEquals(List.of("1", "2"), routed, "the suspend ends the batch after the record that is being routed");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("1", "2", "3", "4"), routed, "records routed after the resume"));

        // the consumer continues after the last record it routed
        GetShardIteratorRequest last = client.iteratorRequests.get(client.iteratorRequests.size() - 1);
        assertEquals(ShardIteratorType.AFTER_SEQUENCE_NUMBER, last.shardIteratorType());
        assertEquals("2", last.sequenceNumber());
    }

    @Test
    void recordsOfAResponseThatArrivesWhileSuspendingAreRoutedAfterTheResume() throws Exception {
        client.put("1", "2");
        // the route is suspended while the GetRecords request is in flight: none of its records is routed
        client.onGetRecords = this::suspendRouteDuringThePoll;

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteSuspended();
        assertEquals(List.of(), routed, "nothing is routed by the suspending consumer");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("1", "2"), routed, "records routed after the resume"));
        assertEquals(1, client.iteratorRequests.size(), "the shard iterator of the request is used again");
    }

    @Test
    void laterRecordsAreRoutedOnceAfterABatchWasCutShort() throws Exception {
        client.put("1", "2", "3");
        suspendAtRecord = "1";

        context.getRouteController().startRoute(ROUTE_ID);
        awaitRouteSuspended();
        client.put("4");

        context.getRouteController().resumeRoute(ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of("1", "2", "3", "4"), routed, "records routed after the resume"));
        // a few more polls: nothing is routed twice
        int requests = client.recordRequests();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= requests + 3);
        assertEquals(List.of("1", "2", "3", "4"), routed);
    }

    @Test
    void firstRecordsOfAShardThatWereNotRoutedAreNotReplacedByTheWholeShardWhenTheIteratorExpires() throws Exception {
        routeId = LATEST_ROUTE_ID;
        // stored before the consumer starts: a FROM_LATEST consumer does not route them
        client.put("1", "2");

        context.getRouteController().startRoute(LATEST_ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= 1);
        // the first response with records arrives while the route is suspended: none of its records is routed, and
        // no record of the shard was routed before
        client.onRecordsReturned = this::suspendRouteDuringThePoll;
        client.put("3", "4");
        awaitRouteSuspended();
        assertEquals(List.of(), routed, "nothing is routed by the suspending consumer");

        // the route stays suspended for longer than a shard iterator is valid
        client.expireIterators();
        context.getRouteController().resumeRoute(LATEST_ROUTE_ID);
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.size() >= 2);
        // a few more polls: nothing else is routed
        int requests = client.recordRequests();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= requests + 3);
        assertEquals(List.of("3", "4"), routed, "the records that were not routed, and not the shard from its start");

        GetShardIteratorRequest last = client.iteratorRequests.get(client.iteratorRequests.size() - 1);
        assertEquals(ShardIteratorType.AT_SEQUENCE_NUMBER, last.shardIteratorType());
        assertEquals("3", last.sequenceNumber());
    }

    /**
     * Called by the thread that polls: suspends the route from another thread, as a route must not suspend itself, and
     * returns when the suspend has reached the consumer (it no longer routes the following records of the batch).
     */
    private void suspendRouteDuringThePoll() {
        if (!suspendRequested.compareAndSet(false, true)) {
            return;
        }
        String id = routeId;
        Ddb2StreamConsumer consumer = (Ddb2StreamConsumer) context.getRoute(id).getConsumer();
        suspender = new Thread(() -> {
            try {
                context.getRouteController().suspendRoute(id);
            } catch (Exception e) {
                suspendFailure.set(e);
            }
        }, "suspend-route");
        suspender.start();

        CountDownLatch batchEnded = new CountDownLatch(1);
        Thread watcher = new Thread(() -> {
            await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
            batchEnded.countDown();
        }, "watch-consumer");
        watcher.start();

        // an interrupt of the thread that polls must not end this wait
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

    void awaitRouteSuspended() throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS).until(() -> suspender != null);
        suspender.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(suspender.isAlive(), "the route suspend has returned");
        assertNull(suspendFailure.get());
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus(routeId));
    }

    /**
     * A stream with one shard. A shard iterator is a position between two records.
     */
    static final class FakeStreamsClient implements DynamoDbStreamsClient {
        final List<GetShardIteratorRequest> iteratorRequests = new CopyOnWriteArrayList<>();
        volatile Runnable onGetRecords;
        // as onGetRecords, for a response that has records
        volatile Runnable onRecordsReturned;
        private final List<Record> records = new ArrayList<>();
        private final Map<String, Integer> iterators = new HashMap<>();
        private final Set<String> expiredIterators = new HashSet<>();
        private int recordRequests;

        synchronized void put(String... sequenceNumbers) {
            for (String sequenceNumber : sequenceNumbers) {
                records.add(Record.builder().eventID("event-" + sequenceNumber).eventName(OperationType.INSERT)
                        .dynamodb(StreamRecord.builder().sequenceNumber(sequenceNumber).build()).build());
            }
        }

        synchronized int recordRequests() {
            return recordRequests;
        }

        /** The shard iterators given out so far are no longer valid. */
        synchronized void expireIterators() {
            expiredIterators.addAll(iterators.keySet());
        }

        @Override
        public ListStreamsResponse listStreams(ListStreamsRequest request) {
            return ListStreamsResponse.builder().streams(Stream.builder().streamArn("arn:stream").build()).build();
        }

        @Override
        public DescribeStreamResponse describeStream(DescribeStreamRequest request) {
            return DescribeStreamResponse.builder().streamDescription(
                    StreamDescription.builder().shards(Shard.builder().shardId("shard-0")
                            // an open shard: no ending sequence number
                            .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("1").build())
                            .build()).build())
                    .build();
        }

        @Override
        public synchronized GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
            iteratorRequests.add(request);
            int position;
            switch (request.shardIteratorType()) {
                case TRIM_HORIZON:
                    position = 0;
                    break;
                case LATEST:
                    position = records.size();
                    break;
                case AT_SEQUENCE_NUMBER:
                    position = Integer.parseInt(request.sequenceNumber()) - 1;
                    break;
                case AFTER_SEQUENCE_NUMBER:
                    position = Integer.parseInt(request.sequenceNumber());
                    break;
                default:
                    throw new IllegalArgumentException("Not expected in this test: " + request.shardIteratorType());
            }
            return GetShardIteratorResponse.builder().shardIterator(newIterator(position)).build();
        }

        @Override
        public GetRecordsResponse getRecords(GetRecordsRequest request) {
            GetRecordsResponse response;
            synchronized (this) {
                recordRequests++;
                if (expiredIterators.contains(request.shardIterator())) {
                    throw ExpiredIteratorException.builder().message("Iterator expired").build();
                }
                int from = iterators.get(request.shardIterator());
                int to = Math.min(records.size(), from + request.limit());
                response = GetRecordsResponse.builder().records(new ArrayList<>(records.subList(from, to)))
                        .nextShardIterator(newIterator(to)).build();
            }
            Runnable hook = onGetRecords;
            if (hook != null) {
                hook.run();
            }
            hook = onRecordsReturned;
            if (hook != null && response.hasRecords() && !response.records().isEmpty()) {
                hook.run();
            }
            return response;
        }

        private String newIterator(int position) {
            String iterator = "iterator-" + (iterators.size() + 1);
            iterators.put(iterator, position);
            return iterator;
        }

        @Override
        public DynamoDbStreamsServiceClientConfiguration serviceClientConfiguration() {
            return null;
        }

        @Override
        public String serviceName() {
            return DynamoDbStreamsClient.SERVICE_NAME;
        }

        @Override
        public void close() {
            // nothing to close
        }
    }
}
