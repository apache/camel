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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class DynamicRoutersWithJMSMessageLostHeadersIssueTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue1")
                        .setHeader("HEADER1", constant("header1"))
                        .dynamicRouter(method(DynamicRouter.class, "dynamicRoute"))
                        .to("mock:checkHeader");

                from("direct:foo")
                        .setHeader("HEADER1", constant("header1"))
                        .dynamicRouter(method(DynamicRouter.class, "dynamicRoute"))
                        .to("mock:checkHeader");
            }
        };
    }

    @Test
    public void testHeaderShouldExisted() throws InterruptedException {
        // direct
        getMockEndpoint("mock:checkHeader").expectedMessageCount(1);
        getMockEndpoint("mock:checkHeader").expectedHeaderReceived("HEADER1", "header1");

        template.sendBody("direct:foo", "A");

        assertMockEndpointsSatisfied();
        resetMocks();

        // actvivemq
        getMockEndpoint("mock:checkHeader").expectedMessageCount(1);
        getMockEndpoint("mock:checkHeader").expectedHeaderReceived("HEADER1", "header1");

        template.sendBody("activemq:queue1", "A");

        assertMockEndpointsSatisfied();
    }

    public static class DynamicRouter {

        public String dynamicRoute(Exchange exchange, @Header(Exchange.SLIP_ENDPOINT) String previous) {
            if (previous == null) {
                return "file://target/outbox";
            } else {
                //end slip
                return null;
            }
        }

    }
}
