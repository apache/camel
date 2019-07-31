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
package org.apache.camel.util.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AsyncCompletionServiceTest extends Assert {

    private ExecutorService executor;
    private AsyncCompletionService<Object> service;

    @Before
    public void setUp() throws Exception {
        executor = Executors.newFixedThreadPool(5);
        service = new AsyncCompletionService<>(executor, true);
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
    }

    @Test
    public void testSubmitOrdered() throws Exception {

        service.submit(result("A"));
        service.submit(result("B"));

        Object a = service.take();
        Object b = service.take();

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedFirstTaskIsSlow() throws Exception {

        service.submit(result("A", 200));
        service.submit(result("B"));

        Thread.sleep(300);

        Object a = service.take();
        Object b = service.take();

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedFirstTaskIsSlowUsingPollTimeout() throws Exception {

        service.submit(result("A", 200));
        service.submit(result("B"));

        Object a = service.poll(5, TimeUnit.SECONDS);
        Object b = service.poll(5, TimeUnit.SECONDS);

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedFirstTaskIsSlowUsingPoll() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        service.submit(result("A", latch, 5, TimeUnit.SECONDS));
        service.submit(result("B"));

        // poll should not get it the first time
        Object a = service.poll();
        assertNull(a);

        // and neither the 2nd time
        a = service.poll();
        assertNull(a);

        // okay complete task
        latch.countDown();

        // okay take them
        a = service.take();
        Object b = service.take();

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedSecondTaskIsSlow() throws Exception {

        service.submit(result("A"));
        service.submit(result("B", 100));

        Object a = service.take();
        Object b = service.take();

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedSecondTaskIsSlowUsingPollTimeout() throws Exception {

        service.submit(result("A"));
        service.submit(result("B", 100));

        Object a = service.poll(5, TimeUnit.SECONDS);
        Object b = service.poll(5, TimeUnit.SECONDS);

        assertEquals("A", a);
        assertEquals("B", b);
    }

    @Test
    public void testSubmitOrderedLastTaskIsSlowUsingPoll() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        service.submit(result("A"));
        service.submit(result("B", latch, 5, TimeUnit.SECONDS));

        // take a
        Object a = service.take();
        assertNotNull(a);

        // poll should not get it the first time
        Object b = service.poll();
        assertNull(b);

        // and neither the 2nd time
        b = service.poll();
        assertNull(b);

        // okay complete task
        latch.countDown();

        // okay take it
        b = service.take();

        assertEquals("A", a);
        assertEquals("B", b);
    }

    Consumer<Consumer<Object>> result(Object r) {
        return result -> result.accept(r);
    }

    Consumer<Consumer<Object>> result(Object r, int delay) {
        return result -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result.accept(r);
        };
    }

    Consumer<Consumer<Object>> result(Object r, CountDownLatch latch, int timeout, TimeUnit unit) {
        return result -> {
            try {
                latch.await(timeout, unit);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result.accept(r);
        };
    }

}
