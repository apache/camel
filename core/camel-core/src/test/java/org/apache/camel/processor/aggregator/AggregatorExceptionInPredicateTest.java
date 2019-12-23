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
import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

/**
 * Based on CAMEL-1546
 */
public class AggregatorExceptionInPredicateTest extends ContextTestSupport {

    @Test
    public void testExceptionInAggregationStrategy() throws Exception {
        testExceptionInFlow("direct:start");
    }

    @Test
    public void testExceptionInPredicate() throws Exception {
        testExceptionInFlow("direct:predicate");
    }

    private void testExceptionInFlow(String startUri) throws Exception {
        // failed first aggregation

        getMockEndpoint("mock:handled").expectedMessageCount(1);

        // second aggregated
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(startUri, "Damn", "id", 1);
        template.sendBodyAndHeader(startUri, "Hello World", "id", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("mock:handled");

                from("direct:start").aggregate(header("id")).completionTimeout(500).aggregationStrategy(new AggregationStrategy() {

                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        Object body = newExchange.getIn().getBody();
                        if ("Damn".equals(body)) {
                            throw new IllegalArgumentException();
                        }
                        return newExchange;
                    }
                }).to("mock:result");

                from("direct:predicate").aggregate(new Expression() {

                    public <T> T evaluate(Exchange exchange, Class<T> type) {
                        if (exchange.getIn().getBody().equals("Damn")) {
                            throw new IllegalArgumentException();
                        }
                        return ExpressionBuilder.headerExpression("id").evaluate(exchange, type);
                    }
                }, new UseLatestAggregationStrategy()).completionTimeout(500).to("mock:result");
            }
        };
    }
}
