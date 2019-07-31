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

import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;

public class ReactorStreamsServiceSubscriberTest extends ReactorStreamsServiceTestSupport {
    @Test
    public void testSubscriber() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:sub1")
                    .to("mock:sub1");
                from("reactive-streams:sub2")
                    .to("mock:sub2");
                from("timer:tick?period=50")
                    .setBody()
                        .simple("${random(500)}")
                    .to("mock:sub3")
                    .to("reactive-streams:pub");
            }
        });

        Subscriber<Integer> sub1 = crs.streamSubscriber("sub1", Integer.class);
        Subscriber<Integer> sub2 = crs.streamSubscriber("sub2", Integer.class);
        Publisher<Integer> pub = crs.fromStream("pub", Integer.class);

        pub.subscribe(sub1);
        pub.subscribe(sub2);

        context.start();

        int count = 2;

        MockEndpoint e1 = getMockEndpoint("mock:sub1");
        e1.expectedMinimumMessageCount(count);
        e1.assertIsSatisfied();

        MockEndpoint e2 = getMockEndpoint("mock:sub2");
        e2.expectedMinimumMessageCount(count);
        e2.assertIsSatisfied();

        MockEndpoint e3 = getMockEndpoint("mock:sub3");
        e3.expectedMinimumMessageCount(count);
        e3.assertIsSatisfied();

        for (int i = 0; i < count; i++) {
            Exchange ex1 = e1.getExchanges().get(i);
            Exchange ex2 = e2.getExchanges().get(i);
            Exchange ex3 = e3.getExchanges().get(i);

            assertEquals(ex1.getIn().getBody(), ex2.getIn().getBody());
            assertEquals(ex1.getIn().getBody(), ex3.getIn().getBody());
        }
    }

    @Test
    public void testSingleConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:singleConsumer")
                    .process()
                        .message(m -> m.setHeader("thread", Thread.currentThread().getId()))
                    .to("mock:singleBucket");
            }
        });

        context.start();

        Flux.range(0, 1000).subscribe(
            crs.streamSubscriber("singleConsumer", Number.class)
        );

        MockEndpoint endpoint = getMockEndpoint("mock:singleBucket");
        endpoint.expectedMessageCount(1000);
        endpoint.assertIsSatisfied();

        Assert.assertEquals(
            1,
            endpoint.getExchanges().stream()
                .map(x -> x.getIn().getHeader("thread", String.class))
                .distinct()
                .count()
        );

        // Ensure order is preserved when using a single consumer
        AtomicLong num = new AtomicLong(0);

        endpoint.getExchanges().stream()
            .map(x -> x.getIn().getBody(Long.class))
            .forEach(n -> Assert.assertEquals(num.getAndIncrement(), n.longValue()));
    }

    @Test
    public void testMultipleConsumers() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:multipleConsumers?concurrentConsumers=3")
                    .process()
                        .message(m -> m.setHeader("thread", Thread.currentThread().getId()))
                    .to("mock:multipleBucket");
            }
        });

        context.start();

        Flux.range(0, 1000).subscribe(
            crs.streamSubscriber("multipleConsumers", Number.class)
        );

        MockEndpoint endpoint = getMockEndpoint("mock:multipleBucket");
        endpoint.expectedMessageCount(1000);
        endpoint.assertIsSatisfied();

        Assert.assertEquals(
            3,
            endpoint.getExchanges().stream()
                .map(x -> x.getIn().getHeader("thread", String.class))
                .distinct()
                .count()
        );
        // Order cannot be preserved when using multiple consumers
    }
}
