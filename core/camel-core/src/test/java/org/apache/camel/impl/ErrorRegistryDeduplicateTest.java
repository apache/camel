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
package org.apache.camel.impl;

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErrorRegistryDeduplicateTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        context.setMessageHistory(true);
        return context;
    }

    @Test
    public void testDeduplicatesSameExchangeId() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        Collection<BacklogErrorEventMessage> entries = context.getErrorRegistry().browse();
        assertEquals(1, entries.size(), "Same exchangeId should appear only once in the error registry");
        assertEquals(true, entries.iterator().next().isHandled());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(0));

                from("direct:start").routeId("dedup")
                        .to("direct:sub");

                // sub-route throws exception which is handled by its own onException
                // firing ExchangeFailureHandledEvent, then the error propagates to parent
                // route's deadLetterChannel which fires another ExchangeFailureHandledEvent
                from("direct:sub").routeId("sub")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(0))
                        .throwException(new IllegalArgumentException("Forced error"));
            }
        };
    }
}
