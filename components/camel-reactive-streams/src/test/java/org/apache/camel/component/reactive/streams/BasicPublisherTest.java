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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicPublisherTest extends BaseReactiveTest {

    @Test
    public void testWorking() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick?period=5&repeatCount=30&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:pub");
            }
        }.addRoutesToCamelContext(context);

        int num = 30;
        CountDownLatch latch = new CountDownLatch(num);
        List<Integer> recv = new LinkedList<>();

        Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("pub", Integer.class))
                .doOnNext(recv::add)
                .doOnNext(n -> latch.countDown())
                .subscribe();

        context.start();
        latch.await(5, TimeUnit.SECONDS);

        assertEquals(num, recv.size());
        for (int i = 1; i <= num; i++) {
            assertEquals(i, recv.get(i - 1).intValue());
        }
    }

    @Test
    public void testMultipleSubscriptions() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick?period=50&includeMetadata=true")
                        .setBody().header(Exchange.TIMER_COUNTER)
                        .to("reactive-streams:unbounded");
            }
        }.addRoutesToCamelContext(context);

        CountDownLatch latch1 = new CountDownLatch(5);
        Disposable disp1 = Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("unbounded", Integer.class))
                .subscribe(n -> latch1.countDown());

        context.start();

        // Add another subscription
        CountDownLatch latch2 = new CountDownLatch(5);
        Disposable disp2 = Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("unbounded", Integer.class))
                .subscribe(n -> latch2.countDown());

        assertTrue(latch1.await(5, TimeUnit.SECONDS));
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        // Unsubscribe both
        disp1.dispose();
        disp2.dispose();

        // No active subscriptions, warnings expected
        Thread.sleep(60);

        // Add another subscription
        CountDownLatch latch3 = new CountDownLatch(5);
        Disposable disp3 = Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("unbounded", Integer.class))
                .subscribe(n -> latch3.countDown());

        assertTrue(latch3.await(5, TimeUnit.SECONDS));
        disp3.dispose();
    }

    @Test
    public void testOnlyOneCamelProducerPerPublisher() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:one")
                        .to("reactive-streams:stream");

                from("direct:two")
                        .to("reactive-streams:stream");
            }
        }.addRoutesToCamelContext(context);

        assertThrows(FailedToStartRouteException.class,
                () -> context.start());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
