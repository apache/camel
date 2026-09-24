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

import org.apache.camel.AsyncCallback;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-singleton producers are pooled per endpoint. When one of them is evicted from the producer cache, it must not be
 * handed out again once it is stopped, and it must not be stopped while it is in use.
 */
class ProducerCacheNonSingletonEvictionTest extends ContextTestSupport {

    private DefaultProducerCache cache;
    private Endpoint a;
    private Endpoint b;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.addComponent("pooled", new PooledComponent());
        a = context.getEndpoint("pooled:a");
        b = context.getEndpoint("pooled:b");
        cache = new DefaultProducerCache(this, context, 2);
        cache.start();
    }

    @AfterEach
    void stopCache() {
        cache.stop();
    }

    @Test
    void testEvictIdleProducer() {
        // two producers for endpoint a, both idle in its pool
        PooledProducer a1 = acquire(a);
        PooledProducer a2 = acquire(a);
        cache.releaseProducer(a, a1);
        cache.releaseProducer(a, a2);

        // a producer for endpoint b evicts a1 (the eldest) from the cache, and a1 is stopped
        cache.releaseProducer(b, acquire(b));
        cache.cleanUp();
        assertTrue(a1.isStopped(), "Evicted idle producer should be stopped");

        // the stopped producer must not be handed out again
        PooledProducer next = acquire(a);
        assertTrue(next.isStarted(), "Acquired producer should be started");
        assertSame(a2, next);
        cache.releaseProducer(a, next);
    }

    @Test
    void testEvictProducerInUse() {
        // a1 is in use, a2 is idle in the pool of endpoint a
        PooledProducer a1 = acquire(a);
        cache.releaseProducer(a, acquire(a));

        // a producer for endpoint b evicts a1 (the eldest) from the cache while it is in use
        cache.releaseProducer(b, acquire(b));
        cache.cleanUp();
        cache.releaseProducer(a, acquire(a));
        assertTrue(a1.isStarted(), "Evicted producer should not be stopped while it is in use");

        // when it is released, it is stopped instead of being returned to the pool
        cache.releaseProducer(a, a1);
        assertTrue(a1.isStopped(), "Evicted producer should be stopped when it is released");

        PooledProducer next1 = acquire(a);
        PooledProducer next2 = acquire(a);
        assertNotSame(a1, next1);
        assertNotSame(a1, next2);
        assertTrue(next1.isStarted(), "Acquired producer should be started");
        assertTrue(next2.isStarted(), "Acquired producer should be started");
        cache.releaseProducer(a, next1);
        cache.releaseProducer(a, next2);
    }

    private PooledProducer acquire(Endpoint endpoint) {
        return (PooledProducer) cache.acquireProducer(endpoint);
    }

    private static final class PooledComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new PooledEndpoint(uri, this);
        }
    }

    private static final class PooledEndpoint extends DefaultEndpoint {

        PooledEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new PooledProducer(this);
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSingletonProducer() {
            return false;
        }
    }

    private static final class PooledProducer extends DefaultAsyncProducer {

        PooledProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            callback.done(true);
            return true;
        }
    }
}
