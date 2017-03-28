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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test the behaviour of the consumer side when using a different number of consumer threads.
 */
public class ConcurrentConsumersTest extends CamelTestSupport {

    @Test
    public void testSingleConsumer() throws Exception {

        Observable.intervalRange(0, 1000, 0, 300, TimeUnit.MICROSECONDS)
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribe(CamelReactiveStreams.get(context()).streamSubscriber("singleConsumer", Long.class));

        MockEndpoint endpoint = getMockEndpoint("mock:singleBucket");
        endpoint.expectedMessageCount(1000);
        endpoint.assertIsSatisfied();

        Set<String> threads = endpoint.getExchanges().stream()
                .map(x -> x.getIn().getHeader("thread", String.class))
                .collect(Collectors.toSet());
        assertEquals(1, threads.size());

        // Ensure order is preserved when using a single consumer
        List<Long> nums = endpoint.getExchanges().stream()
                .map(x -> x.getIn().getBody(Long.class))
                .collect(Collectors.toList());

        long prev = -1;
        for (long n : nums) {
            assertEquals(prev + 1, n);
            prev = n;
        }
    }

    @Test
    public void testMultipleConsumers() throws Exception {

        Observable.intervalRange(0, 1000, 0, 300, TimeUnit.MICROSECONDS)
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribe(CamelReactiveStreams.get(context()).streamSubscriber("multipleConsumers", Long.class));

        MockEndpoint endpoint = getMockEndpoint("mock:multipleBucket");
        endpoint.expectedMessageCount(1000);
        endpoint.assertIsSatisfied();

        Set<String> threads = endpoint.getExchanges().stream()
                .map(x -> x.getIn().getHeader("thread", String.class))
                .collect(Collectors.toSet());

        assertEquals(3, threads.size());
        // Order cannot be preserved when using multiple consumers
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("reactive-streams:singleConsumer")
                        .process(x -> x.getIn().setHeader("thread", Thread.currentThread().getId()))
                        .to("mock:singleBucket");

                from("reactive-streams:multipleConsumers?concurrentConsumers=3")
                        .process(x -> x.getIn().setHeader("thread", Thread.currentThread().getId()))
                        .to("mock:multipleBucket");

            }
        };
    }
}
