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
package org.apache.camel.builder.saxon;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class XQueryRecipientListTest extends CamelTestSupport {

    protected MockEndpoint londonEndpoint;
    protected MockEndpoint tampaEndpoint;

    @Test
    public void testSendLondonMessage() throws Exception {
        londonEndpoint.expectedMessageCount(1);
        tampaEndpoint.expectedMessageCount(0);

        template.sendBody("direct:start", "<person name='James' city='London'/>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendTampaMessage() throws Exception {
        londonEndpoint.expectedMessageCount(0);
        tampaEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "<person name='Hiram' city='Tampa'/>");

        assertMockEndpointsSatisfied();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        londonEndpoint = getMockEndpoint("mock:foo.London");
        tampaEndpoint = getMockEndpoint("mock:foo.Tampa");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // TODO is there a nicer way to do this with XQuery?

                // START SNIPPET: example
                from("direct:start").recipientList().xquery("concat('mock:foo.', /person/@city)", String.class);
                // END SNIPPET: example
            }
        };
    }
}
