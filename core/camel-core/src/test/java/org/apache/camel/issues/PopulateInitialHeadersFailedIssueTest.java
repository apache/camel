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
package org.apache.camel.issues;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PopulateInitialHeadersFailedIssueTest extends ContextTestSupport {

    @Test
    public void testPopulateInitialHeadersFailed() throws Exception {
        Exchange exchange = DefaultExchange.newFromEndpoint(context.getEndpoint("seda:start"));
        exchange.setPattern(ExchangePattern.InOut);
        MyFaultMessage msg = new MyFaultMessage(exchange);
        exchange.setMessage(msg);
        msg.setBody("Hello World");

        getMockEndpoint("mock:result").expectedMessageCount(0);
        template.send("seda:start", exchange);
        assertMockEndpointsSatisfied();

        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, exchange.getException());
        Assertions.assertEquals("Forced headers error", iae.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable redelivery which forces copy defensive headers
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));

                from("seda:start")
                        .to("mock:result");
            }
        };
    }

    private static class MyFaultMessage extends DefaultMessage {

        public MyFaultMessage(Exchange exchange) {
            super(exchange);
        }

        @Override
        protected void populateInitialHeaders(Map<String, Object> map) {
            throw new IllegalArgumentException("Forced headers error");
        }
    }
}
