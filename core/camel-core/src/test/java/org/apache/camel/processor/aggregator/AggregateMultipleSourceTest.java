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

public class AggregateMultipleSourceTest extends ContextTestSupport {

    @Test
    public void testAggregateMultipleSourceTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectsNoDuplicates(body());

        for (int i = 0; i < 40; i++) {
            if (i % 2 == 0) {
                template.sendBodyAndHeader("seda:foo", "" + i, "type", "A");
            } else if (i % 5 == 0) {
                template.sendBodyAndHeader("seda:bar", "" + i, "type", "A");
            } else {
                template.sendBodyAndHeader("seda:baz", "" + i, "type", "A");
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("direct:aggregate");
                from("seda:bar").to("direct:aggregate");
                from("seda:baz").to("direct:aggregate");

                from("direct:aggregate").aggregate(header("type"), new MyAggregationStrategy()).completionSize(25).completionTimeout(500).completionTimeoutCheckerInterval(10)
                    .to("mock:result").end();
            }
        };
    }

    private static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body = oldExchange.getIn().getBody(String.class);
            String newBody = newExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body + newBody);
            return oldExchange;
        }
    }
}
