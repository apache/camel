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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The LRU cache of a ServicePool calls onEvict while it holds its write lock. The stop of an evicted idle singleton
 * producer (and the in-use check and stop of its endpoint, which take the lock of the ServicePool) must therefore run
 * after the cache operation, not inside the eviction: otherwise a stop of the ServicePool (which holds its lock and
 * clears the cache) deadlocks with an acquire that evicts, and a slow stop of the evicted producer blocks every acquire
 * of the cache.
 */
class SinglePoolEvictionLockTest extends ContextTestSupport {

    // daemon threads: on failure they stay blocked, and must not keep the JVM alive
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SinglePoolEvictionLockTest");
        t.setDaemon(true);
        return t;
    });

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void testStopOfThePoolDoesNotDeadlockWithAnAcquireThatEvicts() throws Exception {
        PausingServicePool pool = new PausingServicePool(1);
        TestEndpoint a = new TestEndpoint("test:a", context, true);
        TestEndpoint b = new TestEndpoint("test:b", context, true);
        // the stop of the pool stops the pool of b before the pool of a (the order of the pool map)
        Map<Endpoint, Boolean> order = new ConcurrentHashMap<>();
        order.put(a, true);
        order.put(b, true);
        assertSame(b, order.keySet().iterator().next());

        AsyncProducer pa = pool.acquire(a);
        pool.release(a, pa);

        // an acquire of b creates its producer, and pauses before it puts it in the cache
        pool.pauseOn = b;
        Future<AsyncProducer> acquired = executor.submit(() -> pool.acquire(b));
        assertTrue(pool.paused.await(10, TimeUnit.SECONDS));
        Thread acquirer = pool.pausedThread;
        BlockingStopProducer pb = (BlockingStopProducer) pool.pausedService;
        pb.blockStop();

        // the pool stops, and waits in the stop of pb while it holds its lock
        Future<?> stopped = executor.submit(pool::stop);
        assertTrue(pb.stopEntered.await(10, TimeUnit.SECONDS));

        // the acquire puts pb in the cache, which evicts pa (idle): the stop of the evicted pa needs the lock of the
        // pool
        pool.proceed.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> isWaitingIn(acquirer, "stopEvicted"));

        // the stop of the pool goes on, and clears the cache
        pb.releaseStop();
        try {
            stopped.get(10, TimeUnit.SECONDS);
            assertNotNull(acquired.get(10, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            fail("The stop of the pool and the acquire that evicts are deadlocked");
        }
        assertTrue(ServiceHelper.isStopped(pa), "The evicted producer should be stopped");
    }

    @Test
    void testSlowStopOfAnEvictedProducerDoesNotBlockOtherAcquires() throws Exception {
        PausingServicePool pool = new PausingServicePool(1);
        TestEndpoint a = new TestEndpoint("test:a", context, true);
        TestEndpoint b = new TestEndpoint("test:b", context, true);

        BlockingStopProducer pa = (BlockingStopProducer) pool.acquire(a);
        pool.release(a, pa);
        pa.blockStop();

        // the acquire of b evicts pa (idle), and the stop of pa waits
        Future<AsyncProducer> acquiredB = executor.submit(() -> pool.acquire(b));
        assertTrue(pa.stopEntered.await(10, TimeUnit.SECONDS));

        // another acquire of the cache meanwhile (a new pool and producer for a) is not held up by the stop of pa
        Future<AsyncProducer> acquiredA = executor.submit(() -> pool.acquire(a));
        try {
            AsyncProducer pa2 = acquiredA.get(10, TimeUnit.SECONDS);
            assertNotNull(pa2);
            assertTrue(pa2 != pa, "The evicted producer should not be acquired again");
        } catch (TimeoutException e) {
            fail("An acquire waits for the stop of an evicted producer of another acquire");
        } finally {
            pa.releaseStop();
        }
        assertNotNull(acquiredB.get(10, TimeUnit.SECONDS));
        pool.stop();
    }

    private static boolean isWaitingIn(Thread thread, String method) {
        if (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (method.equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static final class PausingServicePool extends ProducerServicePool {
        final CountDownLatch paused = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        volatile Endpoint pauseOn;
        volatile Thread pausedThread;
        volatile AsyncProducer pausedService;

        PausingServicePool(int capacity) {
            super(Endpoint::createAsyncProducer, Producer::getEndpoint, capacity);
            start();
        }

        @Override
        void onSingleAcquired(AsyncProducer s) {
            if (s != null && pauseOn != null && s.getEndpoint() == pauseOn) {
                pauseOn = null;
                pausedThread = Thread.currentThread();
                pausedService = s;
                paused.countDown();
                try {
                    proceed.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static final class TestEndpoint extends DefaultEndpoint {
        private final boolean singleton;

        TestEndpoint(String uri, CamelContext context, boolean singleton) {
            super(uri, null);
            setCamelContext(context);
            this.singleton = singleton;
        }

        @Override
        public boolean isSingletonProducer() {
            return singleton;
        }

        @Override
        public Producer createProducer() {
            return new BlockingStopProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class BlockingStopProducer extends DefaultAsyncProducer {
        final CountDownLatch stopEntered = new CountDownLatch(1);
        private final CountDownLatch stopReleased = new CountDownLatch(1);
        private volatile boolean block;

        BlockingStopProducer(Endpoint endpoint) {
            super(endpoint);
        }

        void blockStop() {
            block = true;
        }

        void releaseStop() {
            stopReleased.countDown();
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            callback.done(true);
            return true;
        }

        @Override
        protected void doStop() throws Exception {
            if (block) {
                stopEntered.countDown();
                stopReleased.await(20, TimeUnit.SECONDS);
            }
        }
    }
}
