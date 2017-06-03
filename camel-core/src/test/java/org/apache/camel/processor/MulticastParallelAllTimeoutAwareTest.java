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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;

/**
 * @version 
 */
public class MulticastParallelAllTimeoutAwareTest extends ContextTestSupport {

    private volatile Exchange receivedExchange;
    private volatile int receivedIndex;
    private volatile int receivedTotal;
    private volatile long receivedTimeout;

    public void testMulticastParallelAllTimeoutAware() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // ABC will timeout so we only get our canned response
        mock.expectedBodiesReceived("AllTimeout");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        assertNotNull(receivedExchange);
        // Just make sure the MyAggregationStrategy is called for all the exchange
        assertEquals(2, receivedIndex);
        assertEquals(3, receivedTotal);
        assertEquals(500, receivedTimeout);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .multicast(new MyAggregationStrategy())
                        .parallelProcessing().timeout(500).to("direct:a", "direct:b", "direct:c")
                        // use end to indicate end of multicast route
                        .end()
                        .to("mock:result");

                from("direct:a").delay(1000).setBody(constant("A"));

                from("direct:b").delay(2000).setBody(constant("B"));

                from("direct:c").delay(1500).setBody(constant("C"));
            }
        };
    }

    private class MyAggregationStrategy implements TimeoutAwareAggregationStrategy {

        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            // we can't assert on the expected values here as the contract of this method doesn't
            // allow to throw any Throwable (including AssertionFailedError) so that we assert
            // about the expected values directly inside the test method itself. other than that
            // asserting inside a thread other than the main thread dosen't make much sense as
            // junit would not realize the failed assertion!
            receivedExchange = oldExchange;
            receivedIndex = index;
            receivedTotal = total;
            receivedTimeout = timeout;

            oldExchange.getIn().setBody("AllTimeout");
        }

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // noop
            return oldExchange;
        }
    }
}
