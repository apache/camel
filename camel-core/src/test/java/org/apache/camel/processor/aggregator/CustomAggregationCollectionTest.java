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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Unit test for using our own aggregation collection.
 */
public class CustomAggregationCollectionTest extends ContextTestSupport {

    public void testCustomAggregationCollection() throws Exception {
        // START SNIPPET: e2
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 5 messages since our custom aggregation collection just gets it all
        // but returns them in reverse order
        result.expectedMessageCount(5);
        result.expectedBodiesReceived("190", "200", "130", "150", "100");

        // then we sent all the message at once
        template.sendBodyAndHeader("direct:start", "100", "id", "1");
        template.sendBodyAndHeader("direct:start", "150", "id", "2");
        template.sendBodyAndHeader("direct:start", "130", "id", "2");
        template.sendBodyAndHeader("direct:start", "200", "id", "1");
        template.sendBodyAndHeader("direct:start", "190", "id", "1");

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
                    // use our own collection for aggregation
                    .aggregator(new MyReverseAggregationCollection())
                    // wait for 0.5 seconds to aggregate
                    .batchTimeout(500L)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}