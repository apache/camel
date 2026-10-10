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
package org.apache.camel.component.sjms.batch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchConcurrentConsumerTest extends JmsTestSupport {

    private static final int SJMS_CONCURRENT_CONSUMERS = 3;
    private static final int SJMS_BATCH_SIZE = 5;
    private static final int TOTAL = 90;
    private static final String SJMS_FROMF_URI = "%s?batching=true&batchSize=%d&concurrentConsumers=%d";
    private static final String SJMS_QUEUE_NAME
            = "sjms:queue:batch.consumer.queue.BatchConcurrentConsumerTest";
    private static final String MOCK_RESULT = "mock:result";

    private final CyclicBarrier barrier = new CyclicBarrier(SJMS_CONCURRENT_CONSUMERS);
    private final AtomicInteger batchesSeen = new AtomicInteger();
    private final Set<String> workerThreads = ConcurrentHashMap.newKeySet();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();

    @Test
    public void testBatchConcurrentConsumer() {
        BatchTestHelper.sendMessagesWithText(template, SJMS_QUEUE_NAME, TOTAL, "Hello World! %d");

        Awaitility.await().atMost(30, TimeUnit.SECONDS)
                .until(() -> bodies.size() >= TOTAL);

        // exactly once: no loss, no duplicates
        assertEquals(TOTAL, bodies.size());
        assertEquals(TOTAL, new HashSet<>(bodies).size());

        // parallelism: N distinct worker threads handled batches
        assertEquals(SJMS_CONCURRENT_CONSUMERS, workerThreads.size(), workerThreads.toString());
        workerThreads.forEach(t -> assertTrue(t.contains("SjmsBatchConsumer")));

        // no batch exceeded batchSize
        batchSizes.forEach(s -> assertTrue(s > 0 && s <= SJMS_BATCH_SIZE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME, SJMS_BATCH_SIZE, SJMS_CONCURRENT_CONSUMERS)
                        .process(e -> {
                            // first N batches must be in-flight simultaneously
                            if (batchesSeen.incrementAndGet() <= SJMS_CONCURRENT_CONSUMERS) {
                                barrier.await(15, TimeUnit.SECONDS);
                            }
                            workerThreads.add(Thread.currentThread().getName());
                            List<Exchange> batch = BatchTestHelper.getBatchExchanges(e);
                            batchSizes.add(batch.size());
                            assertEquals(batch.size(),
                                    e.getIn().getHeader(SjmsConstants.SJMS_BATCH_SIZE_HEADER, Integer.class));
                            batch.forEach(x -> bodies.add(x.getIn().getBody(String.class)));
                        })
                        .to(MOCK_RESULT);
            }
        };
    }
}
