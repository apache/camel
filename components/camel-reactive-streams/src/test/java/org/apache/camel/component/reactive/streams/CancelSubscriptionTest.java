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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.engine.CamelSubscription;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelSubscriptionTest extends BaseReactiveTest {

    @Test
    void testCancelInOnNextCompletesTheOtherExchanges() throws Exception {
        TestSubscriber subscriber = new TestSubscriber(true);
        CamelReactiveStreams.get(context).fromStream("numbers").subscribe(subscriber);
        await().atMost(5, TimeUnit.SECONDS).until(() -> subscriber.subscription != null);

        List<CompletableFuture<Exchange>> sent = sendAndWaitBuffered(subscriber, 3);

        // the subscriber takes the three exchanges at once and cancels when it sees the first one
        subscriber.subscription.request(3);

        for (int i = 0; i < sent.size(); i++) {
            CompletableFuture<Exchange> future = sent.get(i);
            int n = i + 1;
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertTrue(future.isDone(), "Exchange " + n + " never completed"));
        }
        assertEquals(List.of(1), subscriber.received);
        assertNull(sent.get(0).get().getException());
        assertInstanceOf(IllegalStateException.class, sent.get(1).get().getException());
        assertInstanceOf(IllegalStateException.class, sent.get(2).get().getException());
    }

    @Test
    void testNonPositiveRequestCompletesTheBufferedExchanges() throws Exception {
        TestSubscriber subscriber = new TestSubscriber(false);
        CamelReactiveStreams.get(context).fromStream("numbers").subscribe(subscriber);
        await().atMost(5, TimeUnit.SECONDS).until(() -> subscriber.subscription != null);

        List<CompletableFuture<Exchange>> sent = sendAndWaitBuffered(subscriber, 2);

        // a request of 0 terminates the subscription with an error (rule 3.9 of the specification)
        subscriber.subscription.request(0);

        assertInstanceOf(IllegalArgumentException.class, subscriber.error);
        for (CompletableFuture<Exchange> future : sent) {
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertTrue(future.isDone(), "A buffered exchange never completed"));
            assertInstanceOf(IllegalStateException.class, future.get().getException());
        }
        assertEquals(List.of(), subscriber.received);
    }

    private List<CompletableFuture<Exchange>> sendAndWaitBuffered(TestSubscriber subscriber, int count) {
        List<CompletableFuture<Exchange>> sent = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int n = i;
            sent.add(template.asyncSend("direct:numbers", e -> e.getIn().setBody(n)));
            // the subscriber has requested nothing yet, so the exchange stays in the buffer of its subscription
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> ((CamelSubscription) subscriber.subscription).getBufferSize() == n);
        }
        return sent;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:numbers")
                        .to("reactive-streams:numbers");
            }
        };
    }

    private static final class TestSubscriber implements Subscriber<Exchange> {

        private final boolean cancelOnFirst;
        private final List<Integer> received = new CopyOnWriteArrayList<>();
        private volatile Subscription subscription;
        private volatile Throwable error;

        private TestSubscriber(boolean cancelOnFirst) {
            this.cancelOnFirst = cancelOnFirst;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(Exchange exchange) {
            received.add(exchange.getIn().getBody(Integer.class));
            if (cancelOnFirst) {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            // noop
        }
    }
}
