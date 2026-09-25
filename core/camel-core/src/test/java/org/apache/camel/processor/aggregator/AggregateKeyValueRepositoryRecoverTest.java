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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.support.KeyValueAggregationRepository;
import org.junit.jupiter.api.Test;

/**
 * An aggregated exchange recovered from a {@link KeyValueAggregationRepository} must be the exchange that was
 * completed, including the exchange that completed the group.
 */
public class AggregateKeyValueRepositoryRecoverTest extends ContextTestSupport {

    @Test
    public void testRecoverGroupCompletedBySize() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C", "A+B+C");
        mock.message(0).header(Exchange.REDELIVERED).isNull();
        mock.message(1).header(Exchange.REDELIVERED).isEqualTo(true);
        // the first attempt fails, so the aggregated exchange is recovered
        mock.whenExchangeReceived(1, exchange -> {
            throw new IllegalArgumentException("Forced");
        });

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);
        template.sendBodyAndHeader("direct:start", "C", "id", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                KeyValueAggregationRepository repository = new KeyValueAggregationRepository();
                repository.setRecoveryInterval(100);

                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(repository)
                        .completionSize(3)
                        .to("mock:result");
            }
        };
    }
}
