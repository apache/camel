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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.KinesisConsumerSuspendedBatchTest.FakeStream;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The polling consumer reads the shards of a stream, and routes their records, on a thread pool that Camel manages (and
 * not on the common fork-join pool of the JVM), with at most maxConcurrentShards shards at the same time.
 */
class KinesisConsumerShardThreadsTest extends CamelTestSupport {

    private static final String READER_THREAD = "Kinesis_shard_reader";

    final FakeStream stream = new FakeStream();
    // the name of the thread that routed each record, by shard/sequence number
    final Map<String, String> threads = new ConcurrentHashMap<>();
    final List<String> routed = new CopyOnWriteArrayList<>();
    // the first record of each shard waits here, when set
    volatile CyclicBarrier firstRecordBarrier;

    @BindToRegistry("kinesis")
    final KinesisClient kinesisClient = stream.client();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "aws2-kinesis://stream?amazonKinesisClient=#kinesis&iteratorType=TRIM_HORIZON"
                             + "&initialDelay=0&delay=10&shardMonitorInterval=100";
                from(uri).routeId("default").autoStartup(false).to("direct:record");
                from(uri + "&maxConcurrentShards=2").routeId("two").autoStartup(false).to("direct:record");
                from(uri + "&maxConcurrentShards=1").routeId("one").autoStartup(false).to("direct:record");

                from("direct:record")
                        .process(exchange -> {
                            String record = exchange.getMessage().getHeader(Kinesis2Constants.SHARD_ID, String.class) + "/"
                                            + exchange.getMessage().getHeader(Kinesis2Constants.SEQUENCE_NUMBER,
                                                    String.class);
                            CyclicBarrier barrier = firstRecordBarrier;
                            if (barrier != null && record.endsWith("/1")) {
                                barrier.await(20, TimeUnit.SECONDS);
                            }
                            threads.put(record, Thread.currentThread().getName());
                            routed.add(record);
                        });
            }
        };
    }

    @Test
    void recordsAreRoutedOnTheShardReaderThreadsOfTheConsumer() throws Exception {
        addShards(4, 3);

        context.getRouteController().startRoute("default");
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.size() == 12);

        for (Map.Entry<String, String> entry : threads.entrySet()) {
            assertTrue(entry.getValue().contains(READER_THREAD) && entry.getValue().contains(context.getName()),
                    "record " + entry.getKey() + " routed on " + entry.getValue());
        }
    }

    @Test
    void maxConcurrentShardsLimitsTheShardsReadAtTheSameTime() throws Exception {
        addShards(4, 3);
        // the first record of a shard is routed only when another shard reaches its first record as well: two shards
        // are read at the same time
        firstRecordBarrier = new CyclicBarrier(2);

        context.getRouteController().startRoute("two");
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.size() == 12);

        Set<String> names = Set.copyOf(threads.values());
        assertEquals(2, names.size(), "two shard reader threads: " + names);
        for (String name : names) {
            assertTrue(name.contains(READER_THREAD), name);
        }
    }

    @Test
    void maxConcurrentShardsOfOneReadsTheShardsOnTheThreadOfTheConsumer() throws Exception {
        addShards(3, 2);

        context.getRouteController().startRoute("one");
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.size() == 6);

        Set<String> names = Set.copyOf(threads.values());
        assertEquals(1, names.size(), "one thread: " + names);
        assertTrue(names.iterator().next().contains("aws2-kinesis://stream"), names.toString());
        assertEquals(List.of("shard-0/1", "shard-0/2", "shard-1/1", "shard-1/2", "shard-2/1", "shard-2/2"), routed,
                "one shard after the other");
    }

    @Test
    void theShardReaderThreadsStopWithTheConsumer() throws Exception {
        addShards(2, 1);

        context.getRouteController().startRoute("default");
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.size() == 2);
        assertTrue(readerThreads().size() > 0, "the shard reader threads run");

        context.getRouteController().stopRoute("default");
        await().atMost(20, TimeUnit.SECONDS).until(() -> readerThreads().isEmpty());
    }

    private Set<String> readerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(name -> name.contains(READER_THREAD) && name.contains("(" + context.getName() + ")"))
                .collect(Collectors.toSet());
    }

    private void addShards(int shards, int records) {
        for (int i = 0; i < shards; i++) {
            stream.addShard("shard-" + i);
            stream.put("shard-" + i, records);
        }
    }
}
