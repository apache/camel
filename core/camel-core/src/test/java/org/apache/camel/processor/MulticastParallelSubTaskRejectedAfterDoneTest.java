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
package org.apache.camel.processor;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a sub-exchange task rejected by the thread pool after the parallel EIP has already completed (here by
 * stopOnException) does not change the exchange that was handed back to the caller.
 * <p/>
 * The EIP task is busy with the streaming iterator while the first sub-exchange fails and completes the EIP. The thread
 * pool is then shut down, so the submission of the next sub-exchange task is rejected.
 */
@Timeout(30)
class MulticastParallelSubTaskRejectedAfterDoneTest extends ContextTestSupport {

    private final CountDownLatch iteratorBlocked = new CountDownLatch(1);
    private final CountDownLatch iteratorRelease = new CountDownLatch(1);
    private ExecutorService pool;

    @Test
    void testRejectedAfterStopOnException() throws Exception {
        Future<Exchange> future = template.asyncSend("direct:start", e -> e.getIn().setBody(new BlockingIterator()));

        // the first sub-exchange fails, which completes the split while its task is blocked in the iterator
        Exchange out = future.get(10, TimeUnit.SECONDS);
        assertEquals(0, iteratorBlocked.getCount());
        assertInstanceOf(CamelExchangeException.class, out.getException());

        // let the task continue, its submission of the last sub-exchange task is rejected,
        // and wait until it is finished
        pool.shutdown();
        iteratorRelease.countDown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // the late rejection must not replace the exception of the exchange the caller already has
        assertInstanceOf(CamelExchangeException.class, out.getException());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @AfterEach
    void shutdownPool() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        pool = Executors.newCachedThreadPool();
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).streaming().parallelProcessing().stopOnException().executorService(pool)
                        .process(e -> {
                            // only fail once the task is blocked in the iterator
                            iteratorBlocked.await(10, TimeUnit.SECONDS);
                            throw new IllegalArgumentException("Forced");
                        })
                        .end();
            }
        };
    }

    /**
     * Iterates over two elements, and blocks when the end is reached until the test releases it.
     */
    private final class BlockingIterator implements Iterator<String> {

        private final Iterator<String> delegate = List.of("a", "b").iterator();
        private final AtomicBoolean blocked = new AtomicBoolean();

        @Override
        public boolean hasNext() {
            if (!delegate.hasNext() && blocked.compareAndSet(false, true)) {
                iteratorBlocked.countDown();
                try {
                    iteratorRelease.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeCamelException(e);
                }
            }
            return delegate.hasNext();
        }

        @Override
        public String next() {
            return delegate.next();
        }
    }
}
