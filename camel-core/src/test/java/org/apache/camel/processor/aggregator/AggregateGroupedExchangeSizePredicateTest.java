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
 * Unit test for aggregate grouped exchanges completed by size
 */
public class AggregateGroupedExchangeSizePredicateTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    public void testGroupedSize() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 2 messages since we group by size (3 and 2)
        result.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "100", "groupSize", 3);
        template.sendBodyAndHeader("direct:start", "150", "groupSize", 3);
        template.sendBodyAndHeader("direct:start", "130", "groupSize", 3);
        template.sendBodyAndHeader("direct:start", "200", "groupSize", 2);
        template.sendBodyAndHeader("direct:start", "190", "groupSize", 2);

        assertMockEndpointsSatisfied();

        Exchange out = result.getExchanges().get(0);
        List<Exchange> grouped = out.getIn().getBody(List.class);
        assertEquals(3, grouped.size());
        assertEquals("100", grouped.get(0).getIn().getBody(String.class));
        assertEquals("150", grouped.get(1).getIn().getBody(String.class));
        assertEquals("130", grouped.get(2).getIn().getBody(String.class));

        out = result.getExchanges().get(1);
        grouped = out.getIn().getBody(List.class);
        assertEquals(2, grouped.size());

        assertEquals("200", grouped.get(0).getIn().getBody(String.class));
        assertEquals("190", grouped.get(1).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    // must use eagerCheckCompletion so we can check the groupSize header on the incoming exchange 
                    .aggregate(new GroupedExchangeAggregationStrategy()).constant(true).eagerCheckCompletion().completionSize(header("groupSize"))
                        .to("mock:result")
                    .end();
            }
        };
    }
}