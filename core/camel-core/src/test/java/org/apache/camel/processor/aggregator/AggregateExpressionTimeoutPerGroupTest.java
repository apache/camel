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

public class AggregateExpressionTimeoutPerGroupTest extends ContextTestSupport {

    @Test
    public void testAggregateExpressionPerGroupTimeout() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("G+H+I", "D+E+F", "A+B+C");

        // will use fallback timeout (1 sec)
        template.sendBodyAndHeader("direct:start", "A", "id", 789);
        template.sendBodyAndHeader("direct:start", "B", "id", 789);
        template.sendBodyAndHeader("direct:start", "C", "id", 789);

        // will use 0.5 sec timeout
        Map<String, Object> headers = new HashMap<>();
        headers.put("id", 123);
        headers.put("timeout", 500);
        template.sendBodyAndHeaders("direct:start", "D", headers);
        template.sendBodyAndHeaders("direct:start", "E", headers);
        template.sendBodyAndHeaders("direct:start", "F", headers);

        // will use 0.1 sec timeout
        Map<String, Object> headers2 = new HashMap<>();
        headers2.put("id", 456);
        headers2.put("timeout", 100);
        template.sendBodyAndHeaders("direct:start", "G", headers2);
        template.sendBodyAndHeaders("direct:start", "H", headers2);
        template.sendBodyAndHeaders("direct:start", "I", headers2);

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
                    // and the timeout header contains the timeout in millis of
                    // inactivity them timeout and complete the aggregation
                    // and send it to mock:aggregated
                    .aggregate(header("id"), new BodyInAggregatingStrategy()).completionTimeout(header("timeout")).completionTimeout(1000).completionTimeoutCheckerInterval(10)
                    .to("mock:aggregated");
                // END SNIPPET: e1
            }
        };
    }
}
