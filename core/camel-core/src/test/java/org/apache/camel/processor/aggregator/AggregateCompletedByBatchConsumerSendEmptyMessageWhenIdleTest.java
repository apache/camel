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
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

/**
 *
 */
public class AggregateCompletedByBatchConsumerSendEmptyMessageWhenIdleTest extends ContextTestSupport {

    @Test
    public void testBatchConsumerSendEmptyMessageWhenIdle() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.allMessages().body().isNull();
        mock.allMessages().header(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("consumer");
        mock.allMessages().header(Exchange.AGGREGATED_SIZE).isEqualTo(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/batch?initialDelay=0&delay=10&sendEmptyMessageWhenIdle=true").aggregate(constant(true), new UseLatestAggregationStrategy())
                    .completionFromBatchConsumer().to("mock:result");

            }
        };
    }
}
