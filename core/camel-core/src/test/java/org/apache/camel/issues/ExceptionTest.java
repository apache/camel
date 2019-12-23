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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ExceptionTest extends ContextTestSupport {

    @Test
    public void testExceptionWithoutHandler() throws Exception {
        MockEndpoint errorEndpoint = getMockEndpoint("mock:error");
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        errorEndpoint.expectedBodiesReceived("<exception/>");
        exceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedMessageCount(0);

        // we don't expect any thrown exception here as there's no onException
        // clause defined for this test
        // so that the general purpose dead letter channel will come into the
        // play and then when all the attempts
        // to redelivery fails the exchange will be moved to "mock:error" and
        // then from the client point of
        // view the exchange is completed.
        template.sendBody("direct:start", "<body/>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionWithHandler() throws Exception {
        MockEndpoint errorEndpoint = getMockEndpoint("mock:error");
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        errorEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedBodiesReceived("<exception/>");
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionWithLongHandler() throws Exception {
        MockEndpoint errorEndpoint = getMockEndpoint("mock:error");
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        errorEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedBodiesReceived("<not-handled/>");
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLongRouteWithHandler() throws Exception {
        MockEndpoint errorEndpoint = getMockEndpoint("mock:error");
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        errorEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedBodiesReceived("<exception/>");
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start2", "<body/>");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor exceptionThrower = new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<exception/>");
                throw new IllegalArgumentException("Exception thrown intentionally.");
            }
        };

        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                if (getName().endsWith("WithLongHandler")) {
                    log.debug("Using long exception handler");
                    onException(IllegalArgumentException.class).setBody(constant("<not-handled/>")).to("mock:exception");
                } else if (getName().endsWith("WithHandler")) {
                    log.debug("Using exception handler");
                    onException(IllegalArgumentException.class).to("mock:exception");
                }
                from("direct:start").process(exceptionThrower).to("mock:result");
                from("direct:start2").to("direct:intermediate").to("mock:result");
                from("direct:intermediate").setBody(constant("<some-value/>")).process(exceptionThrower).to("mock:result");
            }
        };
    }
}
