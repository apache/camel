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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregateExpressionSizeTest extends ContextTestSupport {

    @Test
    public void testAggregateExpressionSize() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C");

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", 123);
        headers.put("mySize", 3);

        template.sendBodyAndHeaders("direct:start", "A", headers);
        template.sendBodyAndHeaders("direct:start", "B", headers);
        template.sendBodyAndHeaders("direct:start", "C", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // aggregate all exchanges correlated by the id header.
                    // Aggregate them using the BodyInAggregatingStrategy
                    // strategy which
                    // and the header mySize determines the number of aggregated
                    // messages should trigger the completion
                    // and send it to mock:aggregated
                    .aggregate(header("id"), new BodyInAggregatingStrategy()).completionSize(header("mySize")).to("mock:aggregated");
                // END SNIPPET: e1
            }
        };
    }
}
