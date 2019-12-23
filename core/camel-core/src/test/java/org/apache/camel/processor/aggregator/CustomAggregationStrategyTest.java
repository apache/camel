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
package org.apache.camel.processor.aggregator;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for using our own aggregation strategy.
 */
public class CustomAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testCustomAggregationStrategy() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect to find the two winners with the highest bid
        result.expectedBodiesReceivedInAnyOrder("200", "150");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "100", "id", "1");
        template.sendBodyAndHeader("direct:start", "150", "id", "2");
        template.sendBodyAndHeader("direct:start", "130", "id", "2");
        template.sendBodyAndHeader("direct:start", "200", "id", "1");
        template.sendBodyAndHeader("direct:start", "190", "id", "1");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending
                // the response to the mock
                from("direct:start")
                    // aggregated by header id and use our own strategy how to
                    // aggregate
                    .aggregate(new MyAggregationStrategy()).header("id")
                    // wait for 1 seconds to aggregate
                    .completionTimeout(1000L).to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e3
    private static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                // the first time we only have the new exchange so it wins the
                // first round
                return newExchange;
            }
            int oldPrice = oldExchange.getIn().getBody(Integer.class);
            int newPrice = newExchange.getIn().getBody(Integer.class);
            // return the "winner" that has the highest price
            return newPrice > oldPrice ? newExchange : oldExchange;
        }
    }
    // END SNIPPET: e3
}
