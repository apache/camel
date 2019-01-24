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
import org.junit.Test;

/**
 * Unit test for aggregate grouped exchanges.
 */
public class AggregateGroupedExchangeTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testGrouped() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 1 messages since we group all we get in using the same correlation key
        result.expectedMessageCount(1);

        // then we sent all the message at once
        template.sendBody("direct:start", "100");
        template.sendBody("direct:start", "150");
        template.sendBody("direct:start", "130");
        template.sendBody("direct:start", "200");
        template.sendBody("direct:start", "190");

        assertMockEndpointsSatisfied();

        Exchange out = result.getExchanges().get(0);
        List<Exchange> grouped = out.getIn().getBody(List.class);

        assertEquals(5, grouped.size());

        assertEquals("100", grouped.get(0).getIn().getBody(String.class));
        assertEquals("150", grouped.get(1).getIn().getBody(String.class));
        assertEquals("130", grouped.get(2).getIn().getBody(String.class));
        assertEquals("200", grouped.get(3).getIn().getBody(String.class));
        assertEquals("190", grouped.get(4).getIn().getBody(String.class));
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregate all using same expression and group the exchanges so we get one single exchange containing all the others
                    .aggregate(new GroupedExchangeAggregationStrategy()).constant(true)
                    // wait for 0.5 seconds to aggregate
                    .completionTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
