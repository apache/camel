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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.spi.ServicePool;

/**
 * @version 
 */
public class CamelContextSharedProducerServicePoolTest extends ContextTestSupport {

    private static class MyProducer extends DefaultProducer implements ServicePoolAware {

        private boolean start;
        private boolean stop;

        MyProducer(Endpoint endpoint) throws Exception {
            super(endpoint);
            start();
        }

        public void process(Exchange exchange) throws Exception {
            // noop
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            assertEquals("Should not be started twice", false, start);
            start = true;
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            assertEquals("Should not be stopped twice", false, stop);
            stop = true;
        }
    }

    public void testSharedProducerServicePool() throws Exception {
        // the default capacity
        assertEquals(100, context.getProducerServicePool().getCapacity());

        // change it
        context.getProducerServicePool().setCapacity(25);
        assertEquals(25, context.getProducerServicePool().getCapacity());
    }

    public void testSharedProducerServicePoolHitMax() throws Exception {
        // the default capacity
        assertEquals(100, context.getProducerServicePool().getCapacity());

        // change it
        ServicePool<Endpoint, Producer> pool = context.getProducerServicePool();
        pool.setCapacity(3);
        assertEquals(3, pool.getCapacity());

        Endpoint endpoint = context.getEndpoint("mock:foo");

        assertNull(pool.acquire(endpoint));
        assertEquals(0, pool.size());

        Producer producer = new MyProducer(endpoint);
        producer = pool.addAndAcquire(endpoint, producer);
        assertEquals(0, pool.size());

        Producer producer2 = new MyProducer(endpoint);
        producer2 = pool.addAndAcquire(endpoint, producer2);
        assertEquals(0, pool.size());

        Producer producer3 = new MyProducer(endpoint);
        producer3 = pool.addAndAcquire(endpoint, producer3);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        pool.release(endpoint, producer2);
        assertEquals(2, pool.size());

        pool.release(endpoint, producer3);
        assertEquals(3, pool.size());

        Producer producer4 = new MyProducer(endpoint);
        try {
            producer4 = pool.addAndAcquire(endpoint, producer4);
            fail("Should throw an exception");
        } catch (IllegalStateException e) {
            assertEquals("Queue full", e.getMessage());
        }
        assertEquals(3, pool.size());
    }

}
