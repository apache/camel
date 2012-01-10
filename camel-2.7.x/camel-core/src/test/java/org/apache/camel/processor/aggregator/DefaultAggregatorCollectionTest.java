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
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * Unit test for DefaultAggregatorCollection.
 */
public class DefaultAggregatorCollectionTest extends ContextTestSupport {

    public void testDefaultAggregateCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 4 messages grouped by the latest message only
        result.expectedMessageCount(4);
        result.expectedBodiesReceivedInAnyOrder("Message 1d", "Message 2b", "Message 3c", "Message 4");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "Message 1a", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 2a", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 3a", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1b", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3b", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 1c", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 3c", "id", "3");
        template.sendBodyAndHeader("direct:start", "Message 2b", "id", "2");
        template.sendBodyAndHeader("direct:start", "Message 1d", "id", "1");
        template.sendBodyAndHeader("direct:start", "Message 4", "id", "4");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // our route is aggregating from the direct queue and sending the response to the mock
                from("direct:start")
                    // aggregated by header id
                    // as we have not configured more on the aggregator it will default to aggregate the
                    // latest exchange only
                    .aggregate(header("id")).aggregationStrategy(new UseLatestAggregationStrategy())
                    // wait for 0.5 seconds to aggregate
                    .completionTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}