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
import org.apache.camel.processor.aggregate.CompletionAwareAggregationStrategy;

/**
 *
 */
public class AggregateCompletionAwareAggregationStrategyTest extends ContextTestSupport {

    public void testAggregateCompletionAware() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C");
        getMockEndpoint("mock:aggregated").expectedHeaderReceived("bodyCopy", "A+B+C");
        getMockEndpoint("mock:aggregated").expectedHeaderReceived("myStrategyCompleted", true);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyCompletionStrategy()).completionSize(3)
                    .to("mock:aggregated");
            }
        };
    }

    private final class MyCompletionStrategy implements CompletionAwareAggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String oldBody = oldExchange.getIn().getBody(String.class);
            String newBody = newExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(oldBody + "+" + newBody);
            return oldExchange;
        }

        @Override
        public void onCompletion(Exchange exchange) {
            // copy body so we can test what the body was when this callback was invoked
            exchange.getIn().setHeader("bodyCopy", exchange.getIn().getBody());
            // add a header so we know we were called
            exchange.getIn().setHeader("myStrategyCompleted", true);
        }
    }
}
