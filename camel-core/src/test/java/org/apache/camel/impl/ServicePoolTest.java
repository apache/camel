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

import org.apache.camel.ServicePool;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;

/**
 * @version $Revision$
 */
public class ServicePoolTest extends ContextTestSupport {

    // TODO: Add unit test for only once stop/start of pooled service
    // TODO: Add some stress test of the pool

    private ProducerServicePool pool;

    private class MyProducer extends DefaultProducer implements ServicePoolAware {

        public MyProducer(Endpoint endpoint) {
            super(endpoint);
        }

        public void process(Exchange exchange) throws Exception {
            // noop
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pool = new ProducerServicePool(5);
        pool.start();
    }

    @Override
    protected void tearDown() throws Exception {
        pool.stop();
        super.tearDown();
    }

    public void testSingleEnry() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        assertNull(pool.acquire(endpoint));
        assertEquals(0, pool.size());

        Producer producer = new MyProducer(endpoint);
        producer = pool.acquireIfAbsent(endpoint, producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        producer = pool.acquire(endpoint);
        assertNotNull(producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        pool.stop();
        assertEquals(0, pool.size());
    }

    public void testTwoEnries() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        Producer producer1 = new MyProducer(endpoint);
        Producer producer2 = new MyProducer(endpoint);

        producer1 = pool.acquireIfAbsent(endpoint, producer1);
        producer2 = pool.acquireIfAbsent(endpoint, producer2);

        assertEquals(0, pool.size());
        pool.release(endpoint, producer1);
        assertEquals(1, pool.size());
        pool.release(endpoint, producer2);
        assertEquals(2, pool.size());

        pool.stop();
        assertEquals(0, pool.size());
    }

    public void testThreeEntries() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        Producer producer1 = new MyProducer(endpoint);
        Producer producer2 = new MyProducer(endpoint);
        Producer producer3 = new MyProducer(endpoint);

        producer1 = pool.acquireIfAbsent(endpoint, producer1);
        producer2 = pool.acquireIfAbsent(endpoint, producer2);
        producer3 = pool.acquireIfAbsent(endpoint, producer3);

        assertEquals(0, pool.size());
        pool.release(endpoint, producer1);
        assertEquals(1, pool.size());
        pool.release(endpoint, producer2);
        assertEquals(2, pool.size());
        pool.release(endpoint, producer3);
        assertEquals(3, pool.size());

        pool.stop();
        assertEquals(0, pool.size());
    }
    
}
