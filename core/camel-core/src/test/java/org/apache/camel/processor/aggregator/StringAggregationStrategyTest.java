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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class StringAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testAggregateString() throws Exception {
        // for testing to string type conversion inside aggregator
        Object objectBody = new Object();
        Object objectHeader = new Object();

        getMockEndpoint("mock:aggregatedBody").expectedBodiesReceived("bodyAbodyB" + objectBody);
        getMockEndpoint("mock:aggregatedBodyComma").expectedBodiesReceived("bodyA, bodyB, " + objectBody);
        getMockEndpoint("mock:aggregatedBodyLines").expectedBodiesReceived("bodyA\nbodyB\n" + objectBody);

        getMockEndpoint("mock:aggregatedHeader").expectedBodiesReceived("headerAheaderB" + objectHeader);
        getMockEndpoint("mock:aggregatedHeaderComma").expectedBodiesReceived("headerA, headerB, " + objectHeader);
        getMockEndpoint("mock:aggregatedHeaderLines").expectedBodiesReceived("headerA\nheaderB\n" + objectHeader);

        template.sendBodyAndHeader("direct:start", "bodyA", "header", "headerA");
        template.sendBodyAndHeader("direct:start", "bodyB", "header", "headerB");
        template.sendBodyAndHeader("direct:start", objectBody, "header", objectHeader);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("direct:aggregateBody", "direct:aggregateBodyComma", "direct:aggregateBodyLines",
                        "direct:aggregateHeader", "direct:aggregateHeaderComma",
                        "direct:aggregateHeaderLines");

                from("direct:aggregateBody").aggregate(constant(true), AggregationStrategies.string()).completionSize(3)
                        .to("mock:aggregatedBody");

                from("direct:aggregateBodyComma").aggregate(constant(true), AggregationStrategies.string().delimiter(", "))
                        .completionSize(3).to("mock:aggregatedBodyComma");

                from("direct:aggregateBodyLines").aggregate(constant(true), AggregationStrategies.string("\n"))
                        .completionSize(3).to("mock:aggregatedBodyLines");

                from("direct:aggregateHeader").aggregate(constant(true), AggregationStrategies.string().pick(header("header")))
                        .completionSize(3).to("mock:aggregatedHeader");

                from("direct:aggregateHeaderComma")
                        .aggregate(constant(true), AggregationStrategies.string(", ").pick(header("header"))).completionSize(3)
                        .to("mock:aggregatedHeaderComma");

                from("direct:aggregateHeaderLines")
                        .aggregate(constant(true), AggregationStrategies.string().pick(header("header")).delimiter("\n"))
                        .completionSize(3)
                        .to("mock:aggregatedHeaderLines");
            }
        };
    }
}
