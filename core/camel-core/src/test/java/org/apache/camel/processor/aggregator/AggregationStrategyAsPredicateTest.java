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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class AggregationStrategyAsPredicateTest extends ContextTestSupport {

    @Test
    public void testAggregateCompletionAware() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:aggregated");
        result.expectedBodiesReceived("A+B+C", "X+Y+ZZZZ");
        result.message(0).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("predicate");
        result.message(1).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("predicate");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "X", "id", 123);
        template.sendBodyAndHeader("direct:start", "Y", "id", 123);
        template.sendBodyAndHeader("direct:start", "ZZZZ", "id", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new MyCompletionStrategy()).to("mock:aggregated");
            }
        };
    }

    private final class MyCompletionStrategy implements AggregationStrategy, Predicate {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class) + "+" + newExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }

        @Override
        public boolean matches(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            return body.length() >= 5;
        }
    }
}
