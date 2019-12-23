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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregateExpressionSizeOverrideFixedTimeoutTest extends ContextTestSupport {

    @Test
    public void testAggregateExpressionSize() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C");
        getMockEndpoint("mock:aggregated").expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", 123);
        headers.put("mySize", 4);

        template.sendBodyAndHeaders("direct:start", "A", headers);
        template.sendBodyAndHeaders("direct:start", "B", headers);
        template.sendBodyAndHeaders("direct:start", "C", headers);

        // do not send the 4th as we want to test for timeout then

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).completionSize(2).completionSize(header("mySize")).completionTimeout(1000)
                    .to("mock:aggregated");
            }
        };
    }
}
