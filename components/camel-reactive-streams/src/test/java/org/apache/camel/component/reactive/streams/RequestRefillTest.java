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
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.support.TestPublisher;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;

/**
 * Test the number of refill requests that are sent to a published from a Camel consumer.
 */
public class RequestRefillTest extends CamelTestSupport {

    @Test
    public void testUnboundedRequests() throws Exception {

        final int numReqs = 100;

        List<Long> requests = Collections.synchronizedList(new LinkedList<>());
        Publisher<Long> nums = createPublisher(numReqs, requests);

        MockEndpoint mock = getMockEndpoint("mock:unbounded-endpoint");
        mock.expectedMessageCount(numReqs);

        CamelReactiveStreamsService rxCamel = CamelReactiveStreams.get(context());
        nums.subscribe(rxCamel.streamSubscriber("unbounded", Long.class));

        mock.assertIsSatisfied();
        assertEquals(1, requests.size());
        assertEquals(Long.MAX_VALUE, requests.get(0).longValue());
        Long sum = mock.getExchanges().stream().map(x -> x.getIn().getBody(Long.class)).reduce((l, r) -> l + r).get();
        assertEquals(numReqs * (numReqs + 1) / 2, sum.longValue());
    }

    @Test
    public void testBoundedRequests() throws Exception {

        final int numReqs = 100;

        List<Long> requests = Collections.synchronizedList(new LinkedList<>());
        Publisher<Long> nums = createPublisher(numReqs, requests);

        MockEndpoint mock = getMockEndpoint("mock:bounded-endpoint");
        mock.expectedMessageCount(numReqs);

        CamelReactiveStreamsService rxCamel = CamelReactiveStreams.get(context());
        nums.subscribe(rxCamel.streamSubscriber("bounded", Long.class));

        mock.assertIsSatisfied();

        assertTrue(requests.size() >= numReqs / 10);
        Long sum = mock.getExchanges().stream().map(x -> x.getIn().getBody(Long.class)).reduce((l, r) -> l + r).get();
        assertEquals(numReqs * (numReqs + 1) / 2, sum.longValue());
    }

    private Publisher<Long> createPublisher(final int numReqs, final List<Long> requests) {
        return new TestPublisher<>(LongStream.rangeClosed(1, numReqs).boxed().collect(Collectors.toList()), 0, requests::add);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:unbounded?maxInflightExchanges=-1")
                        .to("mock:unbounded-endpoint");

                from("reactive-streams:bounded?maxInflightExchanges=10")
                        .to("mock:bounded-endpoint");
            }
        };
    }
}