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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisConsumerOffsetProcessor;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategy;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategyConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.resume.ResumeActionAwareAdapter;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The position of the consumer in a shard when it needs a new shard iterator: after the cached iterator expired (they
 * are valid for 5 minutes), for the shards created by resharding, and from the offsets of a resume strategy. The
 * Kinesis client is a small in-memory stream.
 */
class KinesisConsumerPositionTest {

    private final CamelContext context = new DefaultCamelContext();
    private final FakeStream stream = new FakeStream();
    private final List<String> routed = new CopyOnWriteArrayList<>();
    private final MapResumeCache resumeCache = new MapResumeCache();

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

    @Test
    void resumeStrategyStartsEachShardAfterItsOwnSequenceNumber() throws Exception {
        stream.addShard("shard-0", null);
        stream.addShard("shard-1", null);
        for (String body : List.of("a0", "b0", "c0")) {
            stream.put("shard-0", body);
        }
        for (String body : List.of("a1", "b1", "c1")) {
            stream.put("shard-1", body);
        }
        // the positions stored by an earlier run
        resumeCache.add("shard-0", "1");
        resumeCache.add("shard-1", "2");
        startConsumer(ShardIteratorType.TRIM_HORIZON, 2, resumeStrategy());

        consumer.poll();
        List<String> sorted = new ArrayList<>(routed);
        Collections.sort(sorted);
        assertEquals(List.of("b0", "c0", "c1"), sorted, "each shard continues after its own sequence number");
    }

    @Test
    void resumeStrategyStartsANewConsumerAfterTheOffsetsOfTheResumableEip() throws Exception {
        stream.addShard("shard-0", null);
        stream.put("shard-0", "a");
        stream.put("shard-0", "b");
        ResumeStrategy resumeStrategy = resumeStrategy();
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1, resumeStrategy);
        assertEquals(2, consumer.poll());

        // the route is started again: a new consumer, the offsets of the routed records are in the resume cache
        consumer.stop();
        stream.put("shard-0", "c");
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1, resumeStrategy);
        assertEquals(1, consumer.poll());
        assertEquals(List.of("a", "b", "c"), routed);

        // the next new iterator of the shard resumes from the offset stored by this consumer
        stream.expireIterators();
        stream.put("shard-0", "d");
        assertEquals(1, consumer.poll());
        assertEquals(List.of("a", "b", "c", "d"), routed);
    }

    @Test
    void consumersOfOneResumeStrategyResumeOneAfterTheOther() throws Exception {
        // two routes with the same resume strategy bean share its adapter
        stream.addShard("shard-0", null);
        stream.put("shard-0", "a");
        BlockingAdapter adapter = new BlockingAdapter();
        KinesisResumeStrategy resumeStrategy = new KinesisResumeStrategy();
        resumeStrategy.setResumeStrategyConfiguration(
                KinesisResumeStrategyConfiguration.builder().withResumeCache(resumeCache).build());
        resumeStrategy.setAdapter(adapter);
        resumeStrategy.start();
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1, resumeStrategy);
        Kinesis2Consumer first = consumer;
        startConsumer(ShardIteratorType.TRIM_HORIZON, 1, resumeStrategy);
        Kinesis2Consumer second = consumer;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            adapter.blockFirstResume = true;
            Future<Integer> firstPoll = executor.submit(first::poll);
            assertTrue(adapter.firstResumeEntered.await(10, TimeUnit.SECONDS), "the first consumer resumes");
            Future<Integer> secondPoll = executor.submit(second::poll);

            assertEquals(1, firstPoll.get(10, TimeUnit.SECONDS));
            assertEquals(1, secondPoll.get(10, TimeUnit.SECONDS));
            assertEquals(1, adapter.actionSetDuringResume.getCount(),
                    "the second consumer set its resume action while the first one resumed with its own");
        } finally {
            executor.shutdownNow();
            first.stop();
        }
    }

    /**
     * An adapter that keeps its first resume running until another resume action is set, at most one second: the time
     * the second consumer has to set its action on the shared adapter if it does not wait for the first one.
     */
    private static final class BlockingAdapter implements ResumeActionAware {
        private final CountDownLatch firstResumeEntered = new CountDownLatch(1);
        private final CountDownLatch actionSetDuringResume = new CountDownLatch(1);
        private volatile boolean blockFirstResume;
        private volatile boolean resuming;

        @Override
        public void setResumeAction(ResumeAction resumeAction) {
            if (resuming) {
                actionSetDuringResume.countDown();
            }
        }

        @Override
        public void resume() {
            if (blockFirstResume && firstResumeEntered.getCount() > 0) {
                resuming = true;
                firstResumeEntered.countDown();
                try {
                    actionSetDuringResume.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    resuming = false;
                }
            }
        }
    }

    private ResumeStrategy resumeStrategy() {
        // as the resumable EIP and the route set it up: the strategy and the adapter of the consumer share the cache
        KinesisResumeStrategy resumeStrategy = new KinesisResumeStrategy();
        resumeStrategy.setResumeStrategyConfiguration(
                KinesisResumeStrategyConfiguration.builder().withResumeCache(resumeCache).build());
        ResumeActionAwareAdapter adapter = new ResumeActionAwareAdapter();
        adapter.setCache(resumeCache);
        resumeStrategy.setAdapter(adapter);
        resumeStrategy.start();
        return resumeStrategy;
    }

    private void startConsumer(ShardIteratorType type, int shards) throws Exception {
        startConsumer(type, shards, null);
    }

    private void startConsumer(ShardIteratorType type, int shards, ResumeStrategy resumeStrategy) throws Exception {
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
                if (resumeStrategy != null) {
                    // what the resumable EIP does when the exchange completes, with the offset of
                    // KinesisConsumerOffsetProcessor
                    try {
                        new KinesisConsumerOffsetProcessor().process(exchange);
                        resumeStrategy.updateLastOffset(exchange.getMessage().getHeader(Exchange.OFFSET, Resumable.class));
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                }
                callback.done(true);
                return true;
            }
        });
        consumer.setConnection(component.getConnection());
        consumer.setResumeStrategy(resumeStrategy);
        // the test calls poll()
        consumer.setStartScheduler(false);
        consumer.start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumer.getCurrentShardList().size() == shards);
    }

    /**
     * A resume cache that follows the contract of {@link ResumeCache#forEach} like camel-caffeine's CaffeineCache: the
     * entry is invalidated when the action returns true.
     */
    private static final class MapResumeCache implements ResumeCache<Object> {
        private final Map<Object, Object> entries = new ConcurrentHashMap<>();

        @Override
        public Object computeIfAbsent(Object key, Function<? super Object, ? super Object> mapping) {
            return entries.computeIfAbsent(key, mapping);
        }

        @Override
        public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ? super Object> remapping) {
            return entries.computeIfPresent(key, remapping);
        }

        @Override
        public boolean contains(Object key, Object entry) {
            return entry.equals(entries.get(key));
        }

        @Override
        public void add(Object key, Object offsetValue) {
            entries.put(key, offsetValue);
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public long capacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public <T> T get(Object key, Class<T> clazz) {
            return clazz.cast(entries.get(key));
        }

        @Override
        public Object get(Object key) {
            return entries.get(key);
        }

        @Override
        public void forEach(BiFunction<? super Object, ? super Object, Boolean> action) {
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (action.apply(entry.getKey(), entry.getValue())) {
                    entries.remove(entry.getKey());
                }
            }
        }
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
