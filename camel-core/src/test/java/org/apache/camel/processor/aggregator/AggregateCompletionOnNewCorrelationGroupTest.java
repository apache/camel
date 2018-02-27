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
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.junit.Test;

public class AggregateCompletionOnNewCorrelationGroupTest extends ContextTestSupport {

    @Test
    public void testCompletionOnNewCorrelationGroup() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("AA", "BB", "CCC");

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "2");
        template.sendBodyAndHeader("direct:start", "B", "id", "2");
        template.sendBodyAndHeader("direct:start", "C", "id", "3");
        template.sendBodyAndHeader("direct:start", "C", "id", "3");
        template.sendBodyAndHeader("direct:start", "C", "id", "3");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .completionOnNewCorrelationGroup()
                        .completionSize(3)
                    .to("log:aggregated", "mock:aggregated");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}