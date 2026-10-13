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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ExpiredIteratorException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The position of the consumer in a shard when it needs a new shard iterator: after the cached iterator expired (they
 * are valid for 5 minutes), and for the shards created by resharding. The Kinesis client is a small in-memory stream.
 */
class KinesisConsumerPositionTest {

    private final CamelContext context = new DefaultCamelContext();
    private final FakeStream stream = new FakeStream();
    private final List<String> routed = new CopyOnWriteArrayList<>();

    private Kinesis2Consumer consumer;

    @AfterEach
    void stop() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @Test
    void expiredIteratorContinuesAfterTheLastRoutedRecord() throws Exception {
        stream.addShard("shard-0", null);
        stream.put("shard-0", "a");
        stream.put("shard-0", "b");
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1);

        assertEquals(2, consumer.poll());
        assertEquals(List.of("a", "b"), routed);

        // the route was suspended, or the delay is longer than the 5 minutes an iterator is valid
        stream.expireIterators();
        stream.put("shard-0", "c");

        assertEquals(1, consumer.poll(), "only the record that was not routed yet");
        assertEquals(List.of("a", "b", "c"), routed, "records routed over both polls");
        GetShardIteratorRequest last = stream.iteratorRequests.get(stream.iteratorRequests.size() - 1);
        assertEquals(ShardIteratorType.AFTER_SEQUENCE_NUMBER, last.shardIteratorType());
        assertEquals("2", last.startingSequenceNumber());
    }

    @Test
    void expiredIteratorDoesNotSkipTheRecordsOfTheGapWithLatest() throws Exception {
        stream.addShard("shard-0", null);
        stream.put("shard-0", "old");
        startConsumer(ShardIteratorType.LATEST, 1);

        assertEquals(0, consumer.poll());
        stream.put("shard-0", "a");
        assertEquals(1, consumer.poll());

        stream.expireIterators();
        stream.put("shard-0", "b");

        assertEquals(1, consumer.poll(), "the record stored while the iterator expired");
        assertEquals(List.of("a", "b"), routed);
    }

    @Test
    void expiredIteratorOfAShardWithoutRoutedRecordsUsesTheConfiguredType() throws Exception {
        stream.addShard("shard-0", null);
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1);

        assertEquals(0, consumer.poll());
        stream.expireIterators();
        stream.put("shard-0", "a");

        assertEquals(1, consumer.poll());
        assertEquals(List.of("a"), routed);
        GetShardIteratorRequest last = stream.iteratorRequests.get(stream.iteratorRequests.size() - 1);
        assertEquals(ShardIteratorType.TRIM_HORIZON, last.shardIteratorType());
    }

    @Test
    void shardCreatedByReshardingIsReadFromItsStartWithLatest() throws Exception {
        stream.addShard("shard-0", null);
        startConsumer(ShardIteratorType.LATEST, 1);

        assertEquals(0, consumer.poll());
        stream.put("shard-0", "a");
        assertEquals(1, consumer.poll());

        // the shard is split: it is closed, and the new records go to the child shards
        stream.close("shard-0");
        stream.addShard("shard-1", "shard-0");
        stream.addShard("shard-2", "shard-0");
        stream.put("shard-1", "b");
        stream.put("shard-2", "c");
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == 3);

        consumer.poll();
        consumer.poll();
        List<String> sorted = new ArrayList<>(routed);
        Collections.sort(sorted);
        assertEquals(List.of("a", "b", "c"), sorted,
                "the records stored in the child shards before the consumer saw these shards");
    }

    @Test
    void shardsListedAtStartAreReadFromLatestAlsoWhenTheyAreChildShards() throws Exception {
        // an earlier resharding: parent and children are all in the first shard list
        stream.addShard("shard-0", null);
        stream.put("shard-0", "old-0");
        stream.close("shard-0");
        stream.addShard("shard-1", "shard-0");
        stream.put("shard-1", "old-1");
        startConsumer(ShardIteratorType.LATEST, 2);

        assertEquals(0, consumer.poll());
        stream.put("shard-1", "new");
        consumer.poll();
        assertEquals(List.of("new"), routed);
    }

    private void startConsumer(ShardIteratorType type, int shards) throws Exception {
        Kinesis2Component component = new Kinesis2Component(context);
        component.start();

        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setAmazonKinesisClient(stream.client());
        configuration.setIteratorType(type);
        configuration.setStreamName("stream");
        configuration.setMaxResultsPerRequest(10);
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
        // the test calls poll()
        consumer.setStartScheduler(false);
        consumer.start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == shards);
    }

    /**
     * Shards with records, and shard iterators that point between two records of a shard and can expire.
     */
    private static final class FakeStream {
        final List<GetShardIteratorRequest> iteratorRequests = new CopyOnWriteArrayList<>();
        private final List<Shard> shards = new ArrayList<>();
        private final Map<String, List<Record>> records = new HashMap<>();
        private final Set<String> closed = new HashSet<>();
        private final Map<String, String> iteratorShard = new HashMap<>();
        private final Map<String, Integer> iteratorPosition = new HashMap<>();
        private final Set<String> expired = new HashSet<>();
        private int iterators;

        synchronized void addShard(String shardId, String parentShardId) {
            shards.add(Shard.builder().shardId(shardId).parentShardId(parentShardId).build());
            records.put(shardId, new ArrayList<>());
        }

        synchronized void put(String shardId, String body) {
            List<Record> list = records.get(shardId);
            list.add(Record.builder().sequenceNumber(String.valueOf(list.size() + 1))
                    .data(SdkBytes.fromString(body, StandardCharsets.UTF_8)).build());
        }

        synchronized void close(String shardId) {
            closed.add(shardId);
        }

        synchronized void expireIterators() {
            expired.addAll(iteratorShard.keySet());
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
            return ListShardsResponse.builder().shards(new ArrayList<>(shards)).build();
        }

        private synchronized GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
            iteratorRequests.add(request);
            List<Record> list = records.get(request.shardId());
            int position;
            switch (request.shardIteratorType()) {
                case TRIM_HORIZON:
                    position = 0;
                    break;
                case LATEST:
                    position = list.size();
                    break;
                case AFTER_SEQUENCE_NUMBER:
                    position = Integer.parseInt(request.startingSequenceNumber());
                    break;
                default:
                    throw new IllegalArgumentException("Not expected in this test: " + request.shardIteratorType());
            }
            return GetShardIteratorResponse.builder().shardIterator(newIterator(request.shardId(), position)).build();
        }

        private synchronized GetRecordsResponse getRecords(GetRecordsRequest request) {
            String iterator = request.shardIterator();
            if (expired.contains(iterator)) {
                throw ExpiredIteratorException.builder().message("Iterator expired").build();
            }
            String shardId = iteratorShard.get(iterator);
            List<Record> list = records.get(shardId);
            int from = iteratorPosition.get(iterator);
            int to = Math.min(list.size(), from + request.limit());
            String next = closed.contains(shardId) && to == list.size() ? null : newIterator(shardId, to);
            return GetRecordsResponse.builder().records(new ArrayList<>(list.subList(from, to))).nextShardIterator(next)
                    .build();
        }

        private String newIterator(String shardId, int position) {
            String iterator = "iterator-" + (++iterators);
            iteratorShard.put(iterator, shardId);
            iteratorPosition.put(iterator, position);
            return iterator;
        }
    }
}
