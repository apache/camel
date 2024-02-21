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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Based on user forum issue
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class AggregateLostGroupIssueTest extends ContextTestSupport {

    private int messageIndex;
    private MemoryAggregationRepository aggregationRepository;

    @BeforeEach
    public void setUp() throws Exception {
        messageIndex = 0;
        super.setUp();
        getAggregationRepository().start();
        context.getRouteController().startRoute("foo");
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.getRouteController().stopRoute("foo");
        getAggregationRepository().stop();
        super.tearDown();
    }

    @Test
    public void testAggregateLostGroupIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).body().isEqualTo("0,1,2,3,4,5,6,7,8,9");
        mock.message(1).body().isEqualTo("10,11,12,13,14,15,16,17,18,19");

        assertMockEndpointsSatisfied();
    }

    protected synchronized MemoryAggregationRepository getAggregationRepository() {
        if (aggregationRepository == null) {
            aggregationRepository = new MemoryAggregationRepository();
        }
        return aggregationRepository;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer://foo?period=10&delay=0").id("foo").startupOrder(2).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getMessage().setBody(messageIndex++);
                        exchange.getMessage().setHeader("aggregateGroup", "group1");
                    }
                }).to("direct:aggregator");

                from("direct:aggregator").startupOrder(1).aggregate(header("aggregateGroup"), new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            return newExchange;
                        }

                        String oldBody = oldExchange.getIn().getBody(String.class);
                        String newBody = newExchange.getIn().getBody(String.class);

                        oldExchange.getIn().setBody(oldBody + "," + newBody);
                        return oldExchange;
                    }
                }).aggregationRepository(getAggregationRepository())
                        .completionSize(10).completionTimeout(200).completionTimeoutCheckerInterval(10).to("log:aggregated")
                        .to("mock:result");
            }
        };
    }

}
