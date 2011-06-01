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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class ExceptionTest extends ContextTestSupport {

    public void testExceptionWithoutHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testExceptionWithHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        exceptionEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testExceptionWithLongHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        exceptionEndpoint.expectedBodiesReceived("<handled/>");
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testLongRouteWithHandler() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");

        exceptionEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody("direct:start2", "<body/>");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testExceptionWithFatalException() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        MockEndpoint exceptionEndpoint = getMockEndpoint("mock:exception");
        MockEndpoint fatalEndpoint = getMockEndpoint("mock:fatal");
        MockEndpoint errorEndpoint = getMockEndpoint("mock:error");

        fatalEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedMessageCount(0);
        errorEndpoint.expectedMessageCount(1);
        // TODO: CAMEL-4022. Message should probably not go to DLC for this scenario
        // We need agree on a solution and implement it. See jira for more details.
        errorEndpoint.expectedBodiesReceived("<some-value/>");

        try {
            template.sendBody("direct:start2", "<body/>");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor exceptionThrower = new Processor() {
            public void process(Exchange exchange) throws Exception {
                String body = exchange.getIn().getBody(String.class);
                body = body.substring(1, body.length() - 2);
                boolean fatal = body.equals("exception") || body.indexOf(' ') != -1;
                String message = fatal ? "FATAL " : "";
                body = fatal ? message + body : "exception";

                // TODO: CAMEL-4022. Set a breakpoint here and see the body growing "FATAl FATAL ... Exception thrown"
                // See discussion in the issue above for solution found (which will probably lead to changing this test
                exchange.getIn().setBody("<" + body + "/>");
                throw new IllegalArgumentException(message + "Exception thrown");
            }
        };

        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                if (getName().endsWith("WithLongHandler")) {
                    log.debug("Using long exception handler");
                    onException(IllegalArgumentException.class).setBody(constant("<handled/>")).
                        to("mock:exception");
                } else if (getName().endsWith("WithHandler")) {
                    log.debug("Using exception handler");
                    onException(IllegalArgumentException.class).to("mock:exception");
                } else if (getName().endsWith("WithFatalException")) {
                    log.debug("Using fatal exception");
                    onException(IllegalArgumentException.class).process(exceptionThrower).to("mock:fatal");
                }
                from("direct:start").process(exceptionThrower).to("mock:result");
                from("direct:start2").to("direct:intermediate").to("mock:result");
                from("direct:intermediate").setBody(constant("<some-value/>")).process(exceptionThrower).to("mock:result");
            }
        };
    }
}

