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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;

public class AggregateCompletionSizeAndBatchConsumerTest extends ContextTestSupport {

    public void testAggregateExpressionSize() throws Exception {
        MockEndpoint result =  getMockEndpoint("mock:result");
        // A+A+A gets completed by size, the others by consumer
        result.expectedBodiesReceived("A+A+A", "A", "B+B", "Z");
        result.message(0).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("size");
        result.message(1).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("consumer");
        result.message(2).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("consumer");
        result.message(3).exchangeProperty(Exchange.AGGREGATED_COMPLETED_BY).isEqualTo("consumer");

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "A");
        // send the last one with the batch size property
        template.sendBodyAndProperty("direct:start", "Z", Exchange.BATCH_SIZE, 7);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(body(), new BodyInAggregatingStrategy()).completionSize(3).completionFromBatchConsumer()
                    .to("log:result", "mock:result");
            }
        };
    }
}
