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
import org.junit.Test;

/**
 * To test CAMEL-10474
 */
public class AggregateForceCompletionHeaderInAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testCompletePreviousOnNewGroup() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("AAA", "BB");
        getMockEndpoint("mock:aggregated").allMessages().header(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS).isNull();
        getMockEndpoint("mock:aggregated").allMessages().exchangeProperty(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS).isNull();

        template.sendBody("direct:start", "A,A,A,B,B");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body()).to("log:input?showAll=true").aggregate(simple("${body}"), new MyAggregationStrategy())
                    .completionPredicate(exchangeProperty(Exchange.SPLIT_COMPLETE)).to("log:aggregated", "mock:aggregated");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                // we start a new correlation group, so complete all previous
                // groups
                newExchange.setProperty(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS, true);
                return newExchange;
            }

            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            // copy over flag to know when splitting is done on the old exchange
            oldExchange.setProperty(Exchange.SPLIT_COMPLETE, newExchange.getProperty(Exchange.SPLIT_COMPLETE));
            return oldExchange;
        }
    }
}
