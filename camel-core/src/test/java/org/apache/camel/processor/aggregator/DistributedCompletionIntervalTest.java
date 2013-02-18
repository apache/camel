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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * Unit test to verify that aggregate by interval only also works.
 *
 * @version 
 */
public class DistributedCompletionIntervalTest extends AbstractDistributedTest {

    private MemoryAggregationRepository sharedAggregationRepository = new MemoryAggregationRepository(true);

    public void testCamelContext1Wins() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Message 19");
        MockEndpoint mock2 = getMockEndpoint2("mock:result");
        mock2.expectedMessageCount(0);

        // ensure messages are send after the 1s
        Thread.sleep(2000);
        sendMessages();

        mock.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    public void testCamelContext2Wins() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        MockEndpoint mock2 = getMockEndpoint2("mock:result");
        mock2.expectedBodiesReceived("Message 19");

        // ensure messages are send after the 1s
        Thread.sleep(2000);
        sendMessages();

        mock2.assertIsSatisfied();
        mock.assertIsSatisfied();
    }

    private void sendMessages() {
        for (int i = 0; i < 20; i++) {
            int choice = i % 2;
            if (choice == 0) {
                template.sendBodyAndHeader("direct:start", "Message " + i, "id", "1");
            } else {
                template2.sendBodyAndHeader("direct:start", "Message " + i, "id", "1");
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .aggregate(header("id"), new UseLatestAggregationStrategy())
                        .aggregationRepository(sharedAggregationRepository)
                        .optimisticLocking()
                        // trigger completion every 5th second
                        .completionInterval(getName().equals("testCamelContext1Wins") ? 5000 : 10000)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder2() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .aggregate(header("id"), new UseLatestAggregationStrategy())
                        .aggregationRepository(sharedAggregationRepository)
                        .optimisticLocking()
                        // trigger completion every 5th second
                        .completionInterval(getName().equals("testCamelContext1Wins") ? 10000 : 5000)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
