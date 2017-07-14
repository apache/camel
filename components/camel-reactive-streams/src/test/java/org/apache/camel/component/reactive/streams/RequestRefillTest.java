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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * Test the number of refill requests that are sent to a published from a Camel consumer.
 */
public class RequestRefillTest extends CamelTestSupport {

    @Test
    public void testUnboundedRequests() throws Exception {
        int numReqs = 100;
        List<Long> requests = executeTest("unbounded", numReqs);
        assertEquals(1, requests.size());
        assertEquals(Long.MAX_VALUE, requests.get(0).longValue());
    }

    @Test
    public void testUnboundedRequestsWatermarkNoEffect() throws Exception {
        int numReqs = 100;
        List<Long> requests = executeTest("unbounded-100", numReqs);
        assertEquals(1, requests.size());
        assertEquals(Long.MAX_VALUE, requests.get(0).longValue());
    }

    @Test
    public void testBoundedRequests() throws Exception {
        int numReqs = 100;
        List<Long> requests = executeTest("bounded", numReqs);
        assertTrue(requests.size() >= numReqs / 10);
    }

    @Test
    public void testBoundedRequestsPercentageRefill() throws Exception {
        int numReqs = 120;
        List<Long> requests0 = executeTest("bounded-0", numReqs);
        List<Long> requests10 = executeTest("bounded-10", numReqs);
        List<Long> requests25 = executeTest("bounded", numReqs);
        List<Long> requests80 = executeTest("bounded-80", numReqs);
        List<Long> requests100 = executeTest("bounded-100", numReqs);

        assertTrue(requests0.size() <= requests10.size()); // too close
        assertTrue(requests10.size() < requests25.size());
        assertTrue(requests25.size() < requests80.size());
        assertTrue(requests80.size() < requests100.size());
    }

    private List<Long> executeTest(String name, int numReqs) throws InterruptedException {
        List<Long> requests = Collections.synchronizedList(new LinkedList<>());
        Publisher<Long> nums = createPublisher(numReqs, requests);

        MockEndpoint mock = getMockEndpoint("mock:" + name + "-endpoint");
        mock.expectedMessageCount(numReqs);

        CamelReactiveStreamsService rxCamel = CamelReactiveStreams.get(context());
        nums.subscribe(rxCamel.streamSubscriber(name, Long.class));

        mock.assertIsSatisfied();

        Long sum = mock.getExchanges().stream().map(x -> x.getIn().getBody(Long.class)).reduce((l, r) -> l + r).get();
        assertEquals(numReqs * (numReqs + 1) / 2, sum.longValue());
        return requests;
    }

    private Publisher<Long> createPublisher(final int numReqs, final List<Long> requests) {
        return Flux.range(1, numReqs).map(Long::valueOf).doOnRequest(requests::add);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:unbounded?maxInflightExchanges=-1")
                        .delayer(1)
                        .to("mock:unbounded-endpoint");

                from("reactive-streams:unbounded-100?maxInflightExchanges=-1&exchangesRefillLowWatermark=1")
                        .delayer(1)
                        .to("mock:unbounded-100-endpoint");

                from("reactive-streams:bounded?maxInflightExchanges=10")
                        .delayer(1)
                        .to("mock:bounded-endpoint");

                from("reactive-streams:bounded-0?maxInflightExchanges=10&exchangesRefillLowWatermark=0")
                        .delayer(1)
                        .to("mock:bounded-0-endpoint");

                from("reactive-streams:bounded-10?maxInflightExchanges=10&exchangesRefillLowWatermark=0.1")
                        .delayer(1)
                        .to("mock:bounded-10-endpoint");

                from("reactive-streams:bounded-80?maxInflightExchanges=10&exchangesRefillLowWatermark=0.8")
                        .delayer(1)
                        .to("mock:bounded-80-endpoint");

                from("reactive-streams:bounded-100?maxInflightExchanges=10&exchangesRefillLowWatermark=1")
                        .delayer(1)
                        .to("mock:bounded-100-endpoint");
            }
        };
    }
}