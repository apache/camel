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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.ServicePoolAware;

/**
 * @version 
 */
public class ServicePoolTest extends ContextTestSupport {

    private static boolean cleanup;
    private DefaultProducerServicePool pool;

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
            cleanup = true;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pool = new DefaultProducerServicePool(5);
        pool.start();
    }

    @Override
    protected void tearDown() throws Exception {
        pool.stop();
        super.tearDown();
        assertEquals("Should have stopped the producers", true, cleanup);
    }

    public void testSingleEntry() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        assertNull(pool.acquire(endpoint));
        assertEquals(0, pool.size());

        Producer producer = new MyProducer(endpoint);
        producer = pool.addAndAcquire(endpoint, producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());

        producer = pool.acquire(endpoint);
        assertNotNull(producer);
        assertEquals(0, pool.size());

        pool.release(endpoint, producer);
        assertEquals(1, pool.size());
    }

    public void testTwoEntries() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        Producer producer1 = new MyProducer(endpoint);
        Producer producer2 = new MyProducer(endpoint);

        producer1 = pool.addAndAcquire(endpoint, producer1);
        producer2 = pool.addAndAcquire(endpoint, producer2);

        assertEquals(0, pool.size());
        pool.release(endpoint, producer1);
        assertEquals(1, pool.size());
        pool.release(endpoint, producer2);
        assertEquals(2, pool.size());
    }

    public void testThreeEntries() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        Producer producer1 = new MyProducer(endpoint);
        Producer producer2 = new MyProducer(endpoint);
        Producer producer3 = new MyProducer(endpoint);

        producer1 = pool.addAndAcquire(endpoint, producer1);
        producer2 = pool.addAndAcquire(endpoint, producer2);
        producer3 = pool.addAndAcquire(endpoint, producer3);

        assertEquals(0, pool.size());
        pool.release(endpoint, producer1);
        assertEquals(1, pool.size());
        pool.release(endpoint, producer2);
        assertEquals(2, pool.size());
        pool.release(endpoint, producer3);
        assertEquals(3, pool.size());
    }

    public void testAcquireAddRelease() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");
        for (int i = 0; i < 10; i++) {
            Producer producer = pool.acquire(endpoint);
            if (producer == null) {
                producer = pool.addAndAcquire(endpoint, new MyProducer(endpoint));
            }
            assertNotNull(producer);
            pool.release(endpoint, producer);
        }
    }

    public void testAcquireAdd() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");
        List<Producer> producers = new ArrayList<Producer>();

        for (int i = 0; i < 5; i++) {
            Producer producer = pool.acquire(endpoint);
            if (producer == null) {
                producer = pool.addAndAcquire(endpoint, new MyProducer(endpoint));
            }
            assertNotNull(producer);
            producers.add(producer);
        }

        // release afterwards
        for (Producer producer : producers) {
            pool.release(endpoint, producer);
        }
    }

    public void testAcquireAddQueueFull() throws Exception {
        Endpoint endpoint = context.getEndpoint("mock:foo");

        for (int i = 0; i < 5; i++) {
            Producer producer = pool.addAndAcquire(endpoint, new MyProducer(endpoint));
            pool.release(endpoint, producer);
        }

        // when adding a 6 we get a queue full
        try {
            pool.addAndAcquire(endpoint, new MyProducer(endpoint));
            fail("Should have thrown an exception");
        } catch (IllegalStateException e) {
            assertEquals("Queue full", e.getMessage());
        }

        assertEquals(5, pool.size());
    }

    public void testConcurrent() throws Exception {
        final Endpoint endpoint = context.getEndpoint("mock:foo");

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Integer>> response = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Future<Integer> out = executor.submit(new Callable<Integer>() {
                public Integer call() throws Exception {
                    Producer producer = pool.acquire(endpoint);
                    if (producer == null) {
                        producer = pool.addAndAcquire(endpoint, new MyProducer(endpoint));
                    }
                    assertNotNull(producer);
                    pool.release(endpoint, producer);
                    return index;
                }
            });

            response.add(out);
        }

        for (int i = 0; i < 5; i++) {
            assertEquals(i, response.get(i).get().intValue());
        }
        executor.shutdownNow();
    }

    public void testConcurrentStress() throws Exception {
        final Endpoint endpoint = context.getEndpoint("mock:foo");

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Integer>> response = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Future<Integer> out = executor.submit(new Callable<Integer>() {
                public Integer call() throws Exception {
                    for (int j = 0; j < 100; j++) {
                        Producer producer = pool.acquire(endpoint);
                        if (producer == null) {
                            producer = pool.addAndAcquire(endpoint, new MyProducer(endpoint));
                        }
                        assertNotNull(producer);
                        pool.release(endpoint, producer);
                    }
                    return index;
                }
            });

            response.add(out);
        }

        for (int i = 0; i < 5; i++) {
            assertEquals(i, response.get(i).get().intValue());
        }
        executor.shutdownNow();
    }

}
