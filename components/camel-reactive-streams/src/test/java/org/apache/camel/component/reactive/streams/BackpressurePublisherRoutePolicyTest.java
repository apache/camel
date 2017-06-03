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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.support.TestSubscriber;
import org.apache.camel.impl.ThrottlingInflightRoutePolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;

public class BackpressurePublisherRoutePolicyTest extends CamelTestSupport {

    @Test
    public void testThatBackpressureCausesTemporaryRouteStop() throws Exception {

        CountDownLatch generationLatch = new CountDownLatch(25);

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
                policy.setMaxInflightExchanges(10);
                policy.setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.Route);
                policy.setResumePercentOfMax(70);
                policy.setLoggingLevel(LoggingLevel.INFO);

                from("timer:tick?period=50&repeatCount=35")
                        .id("policy-route")
                        .routePolicy(policy)
                        .process(x -> generationLatch.countDown())
                        .to("reactive-streams:pub");
            }
        }.addRoutesToCamelContext(context);

        CountDownLatch receptionLatch = new CountDownLatch(35);

        Publisher<Exchange> pub = CamelReactiveStreams.get(context()).fromStream("pub", Exchange.class);
        TestSubscriber<Exchange> subscriber = new TestSubscriber<Exchange>() {
            @Override
            public void onNext(Exchange o) {
                super.onNext(o);
                receptionLatch.countDown();
            }
        };
        subscriber.setInitiallyRequested(10);
        pub.subscribe(subscriber);

        // Add another (fast) subscription that should not affect the backpressure on the route
        Observable.fromPublisher(pub)
                .subscribe();

        context.start();

        generationLatch.await(5, TimeUnit.SECONDS); // after 25 messages are generated
        // The number of exchanges should be 10 (requested by the subscriber), so 35-10=25
        assertEquals(25, receptionLatch.getCount());

        // fire a delayed request from the subscriber (required by camel core)
        subscriber.request(1);
        Thread.sleep(250);

        StatefulService service = (StatefulService) context().getRoute("policy-route").getConsumer();
        // ensure the route is stopped or suspended
        assertTrue(service.isStopped() || service.isSuspended());

        // request all the remaining exchanges
        subscriber.request(24);
        assertTrue(receptionLatch.await(5, TimeUnit.SECONDS));
        // The reception latch has gone to 0
    }

    @Test
    public void testThatRouteRestartsOnUnsubscription() throws Exception {

        CountDownLatch generationLatch = new CountDownLatch(25);

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
                policy.setMaxInflightExchanges(10);
                policy.setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.Route);
                policy.setResumePercentOfMax(70);
                policy.setLoggingLevel(LoggingLevel.INFO);

                from("timer:tick?period=50") // unbounded
                        .id("policy-route")
                        .routePolicy(policy)
                        .process(x -> generationLatch.countDown())
                        .to("reactive-streams:pub");
            }
        }.addRoutesToCamelContext(context);

        CountDownLatch receptionLatch = new CountDownLatch(35);

        Publisher<Exchange> pub = CamelReactiveStreams.get(context()).fromStream("pub", Exchange.class);
        TestSubscriber<Exchange> subscriber = new TestSubscriber<Exchange>() {
            @Override
            public void onNext(Exchange o) {
                super.onNext(o);
                receptionLatch.countDown();
            }
        };
        subscriber.setInitiallyRequested(10);
        pub.subscribe(subscriber);

        // Add another (fast) subscription that should not affect the backpressure on the route
        Observable.fromPublisher(pub)
                .subscribe();

        context.start();

        generationLatch.await(5, TimeUnit.SECONDS); // after 25 messages are generated
        // The number of exchanges should be 10 (requested by the subscriber), so 35-10=25
        assertEquals(25, receptionLatch.getCount());

        // fire a delayed request from the subscriber (required by camel core)
        subscriber.request(1);
        Thread.sleep(250);

        StatefulService service = (StatefulService) context().getRoute("policy-route").getConsumer();
        // ensure the route is stopped or suspended
        assertTrue(service.isStopped() || service.isSuspended());
        subscriber.cancel();

        // request other exchanges to ensure that the route works
        CountDownLatch latch = new CountDownLatch(20);
        Observable.fromPublisher(pub)
                .subscribe(n -> {
                    latch.countDown();
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
