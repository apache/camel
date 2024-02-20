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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Based on an issue on the camel user forum.
 */
public class InterceptSendToIssueTest extends ContextTestSupport {

    @Test
    public void testInterceptSendTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived(Exchange.INTERCEPTED_ENDPOINT, "direct://foo");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals("direct://start", exchange.getFromEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:foo").to("mock:foo");

                from("direct:start").setHeader(Exchange.FILE_NAME, constant("hello.txt")).to("direct:foo");
                from("direct:foo").log("Dummy");
            }
        };
    }
}
