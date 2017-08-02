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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;

/**
 * @version 
 */
public class AggregateDiscardOnTimeoutTest extends ContextTestSupport {

    public void testAggregateDiscardOnTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        // wait 0.25 seconds
        Thread.sleep(250);

        mock.assertIsSatisfied();

        // now send 3 which does not timeout
        mock.reset();
        mock.expectedBodiesReceived("C+D+E");

        template.sendBodyAndHeader("direct:start", "C", "id", 456);
        template.sendBodyAndHeader("direct:start", "D", "id", 456);
        template.sendBodyAndHeader("direct:start", "E", "id", 456);

        // should complete before timeout
        assertTrue(mock.await(1000, TimeUnit.MILLISECONDS));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(3)
                        // use a 0.2 second timeout
                        .completionTimeout(200)
                        // speedup checker
                        .completionTimeoutCheckerInterval(10)
                        // and if timeout occurred then just discard the aggregated message
                        .discardOnCompletionTimeout()
                        .to("mock:aggregated");
                // END SNIPPET: e1
            }
        };
    }
}
