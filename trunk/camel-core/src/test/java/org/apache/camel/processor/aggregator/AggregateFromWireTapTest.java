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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class AggregateFromWireTapTest extends ContextTestSupport {

    public void testAggregateFromWireTap() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("A", "B");

        MockEndpoint aggregated = getMockEndpoint("mock:aggregated");
        aggregated.expectedMessageCount(1);

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");

        assertMockEndpointsSatisfied();

        String body = aggregated.getReceivedExchanges().get(0).getIn().getBody(String.class);
        // should be either AB or BA (wiretap can be run out of order)
        assertTrue("Should be AB or BA, was: " + body, "AB".equals(body) || "BA".equals(body));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start")
                    .wireTap("direct:tap")
                    .to("mock:end");

                from("direct:tap")
                    // just use a constant correlation expression as we want to agg everything
                    // in the same group. set batch size to two which means to fire when we
                    // have aggregated 2 messages, if not the timeout of 5 sec will kick in
                    .aggregate(constant(true), new MyAggregationStrategy())
                        .completionSize(2).completionTimeout(5000L)
                            .to("direct:aggregated")
                    .end();

                from("direct:aggregated")
                    .to("mock:aggregated");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String oldBody = oldExchange.getIn().getBody(String.class);
            String newBody = newExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(oldBody + newBody);
            return oldExchange;
        }
    }

}