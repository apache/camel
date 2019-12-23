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

public class AggregateParallelProcessingTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAggregateParallelProcessing() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).eagerCheckCompletion().completionPredicate(body().isEqualTo("END"))
                    .parallelProcessing().to("log:result", "mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("A+Donkey+END", "B+Camel+END");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "Donkey", "id", 1);
        template.sendBodyAndHeader("direct:start", "END", "id", 1);

        template.sendBodyAndHeader("direct:start", "B", "id", 2);
        template.sendBodyAndHeader("direct:start", "Camel", "id", 2);
        template.sendBodyAndHeader("direct:start", "END", "id", 2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAggregateNotParallelProcessing() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).eagerCheckCompletion().completionPredicate(body().isEqualTo("END")).to("log:result",
                                                                                                                                                                     "mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("A+Donkey+END", "B+Camel+END");

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "Donkey", "id", 1);
        template.sendBodyAndHeader("direct:start", "END", "id", 1);

        template.sendBodyAndHeader("direct:start", "B", "id", 2);
        template.sendBodyAndHeader("direct:start", "Camel", "id", 2);
        template.sendBodyAndHeader("direct:start", "END", "id", 2);

        assertMockEndpointsSatisfied();
    }

}
