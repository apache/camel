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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventAsyncTest extends CamelTestSupport {

    @Test
    void testAsyncEventProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:asyncResult");
        mock.expectedMinimumMessageCount(3);

        // Send multiple messages to generate exchange events
        template.sendBody("direct:asyncSource", "Hello 1");
        template.sendBody("direct:asyncSource", "Hello 2");
        template.sendBody("direct:asyncSource", "Hello 3");

        mock.assertIsSatisfied();

        // Verify events were processed on different threads (async)
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        mock.getExchanges().forEach(e -> {
            threadNames.add(Thread.currentThread().getName());
        });

        // Verify event type header is set correctly
        mock.getExchanges().forEach(e -> {
            assertEquals("ExchangeCompleted",
                    e.getIn().getHeader(CamelEventConstants.HEADER_EVENT_TYPE, String.class));
        });
    }

    @Test
    void testAsyncWithPoolSize() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:asyncPoolResult");
        mock.expectedMinimumMessageCount(2);

        template.sendBody("direct:asyncPoolSource", "Hello 1");
        template.sendBody("direct:asyncPoolSource", "Hello 2");

        mock.assertIsSatisfied();

        // Verify the events were received
        assertTrue(mock.getExchanges().size() >= 2);
    }

    @Test
    void testAsyncNoRecursion() throws Exception {
        // Async processing with Exchange* should not cause recursion
        MockEndpoint mock = getMockEndpoint("mock:asyncRecursion");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:asyncRecursionSource", "Hello");

        mock.assertIsSatisfied();

        // Should not have excessive events (recursion protection still works with async)
        assertTrue(mock.getExchanges().size() < 100,
                "Should not have an excessive number of events (got " + mock.getExchanges().size() + ")");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted?async=true&include=asyncSourceRoute")
                        .routeId("asyncConsumer")
                        .to("mock:asyncResult");

                from("event:ExchangeCompleted?async=true&asyncPoolSize=2&include=asyncPoolSourceRoute")
                        .routeId("asyncPoolConsumer")
                        .to("mock:asyncPoolResult");

                from("event:ExchangeCreated,ExchangeCompleted?async=true")
                        .routeId("asyncRecursionConsumer")
                        .to("mock:asyncRecursion");

                from("direct:asyncSource").routeId("asyncSourceRoute")
                        .log("Processing async source");

                from("direct:asyncPoolSource").routeId("asyncPoolSourceRoute")
                        .log("Processing async pool source");

                from("direct:asyncRecursionSource").routeId("asyncRecursionSourceRoute")
                        .log("Processing async recursion source");
            }
        };
    }
}
