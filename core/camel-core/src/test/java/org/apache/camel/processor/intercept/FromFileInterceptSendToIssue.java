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

/**
 * Based on an issue on the camel user forum.
 */
public class FromFileInterceptSendToIssue extends ContextTestSupport {

    public void testInterceptSendTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.INTERCEPTED_ENDPOINT, "seda://foo");

        template.sendBodyAndHeader("file://target/intercept", "Hello World", Exchange.FILE_NAME, "input.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertTrue(exchange.getFromEndpoint().getEndpointUri().startsWith("file://target/intercept"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:foo").to("mock:foo");

                from("file://target/intercept?initialDelay=0&delay=10").setHeader(Exchange.FILE_NAME, constant("hello.txt")).to("seda:foo");
            }
        };
    }
}
