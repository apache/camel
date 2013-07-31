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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class RecipientListExchangePropertyTest extends ContextTestSupport {

    public void testRecipientExchangeProperty() throws Exception {
        getMockEndpoint("mock:a").expectedPropertyReceived(Exchange.RECIPIENT_LIST_ENDPOINT, "direct://a");
        getMockEndpoint("mock:a").expectedPropertyReceived(Exchange.TO_ENDPOINT, "mock://a");
        getMockEndpoint("mock:b").expectedPropertyReceived(Exchange.RECIPIENT_LIST_ENDPOINT, "direct://b");
        getMockEndpoint("mock:b").expectedPropertyReceived(Exchange.TO_ENDPOINT, "mock://b");
        getMockEndpoint("mock:c").expectedPropertyReceived(Exchange.RECIPIENT_LIST_ENDPOINT, "direct://c");
        getMockEndpoint("mock:c").expectedPropertyReceived(Exchange.TO_ENDPOINT, "mock://c");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello c");
        // would be the last one
        mock.expectedPropertyReceived(Exchange.RECIPIENT_LIST_ENDPOINT, "direct://c");

        String out = template.requestBodyAndHeader("direct:start", "Hello World", "slip", "direct:a,direct:b,direct:c", String.class);
        assertEquals("Hello c", out);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").recipientList(header("slip")).to("mock:result");

                from("direct:a").to("mock:a").transform(constant("Hello a"));
                from("direct:b").to("mock:b").transform(constant("Hello b"));
                from("direct:c").to("mock:c").transform(constant("Hello c"));
            }
        };
    }

}