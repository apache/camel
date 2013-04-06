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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Unit test to verify that Aggregate aggregator does not included filtered exchanges.
 *
 * @version 
 */
public class AggregateShouldSkipFilteredExchangesTest extends ContextTestSupport {

    public void testAggregateWithFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World,Bye World");

        MockEndpoint filtered = getMockEndpoint("mock:filtered");
        filtered.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBodyAndHeader("direct:start", "Hello World", "id", 1);
        template.sendBodyAndHeader("direct:start", "Hi there", "id", 1);
        template.sendBodyAndHeader("direct:start", "Bye World", "id", 1);
        template.sendBodyAndHeader("direct:start", "How do you do?", "id", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Predicate goodWord = body().contains("World");

                from("direct:start")
                    .filter(goodWord)
                        .to("mock:filtered")
                        .aggregate(header("id"), new MyAggregationStrategy()).completionTimeout(1000)
                            .to("mock:result")
                        .end()
                    .end();

            }
        };
    }

    private static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String newBody = newExchange.getIn().getBody(String.class);
            String body = oldExchange.getIn().getBody(String.class);
            body = body + "," + newBody;
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }

    }
}