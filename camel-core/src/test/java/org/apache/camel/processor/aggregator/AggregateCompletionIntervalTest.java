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
 * Unit test to verify that aggregate by interval only also works.
 *
 * @version 
 */
public class AggregateCompletionIntervalTest extends ContextTestSupport {

    public void testAggregateInterval() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        // by default the use latest aggregation strategy is used so we get message 9
        result.expectedBodiesReceived("Message 9");

        // ensure messages are send after a little bit
        Thread.sleep(100);
        
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("seda:start", "Message " + i, "id", "1");
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("seda:start")
                    .aggregate(header("id"), new UseLatestAggregationStrategy())
                        // trigger completion every 2nd second
                        .completionInterval(2000)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}