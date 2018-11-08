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
import org.junit.Test;

/**
 * To be run manually when testing the timeout aggregator
 *
 * @version 
 */
public class AggregateTimeoutManualTest extends ContextTestSupport {

    @Test
    public void testDisabled() throws Exception {
        // noop
    }

    public void xxxtestAggregateTimeoutManual() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        // by default the use latest aggregation strategy
        result.expectedBodiesReceived("Message 1999");
        // should take at least 3 seconds to complete this one
        result.setResultMinimumWaitTime(2500);

        for (int i = 0; i < 2000; i++) {
            template.sendBodyAndHeader("direct:start", "Message " + i, "id", "1");
            Thread.sleep(3);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // timeout after 3 seconds
                    .aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(3000)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}