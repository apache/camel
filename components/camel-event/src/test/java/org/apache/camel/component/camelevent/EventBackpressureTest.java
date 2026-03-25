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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventBackpressureTest extends CamelTestSupport {

    @Test
    void testAsyncWithBlockPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:blockResult");
        mock.expectedMinimumMessageCount(3);

        template.sendBody("direct:blockSource", "Hello 1");
        template.sendBody("direct:blockSource", "Hello 2");
        template.sendBody("direct:blockSource", "Hello 3");

        mock.assertIsSatisfied();

        // All events should have been processed (Block policy waits)
        assertTrue(mock.getExchanges().size() >= 3);
        mock.getExchanges().forEach(e -> {
            assertEquals("ExchangeCompleted",
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class));
        });
    }

    @Test
    void testAsyncWithDropPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:dropResult");
        // With drop policy, some events may be lost but no exceptions should occur
        mock.expectedMinimumMessageCount(1);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:dropSource", "Hello " + i);
        }

        mock.assertIsSatisfied();

        // Verify received events are valid
        mock.getExchanges().forEach(e -> {
            assertEquals("ExchangeCompleted",
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class));
        });
    }

    @Test
    void testAsyncWithQueueSize() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:queueSizeResult");
        mock.expectedMinimumMessageCount(2);

        template.sendBody("direct:queueSizeSource", "Hello 1");
        template.sendBody("direct:queueSizeSource", "Hello 2");

        mock.assertIsSatisfied();

        assertTrue(mock.getExchanges().size() >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted?async=true&backpressurePolicy=Block&asyncQueueSize=100&include=blockSourceRoute")
                        .routeId("blockConsumer")
                        .to("mock:blockResult");

                from("event:ExchangeCompleted?async=true&backpressurePolicy=Drop&asyncQueueSize=5&include=dropSourceRoute")
                        .routeId("dropConsumer")
                        .to("mock:dropResult");

                from("event:ExchangeCompleted?async=true&asyncQueueSize=50&include=queueSizeSourceRoute")
                        .routeId("queueSizeConsumer")
                        .to("mock:queueSizeResult");

                from("direct:blockSource").routeId("blockSourceRoute")
                        .log("Processing block source");

                from("direct:dropSource").routeId("dropSourceRoute")
                        .log("Processing drop source");

                from("direct:queueSizeSource").routeId("queueSizeSourceRoute")
                        .log("Processing queue size source");
            }
        };
    }
}
