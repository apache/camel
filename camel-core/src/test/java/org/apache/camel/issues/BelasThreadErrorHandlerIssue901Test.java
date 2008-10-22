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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Unit test to verify that error handling using thread() pool also works as expected.
 */
public class BelasThreadErrorHandlerIssue901Test extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(BelasThreadErrorHandlerIssue901Test.class);
    private String msg1 = "Message Intended For Processor #1";
    private String msg2 = "Message Intended For Processor #2";
    private String msg3 = "Message Intended For Processor #3";
    
    private int callCounter1;
    private int callCounter2;
    private int callCounter3;
    private int redelivery = 1;

    protected void setUp() throws Exception {
        disableJMX();
        super.setUp();
    }

    public void testThreadErrorHandlerLogging() throws Exception {
        MockEndpoint handled = getMockEndpoint("mock:handled");
        handled.expectedBodiesReceived(msg3);

        try {
            template.sendBody("direct:errorTest", msg1);
            fail("Should have thrown a MyBelaException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof MyBelaException);
        }

        assertMockEndpointsSatisfied();

        assertEquals(1, callCounter1);
        assertEquals(1, callCounter2);
        assertEquals(1 + redelivery, callCounter3);  // Only this should be more then 1
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:handled").maximumRedeliveries(redelivery));

                from("direct:errorTest")
                    .thread(5)
                    // Processor #1
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            callCounter1++;
                            LOG.debug("Processor #1 Received A " + exchange.getIn().getBody());
                            exchange.getOut().setBody(msg2);
                        }
                    })
                    // Processor #2
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            callCounter2++;
                            LOG.debug("Processor #2 Received A " + exchange.getIn().getBody());
                            exchange.getOut().setBody(msg3);
                        }
                    })
                    // Processor #3
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            callCounter3++;
                            LOG.debug("Processor #3 Received A " + exchange.getIn().getBody());
                            throw new MyBelaException("Forced exception by unit test");
                        }
                    });
            }
        };
    }

    public static class MyBelaException extends Exception {
        public MyBelaException(String message) {
            super(message);
        }
    }

}
