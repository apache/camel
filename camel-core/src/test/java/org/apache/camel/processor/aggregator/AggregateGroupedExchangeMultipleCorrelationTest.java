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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;

/**
 * Unit test for aggregate grouped exchanges.
 */
public class AggregateGroupedExchangeMultipleCorrelationTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    public void testGrouped() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 2 messages since we group using A or B keys
        result.expectedMessageCount(2);

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "100", "foo", "A");
        template.sendBodyAndHeader("direct:start", "130", "foo", "B");
        template.sendBodyAndHeader("direct:start", "150", "foo", "A");
        template.sendBodyAndHeader("direct:start", "200", "foo", "B");
        template.sendBodyAndHeader("direct:start", "180", "foo", "B");

        // to force B to timeout first as A is added last
        Thread.sleep(250);
        template.sendBodyAndHeader("direct:start", "120", "foo", "A");

        assertMockEndpointsSatisfied();

        Exchange out = result.getExchanges().get(0);
        List<Exchange> grouped = out.getIn().getBody(List.class);

        assertEquals(3, grouped.size());

        // B timeout first
        assertEquals("130", grouped.get(0).getIn().getBody(String.class));
        assertEquals("200", grouped.get(1).getIn().getBody(String.class));
        assertEquals("180", grouped.get(2).getIn().getBody(String.class));

        out = result.getExchanges().get(1);
        grouped = out.getIn().getBody(List.class);

        assertEquals(3, grouped.size());

        assertEquals("100", grouped.get(0).getIn().getBody(String.class));
        assertEquals("150", grouped.get(1).getIn().getBody(String.class));
        assertEquals("120", grouped.get(2).getIn().getBody(String.class));
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregate all using the foo header and group the exchanges so we get one single exchange containing all the others
                    .aggregate(header("foo"), new GroupedExchangeAggregationStrategy())
                    // wait for 1 seconds to aggregate
                    .completionTimeout(1000L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}