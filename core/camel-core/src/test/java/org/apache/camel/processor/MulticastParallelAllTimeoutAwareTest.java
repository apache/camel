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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Isolated("Does not play well with parallel execution")
public class MulticastParallelAllTimeoutAwareTest extends ContextTestSupport {

    private volatile Exchange receivedExchange;
    private volatile int receivedIndex;
    private volatile int receivedTotal;
    private volatile long receivedTimeout;

    // holds the branch that must time out until the test is done: it cannot complete before the timeout,
    // and it does not keep the graceful shutdown of the CamelContext waiting
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseTimedOutBranch() {
        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
    }

    @Test
    public void testMulticastParallelAllTimeoutAware() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // ABC will timeout so we only get our canned response
        mock.expectedBodiesReceived("AllTimeout");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        assertNotNull(receivedExchange);
        // Just make sure the MyAggregationStrategy is called for all the
        // exchange
        assertEquals(2, receivedIndex);
        assertEquals(3, receivedTotal);
        assertEquals(500, receivedTimeout);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").multicast(new MyAggregationStrategy()).parallelProcessing().timeout(500)
                        .to("direct:a", "direct:b", "direct:c")
                        // use end to indicate end of multicast route
                        .end().to("mock:result");

                from("direct:a").process(e -> release.await(30, TimeUnit.SECONDS)).setBody(constant("A"));

                from("direct:b").process(e -> release.await(30, TimeUnit.SECONDS)).setBody(constant("B"));

                from("direct:c").process(e -> release.await(30, TimeUnit.SECONDS)).setBody(constant("C"));
            }
        };
    }

    private class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            // we can't assert on the expected values here as the contract of
            // this method doesn't
            // allow to throw any Throwable (including AssertionError) so that
            // we assert
            // about the expected values directly inside the test method itself.
            // other than that
            // asserting inside a thread other than the main thread doesn't make
            // much sense as
            // junit would not realize the failed assertion!
            receivedExchange = oldExchange;
            receivedIndex = index;
            receivedTotal = total;
            receivedTimeout = timeout;

            oldExchange.getIn().setBody("AllTimeout");
        }

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // noop
            return oldExchange;
        }
    }
}
