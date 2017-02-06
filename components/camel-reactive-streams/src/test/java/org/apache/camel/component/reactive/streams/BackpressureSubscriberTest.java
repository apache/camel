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

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test backpressure from the consumer side.
 */
public class BackpressureSubscriberTest extends CamelTestSupport {

    @Test
    public void testBackpressure() throws Exception {

        long start = System.currentTimeMillis();
        Observable.range(0, 10)
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribe(CamelReactiveStreams.get(context).streamSubscriber("slowNumbers", Integer.class));

        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(10);
        endpoint.assertIsSatisfied();
        long end = System.currentTimeMillis();

        // Maximum one inflight exchange, even if multiple consumer threads are present
        // Must take at least 50 * 10 = 500ms
        assertTrue("Exchange completed too early", end - start >= 500);
    }

    @Test
    public void testSlowerBackpressure() throws Exception {

        long start = System.currentTimeMillis();
        Observable.range(0, 2)
                .toFlowable(BackpressureStrategy.BUFFER)
                .subscribe(CamelReactiveStreams.get(context).streamSubscriber("slowerNumbers", Integer.class));

        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(2);
        endpoint.assertIsSatisfied();
        long end = System.currentTimeMillis();

        // Maximum one inflight exchange, even if multiple consumer threads are present
        // Must take at least 300 * 2 = 600ms
        assertTrue("Exchange completed too early", end - start >= 600);
    }

    @Test
    public void testParallelSlowBackpressure() throws Exception {

        long start = System.currentTimeMillis();
        Flowable.range(0, 40)
                .subscribe(CamelReactiveStreams.get(context).streamSubscriber("parallelSlowNumbers", Integer.class));

        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(40);
        endpoint.assertIsSatisfied();
        long end = System.currentTimeMillis();

        // Maximum 5 inflight exchanges
        // Must take at least 100 * (40 / 5) = 800ms
        assertTrue("Exchange completed too early", end - start >= 800);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:slowNumbers?concurrentConsumers=10&maxInflightExchanges=1")
                        .process(x -> Thread.sleep(50))
                        .to("mock:endpoint");

                from("reactive-streams:slowerNumbers?concurrentConsumers=10&maxInflightExchanges=1")
                        .process(x -> Thread.sleep(300))
                        .to("mock:endpoint");

                from("reactive-streams:parallelSlowNumbers?concurrentConsumers=10&maxInflightExchanges=5")
                        .process(x -> Thread.sleep(100))
                        .to("mock:endpoint");
            }
        };
    }
}
