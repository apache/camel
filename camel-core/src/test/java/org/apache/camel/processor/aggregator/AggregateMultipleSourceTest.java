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

/**
 * @version $Revision$
 */
public class AggregateMultipleSourceTest extends ContextTestSupport {

    public void testAggregateMultipleSourceTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(20000);

        template.sendBodyAndHeader("seda:foo", "0", "type", "A");
        template.sendBodyAndHeader("seda:bar", "1", "type", "A");
        template.sendBodyAndHeader("seda:baz", "2", "type", "A");
        template.sendBodyAndHeader("seda:foo", "3", "type", "A");
        template.sendBodyAndHeader("seda:bar", "4", "type", "A");

        template.sendBodyAndHeader("seda:baz", "5", "type", "A");
        template.sendBodyAndHeader("seda:foo", "6", "type", "A");
        template.sendBodyAndHeader("seda:bar", "7", "type", "A");

        // we are 2 messages short to reach the batchSize of 5 the 2nd time
        // so then it should be the timeout that triggers this one

        assertMockEndpointsSatisfied();

        String body1 = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String body2 = mock.getReceivedExchanges().get(1).getIn().getBody(String.class);

        assertEquals(5, body1.length());
        assertEquals(3, body2.length());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("direct:aggregate");
                from("seda:bar").to("direct:aggregate");
                from("seda:baz").to("log:org.apache.camel.Baz").to("direct:aggregate");

                from("direct:aggregate")
                    .aggregator(constant(true), new MyAggregationStrategy())
                        .batchSize(5)
                        .batchTimeout(5000)
                        .to("log:org.apache.camel.Aggregated")
                        .to("mock:result")
                    .end();
            }
        };
    }

    private class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body = oldExchange.getIn().getBody(String.class);
            String newBody = newExchange.getIn().getBody(String.class);
            String merge = merge(body, newBody);
            oldExchange.getIn().setBody(merge);
            return oldExchange;
        }

        private String merge(String body, String newBody) {
            return body + newBody;
        }
    }
}
