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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.InflightRepository;

/**
 * @version
 */
public class DefaultInflightRepositoryConcurrentTest extends ContextTestSupport {

    private static final int THREAD_COUNT = 20;
    private static final int TOTAL_ENDPOINTS = 10000;
    private static final int LOOP_COUNT = 100000;

    // the failed flag should be marked as volatile so that we have got guaranteed visibility inside the main thread
    private static volatile boolean failed;
    private static CamelContext context = new DefaultCamelContext();

    public void testThreaded() throws Exception {
        DefaultInflightRepository toTest = new DefaultInflightRepository();
        Endpoint[] endpoints = new Endpoint[TOTAL_ENDPOINTS];

        for (int i = 0; i < endpoints.length; i++) {
            // create TOTAL_ENDPOINTS endpoints
            Endpoint endpoint = new DefaultEndpoint() {
                final String uri = "def:" + System.nanoTime();
                @Override
                public String getEndpointUri() {
                    return uri;
                }

                public boolean isSingleton() {
                    return false;
                }

                public Producer createProducer() throws Exception {
                    return null;
                }

                public Consumer createConsumer(Processor processor) throws Exception {
                    return null;
                }
            };
            endpoints[i] = endpoint;
        }

        AtomicInteger locker = new AtomicInteger();

        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new TypicalConsumer(endpoints, toTest, locker));
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }

        Thread.sleep(1000);

        while (locker.get() > 0) {
            synchronized (locker) {
                locker.wait();
            }
        }

        if (failed) {
            throw new AssertionError("Failed to properly track endpoints");
        }

        for (Endpoint endpoint : endpoints) {
            Assert.assertEquals("Size MUST be 0", 0, toTest.size(endpoint));
        }

        if (toTest.size() > 0) {
            throw new AssertionError("Test either incomplete or tracking failed");
        }

        Assert.assertEquals("Must not have any references left", 0, toTest.endpointSize());
    }

    private static class TypicalConsumer implements Runnable {
        private final Endpoint[] endpoints;
        private final InflightRepository repo;
        private final AtomicInteger locker;
        private final Random rand = new Random(System.nanoTime());

        TypicalConsumer(Endpoint[] endpoints, InflightRepository repo, AtomicInteger locker) {
            this.endpoints = endpoints;
            this.repo = repo;
            this.locker = locker;
        }

        public void run() {
            synchronized (locker) {
                locker.incrementAndGet();
            }
            try {
                for (int i = 0; i < LOOP_COUNT; i++) {
                    Endpoint endpoint = endpoints[Math.abs(rand.nextInt() % endpoints.length)];
                    endpoint.setCamelContext(context);
                    Exchange exchange = new DefaultExchange(endpoint);
                    repo.add(exchange);
                    int size = repo.size(endpoint);
                    if (size <= 0) {
                        failed = true;
                    }
                    repo.remove(exchange);
                }
            // just to make it sure do catch any possible Throwable 
            } catch (Throwable t) {
                failed = true;
            }

            synchronized (locker) {
                locker.decrementAndGet();
                locker.notifyAll();
            }
        }
    }
}
