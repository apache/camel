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
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;

public class AggregateGroupMessageTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    public void testGrouped() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");

        result.expectedMessageCount(1);

        template.sendBody("direct:start", "100");
        template.sendBody("direct:start", "150");
        template.sendBody("direct:start", "130");
        template.sendBody("direct:start", "200");
        template.sendBody("direct:start", "190");

        assertMockEndpointsSatisfied();

        Exchange out = result.getExchanges().get(0);
        List<Message> grouped = out.getIn().getBody(List.class);

        assertEquals(5, grouped.size());

        assertEquals("100", grouped.get(0).getBody(String.class));
        assertEquals("150", grouped.get(1).getBody(String.class));
        assertEquals("130", grouped.get(2).getBody(String.class));
        assertEquals("200", grouped.get(3).getBody(String.class));
        assertEquals("190", grouped.get(4).getBody(String.class));
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(constant(true), new GroupedMessageAggregationStrategy())
                    .completionTimeout(500L)
                    .to("mock:result");
            }
        };
    }
}
