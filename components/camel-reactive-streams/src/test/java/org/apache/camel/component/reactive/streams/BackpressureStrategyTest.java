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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.support.TestSubscriber;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BackpressureStrategyTest extends CamelTestSupport {

    @Test
    public void testBackpressureBufferStrategy() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:integers");
            }
        }.addRoutesToCamelContext(context);

        Flowable<Integer> integers = Flowable.fromPublisher(CamelReactiveStreams.get(context).fromStream("integers", Integer.class));

        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flowable.interval(0, 50, TimeUnit.MILLISECONDS)
                .zipWith(integers, (l, i) -> i)
                .timeout(2000, TimeUnit.MILLISECONDS, Flowable.empty())
                .doOnComplete(latch::countDown)
                .subscribe(queue::add);

        context().start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(20, queue.size());
        int num = 1;
        for (int i : queue) {
            assertEquals(num++, i);
        }
    }

    @Test
    public void testBackpressureDropStrategy() throws Exception {

        ReactiveStreamsComponent comp = (ReactiveStreamsComponent) context().getComponent("reactive-streams");
        comp.setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.OLDEST);

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:integers");
            }
        }.addRoutesToCamelContext(context);


        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);

        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer o) {
                queue.add(o);
                latch.countDown();
                latch2.countDown();
            }
        };
        subscriber.setInitiallyRequested(1);
        CamelReactiveStreams.get(context).fromStream("integers", Integer.class).subscribe(subscriber);

        context().start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000); // wait for all numbers to be generated

        subscriber.request(19);
        assertTrue(latch2.await(1, TimeUnit.SECONDS));
        Thread.sleep(200); // add other time to ensure no other items arrive
        assertEquals(2, queue.size());
        int sum = queue.stream().reduce((i, j) -> i + j).get();
        assertEquals(3, sum); // 1 + 2 = 3

        subscriber.cancel();
    }

    @Test
    public void testBackpressureLatestStrategy() throws Exception {

        ReactiveStreamsComponent comp = (ReactiveStreamsComponent) context().getComponent("reactive-streams");
        comp.setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.LATEST);

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:integers");
            }
        }.addRoutesToCamelContext(context);


        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);

        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer o) {
                queue.add(o);
                latch.countDown();
                latch2.countDown();
            }
        };
        subscriber.setInitiallyRequested(1);
        CamelReactiveStreams.get(context).fromStream("integers", Integer.class).subscribe(subscriber);

        context().start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000); // wait for all numbers to be generated

        subscriber.request(19);
        assertTrue(latch2.await(1, TimeUnit.SECONDS));
        Thread.sleep(200); // add other time to ensure no other items arrive
        assertEquals(2, queue.size());
        int sum = queue.stream().reduce((i, j) -> i + j).get();
        assertEquals(21, sum); // 1 + 20 = 21

        subscriber.cancel();
    }

    @Test
    public void testBackpressureDropStrategyInEndpoint() throws Exception {
        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:integers?backpressureStrategy=OLDEST");
            }
        }.addRoutesToCamelContext(context);


        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);

        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer o) {
                queue.add(o);
                latch.countDown();
                latch2.countDown();
            }
        };
        subscriber.setInitiallyRequested(1);
        CamelReactiveStreams.get(context).fromStream("integers", Integer.class).subscribe(subscriber);

        context().start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000); // wait for all numbers to be generated

        subscriber.request(19);
        assertTrue(latch2.await(1, TimeUnit.SECONDS));
        Thread.sleep(200); // add other time to ensure no other items arrive
        assertEquals(2, queue.size());
        int sum = queue.stream().reduce((i, j) -> i + j).get();
        assertEquals(3, sum); // 1 + 2 = 3

        subscriber.cancel();
    }
}
