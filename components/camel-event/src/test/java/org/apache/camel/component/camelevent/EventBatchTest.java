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
package org.apache.camel.component.camelevent;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventBatchTest extends CamelTestSupport {

    @Test
    void testBatchFull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:batchResult");
        // With batchSize=3, sending 3 messages should produce at least 1 batch
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:batchSource", "Hello 1");
        template.sendBody("direct:batchSource", "Hello 2");
        template.sendBody("direct:batchSource", "Hello 3");

        mock.assertIsSatisfied();

        // Verify the body is a List of CamelEvents
        Object body = mock.getExchanges().get(0).getIn().getBody();
        assertInstanceOf(List.class, body);
        @SuppressWarnings("unchecked")
        List<CamelEvent> events = (List<CamelEvent>) body;
        assertTrue(events.size() >= 1, "Batch should contain events");

        // Verify batch size header
        Integer batchSize = mock.getExchanges().get(0).getIn()
                .getHeader(CamelEventConstants.HEADER_EVENT_BATCH_SIZE, Integer.class);
        assertNotNull(batchSize);
        assertTrue(batchSize >= 1);
    }

    @Test
    void testBatchTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:timeoutBatchResult");
        // Send fewer events than batchSize, the batch timeout should flush them
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:timeoutBatchSource", "Hello 1");

        // Wait for the batch timeout to trigger (batchTimeout=500ms)
        mock.assertIsSatisfied(3000);

        // Verify partial batch was flushed
        Object body = mock.getExchanges().get(0).getIn().getBody();
        assertInstanceOf(List.class, body);
        @SuppressWarnings("unchecked")
        List<CamelEvent> events = (List<CamelEvent>) body;
        assertTrue(events.size() >= 1, "Partial batch should have been flushed by timeout");
    }

    @Test
    void testBatchWithAsync() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:asyncBatchResult");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:asyncBatchSource", "Hello 1");
        template.sendBody("direct:asyncBatchSource", "Hello 2");

        // Wait for async processing + batch timeout
        mock.assertIsSatisfied(5000);

        // Verify batch
        Object body = mock.getExchanges().get(0).getIn().getBody();
        assertInstanceOf(List.class, body);

        // Verify event type header is set
        String eventType = mock.getExchanges().get(0).getIn()
                .getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class);
        assertEquals("ExchangeCompleted", eventType);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Synchronous batching with batchSize=3
                from("event:ExchangeCompleted?batchSize=3&batchTimeout=5000&include=batchSourceRoute")
                        .routeId("batchConsumer")
                        .to("mock:batchResult");

                // Batch with short timeout to test partial flush
                from("event:ExchangeCompleted?batchSize=10&batchTimeout=500&include=timeoutBatchSourceRoute")
                        .routeId("timeoutBatchConsumer")
                        .to("mock:timeoutBatchResult");

                // Async + batching combined
                from("event:ExchangeCompleted?async=true&batchSize=2&batchTimeout=1000&include=asyncBatchSourceRoute")
                        .routeId("asyncBatchConsumer")
                        .to("mock:asyncBatchResult");

                from("direct:batchSource").routeId("batchSourceRoute")
                        .log("Processing batch source");

                from("direct:timeoutBatchSource").routeId("timeoutBatchSourceRoute")
                        .log("Processing timeout batch source");

                from("direct:asyncBatchSource").routeId("asyncBatchSourceRoute")
                        .log("Processing async batch source");
            }
        };
    }
}
