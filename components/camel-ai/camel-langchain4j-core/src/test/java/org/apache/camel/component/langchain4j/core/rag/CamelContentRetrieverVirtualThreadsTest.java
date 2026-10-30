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
package org.apache.camel.component.langchain4j.core.rag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.concurrent.ThreadType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * With virtual threads the Camel pool is one thread per task, bounded by its queue size only: the retriever must still
 * run at most {@code concurrency} calls and reject a call beyond {@code concurrency + queueCapacity} at once.
 */
@Isolated
public class CamelContentRetrieverVirtualThreadsTest extends CamelTestSupport {

    private static final String VIRTUAL_THREADS_PROPERTY = "camel.threads.virtual.enabled";
    private static String previousVirtualThreadsProperty;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private final AtomicBoolean virtual = new AtomicBoolean(true);

    @BeforeAll
    static void enableVirtualThreads() throws Exception {
        previousVirtualThreadsProperty = System.getProperty(VIRTUAL_THREADS_PROPERTY);
        System.setProperty(VIRTUAL_THREADS_PROPERTY, "true");
        resetThreadType();
    }

    @AfterAll
    static void restoreVirtualThreads() throws Exception {
        if (previousVirtualThreadsProperty == null) {
            System.clearProperty(VIRTUAL_THREADS_PROPERTY);
        } else {
            System.setProperty(VIRTUAL_THREADS_PROPERTY, previousVirtualThreadsProperty);
        }
        resetThreadType();
    }

    private static void resetThreadType() throws Exception {
        Field field = ThreadType.class.getDeclaredField("current");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:hold").process(e -> {
                    virtual.compareAndSet(true, Thread.currentThread().isVirtual());
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } finally {
                        running.decrementAndGet();
                    }
                }).setBody(constant("held"));
            }
        };
    }

    @Test
    public void boundsHoldWithVirtualThreads() throws Exception {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "hold", "direct:hold")
                .setConcurrency(2).setQueueCapacity(1);
        retriever.start();
        ExecutorService callers = Executors.newFixedThreadPool(3);
        try {
            List<Future<List<Content>>> admitted = new ArrayList<>();
            List<Thread> callerThreads = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 3; i++) {
                admitted.add(callers.submit(() -> {
                    callerThreads.add(Thread.currentThread());
                    return retriever.retrieve(Query.from("q"));
                }));
            }
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(running).hasValue(2));
            // every admitted caller holds its slot only once it waits on its result, timed by the retriever timeout
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(callerThreads)
                    .hasSize(3)
                    .allSatisfy(t -> assertThat(t.getState()).isEqualTo(Thread.State.TIMED_WAITING)));

            long start = System.nanoTime();
            assertThatThrownBy(() -> retriever.retrieve(Query.from("rejected")))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasMessageContaining("was rejected")
                    .hasRootCauseInstanceOf(RejectedExecutionException.class);
            // rejected at once, not after the keep-alive time of the pool
            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)).isLessThan(5);

            release.countDown();
            for (Future<List<Content>> call : admitted) {
                assertThat(call.get(5, TimeUnit.SECONDS)).hasSize(1);
            }
            assertThat(maxRunning).hasValue(2);
            assertThat(virtual).as("the route ran on virtual threads").isTrue();
        } finally {
            release.countDown();
            callers.shutdownNow();
            retriever.stop();
        }
    }
}
