/**
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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * @version 
 */
public class DefaultProducerCacheTest extends ContextTestSupport {

    private final AtomicInteger stopCounter = new AtomicInteger();
    private final AtomicInteger shutdownCounter = new AtomicInteger();

    public void testCacheProducerAcquireAndRelease() throws Exception {
        ProducerCache cache = new ProducerCache(this, context);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        // test that we cache at most 1000 producers to avoid it eating to much memory
        for (int i = 0; i < 1003; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            Producer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        assertEquals("Size should be 1000", 1000, cache.size());
        cache.stop();
    }

    public void testCacheStopExpired() throws Exception {
        ProducerCache cache = new ProducerCache(this, context, 5);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        for (int i = 0; i < 8; i++) {
            Endpoint e = new MyEndpoint(true, i);
            Producer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        assertEquals("Size should be 5", 5, cache.size());

        // should have stopped the 3 evicted
        assertEquals(3, stopCounter.get());

        cache.stop();

        // should have stopped all 8
        assertEquals(8, stopCounter.get());
    }

    public void testReleaseProducerInvokesStopAndShutdownByNonSingletonProducers() throws Exception {
        ProducerCache cache = new ProducerCache(this, context, 1);
        cache.start();

        assertEquals("Size should be 0", 0, cache.size());

        for (int i = 0; i < 3; i++) {
            Endpoint e = new MyEndpoint(false, i);
            Producer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        assertEquals("Size should be 0", 0, cache.size());

        // should have stopped all 3
        assertEquals(3, stopCounter.get());

        // should have shutdown all 3
        assertEquals(3, shutdownCounter.get());

        cache.stop();

        // no more stop after stopping the cache
        assertEquals(3, stopCounter.get());

        // no more shutdown after stopping the cache
        assertEquals(3, shutdownCounter.get());
    }

    private final class MyEndpoint extends DefaultEndpoint {

        private final boolean isSingleton;
        private final int number;

        private MyEndpoint(boolean isSingleton, int number) {
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

        @Override
        public String getEndpointUri() {
            return "my://" + number;
        }
    }

    private final class MyProducer extends DefaultProducer {

        public MyProducer(Endpoint endpoint) {
            super(endpoint);
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
    }

}
