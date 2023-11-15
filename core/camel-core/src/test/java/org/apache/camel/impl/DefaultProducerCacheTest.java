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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.cache.ProducerServicePool;
import org.apache.camel.util.function.ThrowingFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultProducerCacheTest extends ContextTestSupport {

    private final AtomicInteger producerCounter = new AtomicInteger();
    private final AtomicInteger stopCounter = new AtomicInteger();
    private final AtomicInteger shutdownCounter = new AtomicInteger();

    private MyComponent component;

    @Test
    public void testCacheProducerAcquireAndRelease() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        // test that we cache at most 1000 producers to avoid it eating to much
        // memory
        for (int i = 0; i < 1003; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            AsyncProducer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // the eviction is async so force cleanup
            cache.cleanUp();
            assertEquals(1000, cache.size(), "Size should be 1000");
        });

        cache.stop();

        assertEquals(0, cache.size(), "Size should be 0");
    }

    @Test
    public void testCacheStopExpired() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 5);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        for (int i = 0; i < 8; i++) {
            Endpoint e = newEndpoint(true, i);
            e.setCamelContext(context);
            AsyncProducer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // the eviction is async so force cleanup
            cache.cleanUp();
            assertEquals(5, cache.size(), "Size should be 5");
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(3, stopCounter.get()));

        cache.stop();

        // should have stopped all 8
        assertEquals(8, stopCounter.get());
    }

    @Test
    public void testExtendedStatistics() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 5);
        cache.setExtendedStatistics(true);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        // use 1 = 2 times
        // use 2 = 3 times
        // use 3..4 = 1 times
        // use 5 = 0 times
        Endpoint e = newEndpoint(true, 1);
        AsyncProducer p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 1);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 2);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 2);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 2);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 3);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);
        e = newEndpoint(true, 4);
        p = cache.acquireProducer(e);
        cache.releaseProducer(e, p);

        assertEquals(4, cache.size(), "Size should be 4");

        EndpointUtilizationStatistics stats = cache.getEndpointUtilizationStatistics();
        assertEquals(4, stats.size());

        Map<String, Long> recent = stats.getStatistics();
        assertEquals(2, recent.get("my://1").longValue());
        assertEquals(3, recent.get("my://2").longValue());
        assertEquals(1, recent.get("my://3").longValue());
        assertEquals(1, recent.get("my://4").longValue());
        assertNull(recent.get("my://5"));

        cache.stop();
    }

    @Test
    public void testCacheEvictWhileInUse() throws Exception {
        producerCounter.set(0);

        MyProducerCache cache = new MyProducerCache(this, context, 2);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        Endpoint e = newEndpoint(false, 1);
        e.setCamelContext(context);

        AsyncProducer p1 = cache.acquireProducer(e);
        assertEquals(0, cache.size(), "Size should be 0");

        AsyncProducer p2 = cache.acquireProducer(e);
        assertEquals(0, cache.size(), "Size should be 0");

        cache.releaseProducer(e, p2);
        cache.releaseProducer(e, p1);

        assertEquals(2, cache.size(), "Size should be 2");

        // nothing has stopped yet
        assertEquals(0, stopCounter.get());

        p1 = cache.acquireProducer(e);
        p2 = cache.acquireProducer(e);
        AsyncProducer p3 = cache.acquireProducer(e);

        assertEquals(0, cache.size(), "Size should be 0");

        // nothing has stopped yet even we have 3 producers and a cache limit of 2
        assertEquals(0, stopCounter.get());

        // force evict p2 while its in use (eg simulate someone else grabbing it while evicting race condition)
        cache.forceEvict(p2);

        // and should still not be stopped
        assertEquals(0, stopCounter.get());

        // now release the others back
        cache.releaseProducer(e, p3);
        cache.releaseProducer(e, p2);

        // which should trigger the eviction run to stop one of the producers as we have 3 and the cache size is 2
        assertEquals(1, stopCounter.get());

        cache.stop();

        // should have stopped all 3 when the cache is stopped
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(3, stopCounter.get()));
    }

    @Test
    public void testAcquireProducerConcurrency() throws InterruptedException, ExecutionException {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        List<Endpoint> endpoints = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            AsyncProducer p = cache.acquireProducer(e);
            endpoints.add(e);
        }

        assertEquals(3, cache.size());

        ExecutorService ex = Executors.newFixedThreadPool(16);

        List<Callable<Boolean>> callables = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            int index = i % 3;
            callables.add(() -> {
                Producer producer = cache.acquireProducer(endpoints.get(index));
                boolean isEqual
                        = producer.getEndpoint().getEndpointUri().equalsIgnoreCase(endpoints.get(index).getEndpointUri());

                if (!isEqual) {
                    log.info("Endpoint uri to acquire: {}, returned producer (uri): {}", endpoints.get(index).getEndpointUri(),
                            producer.getEndpoint().getEndpointUri());
                }

                return isEqual;
            });
        }

        for (int i = 1; i <= 100; i++) {
            log.info("Iteration: {}", i);
            List<Future<Boolean>> results = ex.invokeAll(callables);
            for (Future<Boolean> future : results) {
                assertEquals(true, future.get());
            }
        }
    }

    private static class MyProducerCache extends DefaultProducerCache {

        private MyServicePool myServicePool;

        public MyProducerCache(Object source, CamelContext camelContext, int cacheSize) {
            super(source, camelContext, cacheSize);
        }

        @Override
        protected ProducerServicePool createServicePool(CamelContext camelContext, int cacheSize) {
            myServicePool = new MyServicePool(Endpoint::createAsyncProducer, Producer::getEndpoint, cacheSize);
            return myServicePool;
        }

        public void forceEvict(AsyncProducer producer) {
            myServicePool.onEvict(producer);
        }

    }

    private static class MyServicePool extends ProducerServicePool {

        public MyServicePool(ThrowingFunction<Endpoint, AsyncProducer, Exception> creator,
                             Function<AsyncProducer, Endpoint> getEndpoint, int capacity) {
            super(creator, getEndpoint, capacity);
        }

        @Override
        protected void onEvict(AsyncProducer asyncProducer) {
            super.onEvict(asyncProducer);
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        component = new MyComponent(context);
    }

    protected MyEndpoint newEndpoint(boolean isSingleton, int number) {
        return new MyEndpoint(component, isSingleton, number);
    }

    private static final class MyComponent extends DefaultComponent {

        public MyComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    private final class MyEndpoint extends DefaultEndpoint {

        private final boolean isSingleton;
        private final int number;

        private MyEndpoint(MyComponent component, boolean isSingleton, int number) {
            super("my://" + number, component);
            this.isSingleton = isSingleton;
            this.number = number;
        }

        @Override
        public Producer createProducer() throws Exception {
            return new MyProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return isSingleton;
        }
    }

    private final class MyProducer extends DefaultProducer {

        private int id;

        MyProducer(Endpoint endpoint) {
            super(endpoint);
            id = producerCounter.incrementAndGet();
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            stopCounter.incrementAndGet();
        }

        @Override
        protected void doShutdown() throws Exception {
            shutdownCounter.incrementAndGet();
        }

        @Override
        public String toString() {
            return "MyProducer[" + id + "]";
        }
    }
}
