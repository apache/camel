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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for aggregate grouped exchanges.
 */
public class AggregateGroupedExchangeCompletionExpressionSizeTest extends ContextTestSupport {

    public void testGrouped() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "A", "size", 3);
        template.sendBodyAndHeader("direct:start", "B", "size", 3);
        template.sendBodyAndHeader("direct:start", "C", "size", 3);
        template.sendBodyAndHeader("direct:start", "D", "size", 3);
        template.sendBodyAndHeader("direct:start", "E", "size", 3);
        template.sendBodyAndHeader("direct:start", "F", "size", 3);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(constant(true)).completionSize(header("size"))
                    .groupExchanges()
                    .to("mock:result");
            }
        };
    }
}