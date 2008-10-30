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

import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.LoggingLevel;

/**
 * @version $Revision$
 */
public class FaultRouteTest extends ContextTestSupport {
    protected MockEndpoint a;
    protected MockEndpoint b;
    protected MockEndpoint c;
    protected MockEndpoint err;
    protected boolean shouldWork = true;

    public void testWithOut() throws Exception {
        a.whenExchangeReceived(1, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getOut().setBody("out");
            }
        });
        a.expectedMessageCount(1);
        b.expectedBodiesReceived("out");
        c.expectedMessageCount(0);

        template.sendBody("direct:start", "in");

        MockEndpoint.assertIsSatisfied(a, b, c);
    }

    public void testWithFault() throws Exception {
        shouldWork = false;

        a.whenExchangeReceived(1, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getFault().setBody("fault");
            }
        });
        a.expectedMessageCount(1);
        b.expectedMessageCount(0);
        c.expectedMessageCount(0);

        template.sendBody("direct:start", "in");

        MockEndpoint.assertIsSatisfied(a, b, c);

        // TODO wrap up as an exception on the mock endpoint
        List<Exchange> list = a.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Message fault = exchange.getFault();
        assertNotNull("Should have a fault on A", fault);
        assertEquals("Fault body", "fault", fault.getBody());
    }

    public void testWithThrowFaultMessage() throws Exception {
        throwFaultTest("direct:string");
    }

    public void testWithThrowFaultException() throws Exception {
        throwFaultTest("direct:exception");
    }

    public void testWithThrowFaultMessageUnhandled() throws Exception {
        throwFaultTest("direct:fault");
    }

    public void testWithHandleFaultMessage() throws Exception {
        throwFaultTest("direct:error", 1);
    }

    private void throwFaultTest(String startPoint) throws InterruptedException {
        throwFaultTest(startPoint, 0);
    }

    private void throwFaultTest(String startPoint, int errors) throws InterruptedException {
        a.expectedMessageCount(1);
        b.expectedMessageCount(0);
        c.expectedMessageCount(0);
        err.expectedMessageCount(errors);

        try {
            template.sendBody(startPoint, "in");
        } catch (RuntimeCamelException e) {
            // expected
        }

        MockEndpoint.assertIsSatisfied(a, b, c, err);

        List<Exchange> list = a.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Message fault = exchange.getFault();
        assertNotNull("Should have a fault on A", fault);
        if (startPoint.equals("direct:exception")) {
            assertTrue("It should be the IllegalStateException",
                       fault.getBody() instanceof IllegalStateException);
            assertEquals("Fault message", "It makes no sense of business logic",
                         ((IllegalStateException)(fault.getBody())).getMessage());
        } else { // test for the throwFault with String
            if (errors == 0) {
                // fault *not* handled
                assertTrue("It should be the CamelException", fault.getBody() instanceof CamelException);
                assertEquals("Fault message", "ExceptionMessage", ((CamelException)(fault.getBody())).getMessage());
            } else {
                // fault handled, exception should contain the fault
                assertNull("Fault body should be null", fault.getBody());
                CamelException faultex = (CamelException)exchange.getException();
                assertNotNull("Exception body should contain the fault", faultex);
                assertEquals("Fault message", "ExceptionMessage", faultex.getMessage());
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        b = resolveMandatoryEndpoint("mock:b", MockEndpoint.class);
        c = resolveMandatoryEndpoint("mock:c", MockEndpoint.class);
        err = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:a").to("mock:b");

                from("direct:string").to("mock:a").throwFault("ExceptionMessage").to("mock:b");

                from("direct:exception").to("mock:a")
                    .throwFault(new IllegalStateException("It makes no sense of business logic"))
                    .to("mock:b");

                from("direct:fault").errorHandler(
                    deadLetterChannel("mock:error")
                            .maximumRedeliveries(2)
                        .loggingLevel(LoggingLevel.DEBUG))
                    .to("mock:a").throwFault("ExceptionMessage").to("mock:b");

                from("direct:error").errorHandler(
                    deadLetterChannel("mock:error")
                            .maximumRedeliveries(2)
                        .loggingLevel(LoggingLevel.DEBUG))
                    .to("mock:a").handleFault().throwFault("ExceptionMessage").to("mock:b");
            }
        };
    }
}
