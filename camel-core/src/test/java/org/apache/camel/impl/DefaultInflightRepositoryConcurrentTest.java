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

/**
 * @version
 */
public class DefaultInflightRepositoryConcurrentTest extends ContextTestSupport {

    public static final int THREAD_COUNT = 20;
    public static final int TOTAL_ENDPOINTS = 10000;
    public static final int LOOP_COUNT = 100000;

    private static boolean failure;
    private static CamelContext context = new DefaultCamelContext();

    public void testThreaded() throws Exception {
        long started = System.currentTimeMillis();

        DefaultInflightRepository toTest = new DefaultInflightRepository();
        Endpoint[] eps = new Endpoint[TOTAL_ENDPOINTS];

        for (int i = 0; i < eps.length; i++) {
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
            eps[i] = endpoint;
        }

        AtomicInteger locker = new AtomicInteger(0);

        Thread[] ts = new Thread[THREAD_COUNT];
        for (int i = 0; i < ts.length; i++) {
            TypicalConsumer consumer = new TypicalConsumer();
            consumer.eps = eps;
            consumer.repo = toTest;
            consumer.locker = locker;
            ts[i] = new Thread(consumer);
        }

        for (int i = 0; i < ts.length; i++) {
            ts[i].start();
        }
        Thread.sleep(1000);
        while (locker.get() > 0) {
            synchronized (locker) {
                locker.wait();
            }
        }

        if (failure) {
            throw new Exception("Failed to properly track endpoints");
        }

        for (Endpoint ep : eps) {
            Assert.assertTrue("Size MUST be 0", 0 == toTest.size(ep));
        }

        if (toTest.size() > 0) {
            throw new Exception("Test either incomplete or tracking failed");
        }

        Assert.assertTrue("Must not have any references left", 0 == toTest.endpointSize());
    }

    private static class TypicalConsumer implements Runnable {
        Endpoint[] eps;
        DefaultInflightRepository repo;
        Random rand = new Random(System.nanoTime());
        AtomicInteger locker;

        public void run() {
            synchronized (locker) {
                locker.incrementAndGet();
            }
            try {
                for (int i = 0; i < LOOP_COUNT; i++) {
                    Endpoint ep = eps[Math.abs(rand.nextInt() % eps.length)];
                    ep.setCamelContext(context);
                    Exchange ex = new DefaultExchange(ep);
                    repo.add(ex);
                    int size = repo.size(ep);
                    if (size <= 0) {
                        failure = true;
                    }
                    repo.remove(ex);
                }
            } catch (Exception e) {
                failure = true;
            }

            synchronized (locker) {
                locker.decrementAndGet();
                locker.notifyAll();
            }
        }
    }
}
