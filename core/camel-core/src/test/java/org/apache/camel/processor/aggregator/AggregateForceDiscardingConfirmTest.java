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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.StringAggregationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Force discarding a group discards it (confirms it in the repository), also when discardOnAggregationFailure is not
 * enabled.
 */
public class AggregateForceDiscardingConfirmTest extends ContextTestSupport {

    private final AggregateController controller = new DefaultAggregateController();
    private final List<String> confirmed = new ArrayList<>();

    @Test
    public void testForceDiscardingOfGroupConfirms() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", "A");
        template.sendBodyAndHeader("direct:start", "B", "id", "B");

        assertEquals(1, controller.forceDiscardingOfGroup("A"));
        assertEquals(1, controller.forceDiscardingOfAllGroups());

        assertMockEndpointsSatisfied();
        assertEquals(2, confirmed.size(), "the discarded groups should be confirmed");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy()).completionSize(10)
                        .aggregationRepository(new MemoryAggregationRepository() {
                            @Override
                            public void confirm(CamelContext camelContext, String exchangeId) {
                                confirmed.add(exchangeId);
                            }
                        })
                        .aggregateController(controller)
                        .to("mock:aggregated");
            }
        };
    }
}
