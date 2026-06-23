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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

public class AggregateCompleteAllOnStopTest extends ContextTestSupport {

    protected final MemoryAggregationRepository repo = new MemoryAggregationRepository();

    @Test
    public void testCompleteAllOnStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("A+B", "C");

        MockEndpoint input = getMockEndpoint("mock:input");
        input.expectedMessageCount(3);

        // we only send 3, but we get 2 exchanges completed when stopping
        // as we tell it to complete all on stop
        template.sendBodyAndHeader("seda:start", "A", "id", "foo");
        template.sendBodyAndHeader("seda:start", "B", "id", "foo");
        template.sendBodyAndHeader("seda:start", "C", "id", "foo");

        input.assertIsSatisfied();

        // mock:input fires before the aggregator step, so assertIsSatisfied() can
        // return while C is still in-flight between mock:input and the aggregator.
        // Wait until C is actually stored in the repository before stopping.
        awaitLastMessageInAggregator();

        context.getRouteController().stopRoute("foo");

        assertMockEndpointsSatisfied();
    }

    protected void awaitLastMessageInAggregator() throws Exception {
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> repo.get(context, "foo") != null);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("foo")
                        .to("mock:input")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .aggregationRepository(repo)
                        .completionSize(2).completionTimeout(5000).completeAllOnStop().completionTimeoutCheckerInterval(10)
                        .to("mock:aggregated");
            }
        };
    }

}
