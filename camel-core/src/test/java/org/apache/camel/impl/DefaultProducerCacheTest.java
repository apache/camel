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

    private static final AtomicInteger COUNTER = new AtomicInteger();

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
            Endpoint e = new MyEndpoint(i);
            Producer p = cache.acquireProducer(e);
            cache.releaseProducer(e, p);
        }

        assertEquals("Size should be 5", 5, cache.size());

        // should have stopped the 3 evicted
        assertEquals(3, COUNTER.get());

        cache.stop();

        // should have stopped all 8
        assertEquals(8, COUNTER.get());
    }

    private final class MyEndpoint extends DefaultEndpoint {

        private int number;

        private MyEndpoint(int number) {
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
            return true;
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
            COUNTER.incrementAndGet();
        }
    }

}
