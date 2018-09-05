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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcAggregateDiscardOnTimeoutTest extends AbstractJdbcAggregationTestSupport {

    @Test
    public void testAggregateDiscardOnTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        // wait 2 seconds
        Thread.sleep(2000);

        mock.assertIsSatisfied();

        // now send 3 which does not timeout
        mock.reset();
        mock.expectedBodiesReceived("C+D+E");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        // should complete before timeout
        mock.await(1500, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .completionSize(3).aggregationRepository(repo)
                        // use a 1 second timeout
                        .completionTimeout(1000)
                        // and if timeout occurred then just discard the aggregated message
                        .discardOnCompletionTimeout()
                        .to("mock:aggregated");
            }
        };
    }
}