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
package org.apache.camel.component.reactive.streams;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;

import org.apache.camel.component.reactive.streams.engine.DelayedMonoPublisher;
import org.apache.camel.component.reactive.streams.support.TestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class DelayedMonoPublisherTest {

    private ExecutorService service;

    @Before
    public void init() {
        service = new ScheduledThreadPoolExecutor(3);
    }

    @After
    public void tearDown() throws Exception {
        service.shutdown();
        service.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testAlreadyAvailable() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setData(5);

        LinkedList<Integer> data = new LinkedList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(1, data.size());
        assertEquals(5, data.get(0).intValue());
    }

    @Test
    public void testExceptionAlreadyAvailable() throws Exception {

        Exception ex = new RuntimeException("An exception");

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setException(ex);

        LinkedList<Throwable> exceptions = new LinkedList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flowable.fromPublisher(pub)
                .subscribe(item -> {
                }, e -> {
                    exceptions.add(e);
                    latch.countDown();
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(1, exceptions.size());
        assertEquals(ex, exceptions.get(0));
    }

    @Test
    public void testAvailableSoon() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);

        LinkedList<Integer> data = new LinkedList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        Thread.yield();
        pub.setData(5);

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(1, data.size());
        assertEquals(5, data.get(0).intValue());
    }

    @Test
    public void testAvailableLater() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);

        LinkedList<Integer> data = new LinkedList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        Thread.sleep(200);
        pub.setData(5);

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(1, data.size());
        assertEquals(5, data.get(0).intValue());
    }

    @Test
    public void testMultipleSubscribers() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);

        ConcurrentLinkedDeque<Integer> data = new ConcurrentLinkedDeque<>();
        CountDownLatch latch = new CountDownLatch(2);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        Thread.sleep(200);
        pub.setData(5);

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(2, data.size());
        for (Integer n : data) {
            assertEquals(5, n.intValue());
        }
    }

    @Test
    public void testMultipleSubscribersMixedArrival() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);

        ConcurrentLinkedDeque<Integer> data = new ConcurrentLinkedDeque<>();
        CountDownLatch latch = new CountDownLatch(2);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        Thread.sleep(200);
        pub.setData(5);

        Flowable.fromPublisher(pub)
                .doOnNext(data::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(2, data.size());
        for (Integer n : data) {
            assertEquals(5, n.intValue());
        }
    }

    @Test
    public void testMultipleSubscribersMixedArrivalException() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);

        Exception ex = new RuntimeException("An exception");

        ConcurrentLinkedDeque<Throwable> exceptions = new ConcurrentLinkedDeque<>();
        CountDownLatch latch = new CountDownLatch(2);

        Flowable.fromPublisher(pub)
                .subscribe(item -> {
                }, e -> {
                    exceptions.add(e);
                    latch.countDown();
                });

        Thread.sleep(200);
        pub.setException(ex);

        Flowable.fromPublisher(pub)
                .subscribe(item -> {
                }, e -> {
                    exceptions.add(e);
                    latch.countDown();
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(2, exceptions.size());
        for (Throwable t : exceptions) {
            assertEquals(ex, t);
        }
    }

    @Test
    public void testDelayedRequest() throws Exception {

        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setData(2);

        BlockingQueue<Integer> queue = new LinkedBlockingDeque<>();

        TestSubscriber<Integer> sub = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer o) {
                queue.add(o);
            }
        };
        sub.setInitiallyRequested(0);

        pub.subscribe(sub);

        Thread.sleep(100);
        sub.request(1);

        Integer res = queue.poll(1, TimeUnit.SECONDS);
        assertEquals(new Integer(2), res);
    }

    @Test(expected = IllegalStateException.class)
    public void testDataOrExceptionAllowed() throws Exception {
        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        Exception ex = new RuntimeException("An exception");
        pub.setException(ex);
        pub.setData(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testDataOrExceptionAllowed2() throws Exception {
        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setData(1);
        Exception ex = new RuntimeException("An exception");
        pub.setException(ex);
    }

    @Test(expected = IllegalStateException.class)
    public void testOnlyOneDataAllowed() throws Exception {
        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setData(1);
        pub.setData(2);
    }

    @Test(expected = IllegalStateException.class)
    public void testOnlyOneExceptionAllowed() throws Exception {
        DelayedMonoPublisher<Integer> pub = new DelayedMonoPublisher<>(service);
        pub.setException(new RuntimeException("An exception"));
        pub.setException(new RuntimeException("An exception"));
    }

}
