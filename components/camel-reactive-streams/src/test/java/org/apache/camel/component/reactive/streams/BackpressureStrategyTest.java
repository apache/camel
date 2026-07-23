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
package org.apache.camel.component.reactive.streams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Flowable;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.support.TestSubscriber;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackpressureStrategyTest extends BaseReactiveTest {

    @Test
    public void testBackpressureBufferStrategy() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:gen?period=20&repeatCount=20&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:integers");
            }
        }.addRoutesToCamelContext(context);

        Flowable<Integer> integers
                = Flowable.fromPublisher(CamelReactiveStreams.get(context).fromStream("integers", Integer.class));

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

        AtomicInteger timerCount = new AtomicInteger(0);

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:gen?period=20&repeatCount=20&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .process(e -> timerCount.incrementAndGet())
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
        // Wait for all 20 timer events to be processed
        await().atMost(5, TimeUnit.SECONDS).until(() -> timerCount.get() >= 20);

        subscriber.request(19);
        assertTrue(latch2.await(2, TimeUnit.SECONDS));
        // Verify exactly 2 items received and no more arrive
        // Use during() to ensure queue size remains stable at 2
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .until(() -> queue.size() == 2);
        // Verify OLDEST strategy behavior:
        // - First item is always 1 (OLDEST keeps the first buffered item)
        // - Second item is an early value (the first item buffered after the initial flush)
        // The exact second value depends on flush timing vs timer events,
        // but it must be > 1 and much less than 20 (the last timer event)
        List<Integer> items = new ArrayList<>(queue);
        assertEquals(1, items.get(0).intValue(), "OLDEST strategy should deliver item 1 first");
        assertTrue(items.get(1) > 1 && items.get(1) < 20,
                "OLDEST strategy should keep an early buffered item, got: " + items.get(1));

        subscriber.cancel();
    }

    @Test
    public void testBackpressureLatestStrategy() throws Exception {

        ReactiveStreamsComponent comp = (ReactiveStreamsComponent) context().getComponent("reactive-streams");
        comp.setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.LATEST);

        AtomicInteger timerCount = new AtomicInteger(0);

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:gen?period=20&repeatCount=20&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .process(e -> timerCount.incrementAndGet())
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
        // Wait for all 20 timer events to be processed
        await().atMost(5, TimeUnit.SECONDS).until(() -> timerCount.get() >= 20);

        subscriber.request(19);
        assertTrue(latch2.await(2, TimeUnit.SECONDS));
        // Verify exactly 2 items received and no more arrive
        // Use during() to ensure queue size remains stable at 2
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .until(() -> queue.size() == 2);
        // Verify LATEST strategy behavior:
        // - Second item is always 20 (LATEST always replaces with the newest)
        // - First item depends on flush timing but is always < 20
        List<Integer> items = new ArrayList<>(queue);
        assertTrue(items.get(0) >= 1 && items.get(0) < 20,
                "First item should be less than 20, got: " + items.get(0));
        assertEquals(20, items.get(1).intValue(), "LATEST strategy should keep the most recent item (20)");

        subscriber.cancel();
    }

    @Test
    public void testBackpressureDropStrategyInEndpoint() throws Exception {

        AtomicInteger timerCount = new AtomicInteger(0);

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:gen?period=20&repeatCount=20&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .process(e -> timerCount.incrementAndGet())
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
        // Wait for all 20 timer events to be processed
        await().atMost(5, TimeUnit.SECONDS).until(() -> timerCount.get() >= 20);

        subscriber.request(19);
        assertTrue(latch2.await(2, TimeUnit.SECONDS));
        // Verify exactly 2 items received and no more arrive
        // Use during() to ensure queue size remains stable at 2
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .until(() -> queue.size() == 2);
        // Verify OLDEST strategy behavior (same invariant as testBackpressureDropStrategy):
        // - First item is always 1, second is an early buffered item
        List<Integer> items = new ArrayList<>(queue);
        assertEquals(1, items.get(0).intValue(), "OLDEST strategy should deliver item 1 first");
        assertTrue(items.get(1) > 1 && items.get(1) < 20,
                "OLDEST strategy should keep an early buffered item, got: " + items.get(1));

        subscriber.cancel();
    }
}
