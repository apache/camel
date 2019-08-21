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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RomeksExceptionTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RomeksExceptionTest.class);

    @Test
    public void testRouteA() throws Exception {
        assertErrorHandlingWorks("a");
    }

    @Test
    public void testRouteB() throws Exception {
        assertErrorHandlingWorks("b");
    }

    protected void assertErrorHandlingWorks(String route) throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        resultEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedBodiesReceived("<exception/>");

        try {
            template.sendBodyAndHeader("direct:start", "<body/>", "route", route);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Exception thrown intentionally.", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        List<Exchange> list = exceptionEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        LOG.debug("Received: " + exchange.getIn());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor exceptionThrower = new Processor() {
            public void process(Exchange exchange) throws Exception {
                LOG.debug("About to throw exception on " + exchange);

                exchange.getIn().setBody("<exception/>");
                throw new IllegalArgumentException("Exception thrown intentionally.");
            }
        };

        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0));

                onException(IllegalArgumentException.class).to("mock:exception");

                from("direct:start").recipientList().simple("direct:${header.route}").to("mock:result");

                from("direct:a").setBody(constant("<some-value/>")).process(exceptionThrower).to("mock:result");

                from("direct:b").process(exceptionThrower).setBody(constant("<some-value/>")).to("mock:result");
            }
        };
    }
}
