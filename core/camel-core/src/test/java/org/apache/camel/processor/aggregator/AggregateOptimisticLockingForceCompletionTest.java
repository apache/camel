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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.StringAggregationStrategy;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With optimistic locking, a group that another node completed first does not stop the force completion of the other
 * groups.
 */
public class AggregateOptimisticLockingForceCompletionTest extends ContextTestSupport {

    private final AggregateController controller = new DefaultAggregateController();

    @Test
    public void testForceCompletionContinuesAfterConflict() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("A", "C");

        template.sendBodyAndHeader("direct:start", "A", "id", "A");
        template.sendBodyAndHeader("direct:start", "B", "id", "B");
        template.sendBodyAndHeader("direct:start", "C", "id", "C");

        controller.forceCompletionOfAllGroups();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testForceDiscardingContinuesAfterConflict() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", "A");
        template.sendBodyAndHeader("direct:start", "B", "id", "B");
        template.sendBodyAndHeader("direct:start", "C", "id", "C");

        // only group B is left (completed by another node, which this test repository never removes)
        controller.forceDiscardingOfAllGroups();
        assertEquals(1, controller.forceDiscardingOfAllGroups());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy()).completionSize(10)
                        .aggregationRepository(new StolenRepository()).optimisticLocking()
                        .aggregateController(controller)
                        .to("mock:aggregated");
            }
        };
    }

    // another node has completed group B first
    private static class StolenRepository extends MemoryAggregationRepository {
        StolenRepository() {
            super(true);
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            if ("B".equals(key)) {
                throw new OptimisticLockingAggregationRepository.OptimisticLockingException();
            }
            super.remove(camelContext, key, exchange);
        }
    }
}
