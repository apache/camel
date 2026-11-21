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
package org.apache.camel.impl;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.cache.ProducerServicePool;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Singleton producers are shared by all the users of a producer cache. When one of them is evicted from the cache, it
 * must not be stopped (nor its endpoint) while it is in use, it must not be handed out again, and the eviction must
 * only affect the pool of that producer.
 */
class ProducerCacheSingletonEvictionTest extends ContextTestSupport {

    private static final String SERVICE_POOL_LOGGER = "org.apache.camel.support.cache.ServicePool";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void doPostSetup() {
        context.addComponent("my", new MyComponent());
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void testEvictedProducerNotStoppedWhileProcessing() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 1);
        cache.start();
        MyEndpoint a = (MyEndpoint) context.getEndpoint("my:a");
        MyEndpoint b = (MyEndpoint) context.getEndpoint("my:b");
        a.inProcess = new CountDownLatch(1);
        a.proceed = new CountDownLatch(1);

        // worker 1 sends to a, and the producer of a waits inside process()
        Future<Exchange> sent = executor.submit(() -> cache.send(a, a.createExchange(), null));
        assertTrue(a.inProcess.await(10, TimeUnit.SECONDS));
        MyProducer pa = a.lastProducer;

        // worker 2 sends to b: the cache (capacity 1) evicts the producer of a
        cache.send(b, b.createExchange(), null);
        assertTrue(pa.isStarted(), "The evicted producer should not be stopped while it is processing");
        assertTrue(a.isStarted(), "The endpoint of the evicted producer should not be stopped while it is used");

        a.proceed.countDown();
        Exchange done = sent.get(10, TimeUnit.SECONDS);
        assertNull(done.getException(), "The send should not fail");

        // the producer is stopped when it is released, and the dynamic endpoint as nothing else uses it
        assertTrue(pa.isStopped(), "The evicted producer should be stopped when it is released");
        assertTrue(a.isStopped(), "The dynamic endpoint not in use should be stopped to free resources");
        cache.stop();
    }

    @Test
    void testEvictedProducerInUseIsStoppedWhenReleased() throws Exception {
        MyServicePool pool = new MyServicePool(1);
        Endpoint a = context.getEndpoint("my:a");
        Endpoint b = context.getEndpoint("my:b");

        AsyncProducer pa1 = pool.acquire(a);
        // acquiring b evicts pa1, which is in use
        AsyncProducer pb = pool.acquire(b);
        assertTrue(isStarted(pa1), "The evicted producer should not be stopped while it is in use");

        // the evicted producer is not handed out again
        AsyncProducer pa2 = pool.acquire(a);
        assertNotSame(pa1, pa2);
        assertTrue(isStarted(pa2), "The acquired producer should be started");

        // the cache evicts pa1 again (an acquire in progress put it in the cache again): this must not affect pa2
        pool.evict(pa1);
        assertSame(pa2, pool.acquire(a));
        pool.release(a, pa2);
        assertTrue(isStarted(pa1), "The evicted producer should not be stopped while it is in use");
        assertTrue(isStarted(pa2), "The current producer should not be stopped by the eviction of another one");

        pool.release(a, pa1);
        assertTrue(isStopped(pa1), "The evicted producer should be stopped when it is released");
        assertTrue(isStarted(pa2), "The current producer should not be stopped by the release of another one");
        assertTrue(ServiceHelper.isStarted(a), "The endpoint should not be stopped while the current producer uses it");

        pool.release(a, pa2);
        pool.release(b, pb);
        pool.stop();
    }

    @Test
    void testEvictionDoesNotAffectTheNewPoolOfTheEndpoint() throws Exception {
        MyServicePool pool = new MyServicePool(1);
        Endpoint a = context.getEndpoint("my:a");

        AsyncProducer pa1 = pool.acquire(a);
        pool.release(a, pa1);
        // acquiring b evicts pa1, which is idle
        AsyncProducer pb = pool.acquire(b());
        assertTrue(isStopped(pa1), "The evicted idle producer should be stopped");

        // resolve the endpoint again (as toD does) and acquire it twice: one pool and one producer of a started endpoint
        Endpoint a2 = context.getEndpoint("my:a");
        AsyncProducer pa2 = pool.acquire(a2);
        assertTrue(isStarted(pa2), "The acquired producer should be started");
        assertTrue(ServiceHelper.isStarted(a2), "The endpoint of the acquired producer should be started");
        AsyncProducer pa3 = pool.acquire(a2);
        assertSame(pa2, pa3, "A singleton endpoint should have one producer");
        pool.release(a2, pa3);
        pool.release(a2, pa2);
        assertTrue(isStarted(pa2), "The current producer should be started");
        assertTrue(ServiceHelper.isStarted(a2), "The endpoint of the current producer should be started");

        pool.release(b(), pb);
        pool.stop();
    }

    @Test
    void testFastPathDoesNotHandOutEvictedProducer() {
        MyProducerCache cache = new MyProducerCache(context);
        cache.start();
        Endpoint a = context.getEndpoint("my:a");

        // pa1 is in use, and is the last used producer of the cache
        AsyncProducer pa1 = cache.acquireProducer(a);
        cache.evict(pa1);
        assertTrue(isStarted(pa1), "The evicted producer should not be stopped while it is in use");

        AsyncProducer pa2 = cache.acquireProducer(a);
        assertNotSame(pa1, pa2, "The evicted producer should not be handed out again");
        assertTrue(isStarted(pa2), "The acquired producer should be started");

        cache.releaseProducer(a, pa1);
        assertTrue(isStopped(pa1), "The evicted producer should be stopped when it is released");
        cache.releaseProducer(a, pa2);
        assertTrue(isStarted(pa2), "The current producer should not be stopped");

        // the fast path acquires the current producer, and counts it as in use
        AsyncProducer pa3 = cache.acquireProducer(a);
        assertSame(pa2, pa3);
        cache.evict(pa3);
        assertTrue(isStarted(pa3), "The evicted producer should not be stopped while it is in use");
        cache.releaseProducer(a, pa3);
        assertTrue(isStopped(pa3), "The evicted producer should be stopped when it is released");
        cache.stop();
    }

    @Test
    void testAcquireDoesNotReturnNullWhenEvictedMeanwhile() throws Exception {
        MyServicePool pool = new MyServicePool(1);
        Endpoint a = context.getEndpoint("my:a");
        MyProducer pa = (MyProducer) pool.acquire(a);
        pool.release(a, pa);

        // the trace log of the acquire calls toString() of the producer, which waits there for the eviction
        pa.inToString = new CountDownLatch(1);
        pa.proceedToString = new CountDownLatch(1);
        Level level = LogManager.getLogger(SERVICE_POOL_LOGGER).getLevel();
        Configurator.setLevel(SERVICE_POOL_LOGGER, Level.TRACE);
        try {
            Future<AsyncProducer> acquired = executor.submit(() -> {
                pa.waitingThread = Thread.currentThread();
                return pool.acquire(a);
            });
            assertTrue(pa.inToString.await(10, TimeUnit.SECONDS));

            // acquiring b evicts pa while the other thread is acquiring it
            AsyncProducer pb = pool.acquire(b());
            pa.proceedToString.countDown();
            AsyncProducer got = acquired.get(10, TimeUnit.SECONDS);
            assertNotNull(got, "Acquire should not return null while the pool is started");
            assertSame(pa, got);
            assertTrue(isStarted(pa), "The evicted producer should not be stopped while it is in use");

            pool.release(a, got);
            assertTrue(isStopped(pa), "The evicted producer should be stopped when it is released");
            pool.release(b(), pb);
        } finally {
            Configurator.setLevel(SERVICE_POOL_LOGGER, level);
        }
        pool.stop();
    }

    private Endpoint b() {
        return context.getEndpoint("my:b");
    }

    private static boolean isStarted(AsyncProducer producer) {
        return ((MyProducer) producer).isStarted();
    }

    private static boolean isStopped(AsyncProducer producer) {
        return ((MyProducer) producer).isStopped();
    }

    private static final class MyServicePool extends ProducerServicePool {

        MyServicePool(int capacity) {
            super(Endpoint::createAsyncProducer, Producer::getEndpoint, capacity);
            start();
        }

        void evict(AsyncProducer producer) {
            onEvict(producer);
        }
    }

    private static final class MyProducerCache extends DefaultProducerCache {

        private MyServicePool pool;

        MyProducerCache(CamelContext context) {
            super(ProducerCacheSingletonEvictionTest.class, context, 1);
        }

        @Override
        protected ProducerServicePool createServicePool(CamelContext camelContext, int cacheSize) {
            pool = new MyServicePool(cacheSize);
            return pool;
        }

        void evict(AsyncProducer producer) {
            pool.evict(producer);
        }
    }

    private static final class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyEndpoint(uri, this);
        }
    }

    private static final class MyEndpoint extends DefaultEndpoint {
        volatile CountDownLatch inProcess;
        volatile CountDownLatch proceed;
        volatile MyProducer lastProducer;

        MyEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            MyProducer producer = new MyProducer(this);
            lastProducer = producer;
            return producer;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MyProducer extends DefaultAsyncProducer {
        volatile CountDownLatch inToString;
        volatile CountDownLatch proceedToString;
        volatile Thread waitingThread;

        MyProducer(MyEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            MyEndpoint endpoint = (MyEndpoint) getEndpoint();
            CountDownLatch in = endpoint.inProcess;
            if (in != null) {
                endpoint.inProcess = null;
                in.countDown();
                try {
                    if (!endpoint.proceed.await(10, TimeUnit.SECONDS)) {
                        exchange.setException(new IllegalStateException("Not allowed to proceed"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exchange.setException(e);
                }
                if (!isStarted()) {
                    // like a producer whose resources are released when it is stopped
                    exchange.setException(new IllegalStateException("Producer stopped while processing"));
                }
            }
            callback.done(true);
            return true;
        }

        @Override
        public String toString() {
            CountDownLatch in = inToString;
            if (in != null && Thread.currentThread() == waitingThread) {
                inToString = null;
                in.countDown();
                try {
                    proceedToString.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "MyProducer[" + getEndpoint().getEndpointUri() + "]";
        }
    }
}
