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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcAggregateTimeoutCompletionRestartTest extends AbstractJdbcAggregationTestSupport {

    @Test
    public void testJdbcAggregateTimeoutCompletionRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        // stop Camel
        context.stop();
        assertEquals(0, mock.getReceivedCounter());

        // start Camel again, and the timeout should trigger a completion
        context.start();

        mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("ABC");

        assertMockEndpointsSatisfied();
        assertEquals(1, mock.getReceivedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                                // use our created jdbc repo as aggregation repository
                        .completionTimeout(3000).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
        };
    }
}