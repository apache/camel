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
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.impl.engine.ProducerServicePool;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.function.ThrowingFunction;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

public class DefaultProducerCacheTest extends ContextTestSupport {

    private final AtomicInteger producerCounter = new AtomicInteger();
    private final AtomicInteger stopCounter = new AtomicInteger();
    private final AtomicInteger shutdownCounter = new AtomicInteger();

    private MyComponent component;

    @Test
    public void testCacheProducerAcquireAndRelease() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

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
            assertEquals("Size should be 1000", 1000, cache.size());
        });

        cache.stop();

        assertEquals("Size should be 0", 0, cache.size());
    }

    @Test
    public void testCacheStopExpired() throws Exception {
        DefaultProducerCache cache = new DefaultProducerCache(this, context, 5);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        for (int i = 0; i < 8; i++) {
            Endpoint e = newEndpoint(true, i);
            e.setCamelContext(context);
            AsyncProducer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // the eviction is async so force cleanup
            cache.cleanUp();
            assertEquals("Size should be 5", 5, cache.size());
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

        assertEquals("Size should be 0", 0, cache.size());

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

        assertEquals("Size should be 4", 4, cache.size());

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

        assertEquals("Size should be 0", 0, cache.size());

        Endpoint e = newEndpoint(false, 1);
        e.setCamelContext(context);

        AsyncProducer p1 = cache.acquireProducer(e);
        assertEquals("Size should be 0", 0, cache.size());

        AsyncProducer p2 = cache.acquireProducer(e);
        assertEquals("Size should be 0", 0, cache.size());

        cache.releaseProducer(e, p2);
        cache.releaseProducer(e, p1);

        assertEquals("Size should be 2", 2, cache.size());

        // nothing has stopped yet
        assertEquals(0, stopCounter.get());

        p1 = cache.acquireProducer(e);
        p2 = cache.acquireProducer(e);
        AsyncProducer p3 = cache.acquireProducer(e);

        assertEquals("Size should be 0", 0, cache.size());

        // nothing has stopped yet even we have 3 producers and a cache limit of 2
        assertEquals(0, stopCounter.get());

        // force evict p1 while its in use (eg simulate someone else grabbing it while evicting race condition)
        cache.forceEvict(p1);

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

    private class MyProducerCache extends DefaultProducerCache {

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

    private class MyServicePool extends ProducerServicePool {

        public MyServicePool(ThrowingFunction<Endpoint, AsyncProducer, Exception> creator, Function<AsyncProducer, Endpoint> getEndpoint, int capacity) {
            super(creator, getEndpoint, capacity);
        }

        @Override
        protected void onEvict(AsyncProducer asyncProducer) {
            super.onEvict(asyncProducer);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        component = new MyComponent(context);
    }

    protected MyEndpoint newEndpoint(boolean isSingleton, int number) {
        return new MyEndpoint(component, isSingleton, number);
    }

    private final class MyComponent extends DefaultComponent {

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
