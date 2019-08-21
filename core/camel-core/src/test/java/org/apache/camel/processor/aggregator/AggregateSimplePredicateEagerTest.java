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
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregateSimplePredicateEagerTest extends ContextTestSupport {

    @Test
    public void testAggregateSimplePredicateEager() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C+END");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "END", "id", 123);

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
                    // strategy
                    // do eager checking which means the completion predicate
                    // will use the incoming exchange
                    // which allows us to trigger completion when a certain
                    // exchange arrived which is the
                    // END message
                    .aggregate(header("id"), new BodyInAggregatingStrategy()).eagerCheckCompletion().completionPredicate(body().isEqualTo("END")).to("mock:aggregated");
                // END SNIPPET: e1
            }
        };
    }
}
