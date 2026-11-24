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
package org.apache.camel.support.cache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncProducer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An acquire of a singleton producer that is evicted while the acquire is in progress must still return the producer it
 * acquired, and the producer must not be stopped until it is released. The test pauses the acquire in the
 * package-private hook {@code ServicePool.onSingleAcquired}, after the pool read and counted its producer.
 */
class SinglePoolAcquireEvictionTest extends ContextTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void testAcquireDoesNotReturnNullWhenEvictedMeanwhile() throws Exception {
        PausingServicePool pool = new PausingServicePool(1);
        Endpoint a = context.getEndpoint("seda:a");
        Endpoint b = context.getEndpoint("seda:b");
        AsyncProducer pa = pool.acquire(a);
        pool.release(a, pa);

        // the next acquire of pa pauses after the pool read it
        pool.pauseOn = pa;
        Future<AsyncProducer> acquired = executor.submit(() -> pool.acquire(a));
        assertTrue(pool.paused.await(10, TimeUnit.SECONDS));

        // acquiring b evicts pa while the other thread is acquiring it
        AsyncProducer pb = pool.acquire(b);
        pool.proceed.countDown();
        AsyncProducer got = acquired.get(10, TimeUnit.SECONDS);
        assertNotNull(got, "Acquire should not return null while the pool is started");
        assertSame(pa, got);
        assertTrue(ServiceHelper.isStarted(pa), "The evicted producer should not be stopped while it is in use");

        pool.release(a, got);
        assertTrue(ServiceHelper.isStopped(pa), "The evicted producer should be stopped when it is released");
        pool.release(b, pb);
        pool.stop();
    }

    private static final class PausingServicePool extends ProducerServicePool {
        final CountDownLatch paused = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        volatile AsyncProducer pauseOn;

        PausingServicePool(int capacity) {
            super(Endpoint::createAsyncProducer, Producer::getEndpoint, capacity);
            start();
        }

        @Override
        void onSingleAcquired(AsyncProducer s) {
            if (s != null && s == pauseOn) {
                pauseOn = null;
                paused.countDown();
                try {
                    proceed.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
