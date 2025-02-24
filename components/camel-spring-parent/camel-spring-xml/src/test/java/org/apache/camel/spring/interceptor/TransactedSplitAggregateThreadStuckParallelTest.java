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
package org.apache.camel.spring.interceptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.StringAggregationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TransactedSplitAggregateThreadStuckParallelTest extends TransactionClientDataSourceSupport {

    @Test
    @Timeout(value = 10)
    public void testThreadStuck() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("Aggregated A");
        getMockEndpoint("mock:end").expectedBodiesReceived("Done A");
        // ensure they execute in this order, where Aggregate EIP completes before done (due to 1 completion size)
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Aggregated A", "Done A");

        template.sendBody("seda:start", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .transacted()
                        .to("direct:split")
                        .log("End ${threadName}")
                        .setBody(simple("Done ${body}"))
                        .to("mock:result", "mock:end");

                from("direct:split")
                        .split(body())
                        .to("direct:aggregate");

                from("direct:aggregate")
                    .aggregate(constant("true"))
                    .completionSize(1).aggregationStrategy(new StringAggregationStrategy()).parallelProcessing()
                        .log("Aggregated ${threadName}")
                        .setBody(simple("Aggregated ${body}"))
                        .to("mock:result", "mock:aggregated")
                    .end();
            }
        };
    }

}
