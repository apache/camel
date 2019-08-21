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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

/**
 * Unit test for PredicateAggregatorCollection.
 */
public class PredicateAggregatorCollectionTest extends ContextTestSupport {

    @Test
    public void testPredicateAggregateCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we only expect two messages as they have reached the completed
        // predicate
        // that we want 3 messages that has the same header id
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Message 1c", "Message 3c");

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
                // our route is aggregating from the direct queue and sending
                // the response to the mock
                from("direct:start")
                    // we use the collection based aggregator we already have
                    // configured
                    .aggregate(header("id"), new UseLatestAggregationStrategy()).completionSize(3).to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
