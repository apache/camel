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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AggregateCompletionByBatchConsumerTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testCorrelationKey() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");

        // we expect 4 messages since we group 4 batches
        result.expectedMessageCount(4);

        //BATCH_SIZE and not BATCH_COMPLETE is used by aggregate to test for batch completion
        final Integer batch_size = Integer.valueOf(8);

        // then we sent the batch of message
        template.sendBodyAndProperty("direct:start", "batch-4", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-4", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-3", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-3", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-2", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-2", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-1", Exchange.BATCH_SIZE, batch_size);
        template.sendBodyAndProperty("direct:start", "batch-1", Exchange.BATCH_SIZE, batch_size);

        assertMockEndpointsSatisfied();

        Exchange out;
        List<Message> grouped;

        out = result.getExchanges().get(1);
        grouped = out.getIn().getBody(List.class);

        assertEquals(2, grouped.size());

        assertEquals("batch-2", grouped.get(0).getBody(String.class));
        assertEquals("batch-2", grouped.get(1).getBody(String.class));
        assertEquals("batch-2", out.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));

        out = result.getExchanges().get(2);
        grouped = out.getIn().getBody(List.class);

        assertEquals(2, grouped.size());

        assertEquals("batch-3", grouped.get(0).getBody(String.class));
        assertEquals("batch-3", grouped.get(1).getBody(String.class));
        assertEquals("batch-3", out.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));

        out = result.getExchanges().get(3);
        grouped = out.getIn().getBody(List.class);

        assertEquals(2, grouped.size());

        assertEquals("batch-4", grouped.get(0).getBody(String.class));
        assertEquals("batch-4", grouped.get(1).getBody(String.class));
        assertEquals("batch-4", out.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));

        out = result.getExchanges().get(0);
        grouped = out.getIn().getBody(List.class);

        assertEquals(2, grouped.size());

        assertEquals("batch-1", grouped.get(0).getBody(String.class));
        assertEquals("batch-1", grouped.get(1).getBody(String.class));
        assertEquals("batch-1", out.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // our route is aggregating from the direct queue and sending
                // the response to the mock
                from("direct:start")
                        // aggregate all using body and group the
                        // exchanges so we get one single exchange containing all
                        .aggregate(body(), new GroupedMessageAggregationStrategy())
                        // we are simulating a batch consumer
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                        .to("mock:result");
            }
        };
    }
}
