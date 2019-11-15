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
package org.apache.camel.component.reactor.engine;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.ReactiveStreamsBackpressureStrategy;
import org.apache.camel.component.reactor.engine.suport.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;

public class ReactorStreamsServiceBackpressureTest extends ReactorStreamsServiceTestSupport {

    @Test
    public void testBufferStrategy() throws Exception {
        getReactiveStreamsComponent().setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.BUFFER);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                    .setBody()
                    .header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:integers");
            }
        });

        Flux<Integer> integers = Flux.from(crs.fromStream("integers", Integer.class));
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flux.range(0, 50)
            .zipWith(integers, (l, i) -> i)
            .timeout(Duration.ofMillis(2000), Flux.empty())
            .doOnComplete(latch::countDown)
            .subscribe(queue::add);

        context.start();

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(20, queue.size());

        int num = 1;
        for (int i : queue) {
            Assert.assertEquals(num++, i);
        }
    }


    @Test
    public void testDropStrategy() throws Exception {
        getReactiveStreamsComponent().setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.OLDEST);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                    .setBody()
                    .header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:integers");
            }
        });

        final ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
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

        crs.fromStream("integers", Integer.class).subscribe(subscriber);
        context.start();

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000); // wait for all numbers to be generated

        subscriber.request(19);
        Assert.assertTrue(latch2.await(1, TimeUnit.SECONDS));

        Thread.sleep(200); // add other time to ensure no other items arrive
        Assert.assertEquals(2, queue.size());

        int sum = queue.stream().reduce((i, j) -> i + j).get();
        Assert.assertEquals(3, sum); // 1 + 2 = 3

        subscriber.cancel();
    }

    @Test
    public void testLatestStrategy() throws Exception {
        getReactiveStreamsComponent().setBackpressureStrategy(ReactiveStreamsBackpressureStrategy.LATEST);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:gen?period=20&repeatCount=20")
                    .setBody()
                    .header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:integers");
            }
        });

        final ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);

        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer o) {
                queue.add(o);
                latch1.countDown();
                latch2.countDown();
            }
        };

        subscriber.setInitiallyRequested(1);

        crs.fromStream("integers", Integer.class).subscribe(subscriber);
        context.start();

        Assert.assertTrue(latch1.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000); // wait for all numbers to be generated

        subscriber.request(19);
        Assert.assertTrue(latch2.await(1, TimeUnit.SECONDS));

        Thread.sleep(200); // add other time to ensure no other items arrive

        // TODO: the chain caches two elements instead of one: change it if you find an EmitterProcessor without prefetch
        // Assert.assertEquals(2, queue.size());
        Assert.assertEquals(3, queue.size());

        int sum = queue.stream().reduce((i, j) -> i + j).get();
        // Assert.assertEquals(21, sum); // 1 + 20 = 21
        Assert.assertEquals(23, sum); // 1 + 2 + 20 = 23

        subscriber.cancel();
    }
}
