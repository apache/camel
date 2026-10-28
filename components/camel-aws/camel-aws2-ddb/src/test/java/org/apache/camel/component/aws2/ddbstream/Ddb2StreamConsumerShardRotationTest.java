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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamResponse;
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
import software.amazon.awssdk.services.dynamodb.model.Stream;
import software.amazon.awssdk.services.dynamodb.model.StreamDescription;
import software.amazon.awssdk.services.dynamodb.model.StreamRecord;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsServiceClientConfiguration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DynamoDB closes the shards of a stream and creates child shards (shard rotation, about every four hours, and splits).
 * A closed shard keeps its records until they are read: the consumer must read every shard to its end before it moves
 * to the children, and the configured iterator type is only the start position of the consumer.
 */
class Ddb2StreamConsumerShardRotationTest extends CamelTestSupport {

    @BindToRegistry("ddbStreams")
    final FakeStreamsClient client = new FakeStreamsClient();

    final List<String> routed = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-ddbstream://table?amazonDynamoDbStreamsClient=#ddbStreams&streamIteratorType=FROM_START"
                     + "&maxResultsPerRequest=2&initialDelay=0&delay=10")
                        .routeId("start").autoStartup(false)
                        .to("direct:records");

                from("aws2-ddbstream://table?amazonDynamoDbStreamsClient=#ddbStreams&streamIteratorType=FROM_LATEST"
                     + "&maxResultsPerRequest=2&initialDelay=0&delay=10")
                        .routeId("latest").autoStartup(false)
                        .to("direct:records");

                from("direct:records")
                        .process(exchange -> routed.add(
                                exchange.getMessage().getHeader(Ddb2StreamConstants.SEQUENCE_NUMBER, String.class)));
            }
        };
    }

    @Test
    void closedShardWithChildrenIsReadToItsEndFromStart() throws Exception {
        // a stream of a few hours: shard-0 (5 records) rotated into shard-1, which is open
        client.shard("shard-0", null).put("1", "2", "3", "4", "5").close();
        client.shard("shard-1", "shard-0").put("6");

        context.getRouteController().startRoute("start");

        awaitRoutedAndNothingMore(List.of("1", "2", "3", "4", "5", "6"));
    }

    @Test
    void streamIsNotReadAgainWhenItsOnlyShardIsReadToItsEnd() throws Exception {
        // one partition: the only root shard is closed, its child is open
        client.shard("shard-0", null).put("1", "2").close();
        client.shard("shard-1", "shard-0").put("3");

        context.getRouteController().startRoute("start");

        awaitRoutedAndNothingMore(List.of("1", "2", "3"));
    }

    @Test
    void recordsOfTheChildOfTheOnlyShardAreRoutedFromLatest() throws Exception {
        FakeShard parent = client.shard("shard-0", null).put("1");

        context.getRouteController().startRoute("latest");
        // the consumer has its LATEST position in shard-0
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= 1);

        // shard rotation: shard-0 gets one more record and closes; the next writes go to the new shard-1, and the
        // first of them is written before the consumer has read shard-0 to its end
        FakeShard child = client.shard("shard-1", "shard-0");
        client.onEndOfShard = closed -> {
            if (closed == parent) {
                child.put("4");
            }
        };
        parent.put("2").close();

        awaitRoutedAndNothingMore(List.of("2", "4"));
        child.put("5");
        awaitRoutedAndNothingMore(List.of("2", "4", "5"));
    }

    @Test
    void childThatIsListedAfterItsParentWasReadToItsEndIsRead() throws Exception {
        FakeShard parent = client.shard("shard-0", null).put("1");

        context.getRouteController().startRoute("latest");
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= 1);

        // the shard closes, and DescribeStream does not list its child yet when the consumer reads its end
        parent.put("2").close();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.endsRead() >= 1);
        int describes = client.describeCalls();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.describeCalls() >= describes + 2);
        client.shard("shard-1", "shard-0").put("3", "4");

        awaitRoutedAndNothingMore(List.of("2", "3", "4"));
    }

    @Test
    void shardThatClosedWhileAnotherShardIsReadToItsEndIsReadToItsEnd() throws Exception {
        // two partitions
        FakeShard a = client.shard("shard-a", null);
        FakeShard b = client.shard("shard-b", null);

        context.getRouteController().startRoute("latest");
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= 2);

        // both shards rotate (the children are listed before their parents close, so that the consumer finds them
        // whenever it reads the end of a parent); shard-b has more records than one GetRecords response returns
        client.shard("shard-a1", "shard-a").put("8");
        client.shard("shard-b1", "shard-b").put("9");
        a.put("1").close();
        b.put("2", "3", "4", "5", "6", "7").close();

        awaitRoutedAndNothingMore(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
    }

    private void awaitRoutedAndNothingMore(List<String> expected) {
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expected.size(), routed.size(), "routed so far: " + shown()));
        // a few more polls of every shard: nothing is routed twice
        int requests = client.recordRequests();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.recordRequests() >= requests + 10);
        List<String> sorted = new ArrayList<>(routed);
        sorted.sort((x, y) -> Integer.compare(Integer.parseInt(x), Integer.parseInt(y)));
        assertEquals(expected, sorted, "each record routed once, in any order across shards: " + shown());
    }

    private List<String> shown() {
        List<String> copy = new ArrayList<>(routed);
        return copy.size() > 20 ? copy.subList(0, 20) : copy;
    }

    final class FakeShard {
        final String id;
        final String parentId;
        final List<Record> records = new ArrayList<>();
        volatile boolean closed;

        FakeShard(String id, String parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        FakeShard put(String... sequenceNumbers) {
            synchronized (client) {
                for (String sequenceNumber : sequenceNumbers) {
                    records.add(Record.builder().eventID("event-" + sequenceNumber).eventName(OperationType.INSERT)
                            .dynamodb(StreamRecord.builder().sequenceNumber(sequenceNumber).build()).build());
                }
            }
            return this;
        }

        FakeShard close() {
            closed = true;
            return this;
        }
    }

    /**
     * A stream whose shards have records; an iterator is a position in a shard. GetRecords on a closed shard that
     * reaches its end returns no next iterator.
     */
    final class FakeStreamsClient implements DynamoDbStreamsClient {
        private final Map<String, FakeShard> shards = new LinkedHashMap<>();
        private final Map<String, Object[]> iterators = new HashMap<>();
        volatile Consumer<FakeShard> onEndOfShard;
        private int recordRequests;
        private int describeCalls;
        private int endsRead;

        synchronized FakeShard shard(String id, String parentId) {
            FakeShard shard = new FakeShard(id, parentId);
            shards.put(id, shard);
            return shard;
        }

        synchronized int recordRequests() {
            return recordRequests;
        }

        synchronized int describeCalls() {
            return describeCalls;
        }

        synchronized int endsRead() {
            return endsRead;
        }

        @Override
        public ListStreamsResponse listStreams(ListStreamsRequest request) {
            return ListStreamsResponse.builder().streams(Stream.builder().streamArn("arn:stream").build()).build();
        }

        @Override
        public synchronized DescribeStreamResponse describeStream(DescribeStreamRequest request) {
            describeCalls++;
            List<Shard> answer = new ArrayList<>();
            for (FakeShard shard : shards.values()) {
                answer.add(Shard.builder().shardId(shard.id).parentShardId(shard.parentId)
                        .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("1")
                                // a closed shard has an ending sequence number
                                .endingSequenceNumber(shard.closed ? "1000" : null).build())
                        .build());
            }
            return DescribeStreamResponse.builder().streamDescription(StreamDescription.builder().shards(answer).build())
                    .build();
        }

        @Override
        public synchronized GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
            FakeShard shard = shards.get(request.shardId());
            int position;
            switch (request.shardIteratorType()) {
                case TRIM_HORIZON:
                    position = 0;
                    break;
                case LATEST:
                    position = shard.records.size();
                    break;
                default:
                    throw new IllegalArgumentException("Not expected in this test: " + request.shardIteratorType());
            }
            return GetShardIteratorResponse.builder().shardIterator(newIterator(shard, position)).build();
        }

        @Override
        public GetRecordsResponse getRecords(GetRecordsRequest request) {
            FakeShard endOfShard = null;
            GetRecordsResponse response;
            synchronized (this) {
                recordRequests++;
                Object[] iterator = iterators.get(request.shardIterator());
                FakeShard shard = (FakeShard) iterator[0];
                int from = (Integer) iterator[1];
                int to = Math.min(shard.records.size(), from + request.limit());
                String next = null;
                if (shard.closed && to == shard.records.size()) {
                    endOfShard = shard;
                    endsRead++;
                } else {
                    next = newIterator(shard, to);
                }
                response = GetRecordsResponse.builder().records(new ArrayList<>(shard.records.subList(from, to)))
                        .nextShardIterator(next).build();
            }
            Consumer<FakeShard> hook = onEndOfShard;
            if (endOfShard != null && hook != null) {
                hook.accept(endOfShard);
            }
            return response;
        }

        private String newIterator(FakeShard shard, int position) {
            String iterator = "iterator-" + (iterators.size() + 1);
            iterators.put(iterator, new Object[] { shard, position });
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
