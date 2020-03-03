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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

public class LogDebugBodyMaxCharsTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "20");
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("logFormatter", new TraceExchangeFormatter());
        return registry;
    }

    @Test
    public void testLogBodyMaxLengthTest() throws Exception {
        // create a big body
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            int value = i % 10;
            sb.append(value);
        }
        String body = sb.toString();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        // should be clipped after 20 chars
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: 01234567890123456789... [Body clipped after 20 chars, total length is 1000]]"));

        // but body and clipped should not be the same
        assertNotSame("clipped log and real body should not be the same", msg, mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testNotClipped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "1234567890");

        assertMockEndpointsSatisfied();

        // should not be clipped as the message is < 20 chars
        TraceExchangeFormatter myFormatter = context.getRegistry().lookupByNameAndType("logFormatter", TraceExchangeFormatter.class);
        String msg = myFormatter.getMessage();
        assertTrue(msg.endsWith("Body: 1234567890]"));

        // but body and clipped should not be the same
        assertNotSame("clipped log and real body should not be the same", msg, mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }
}
