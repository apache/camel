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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class RedeliveryWithExceptionAndFaultDelayInHeader extends ContextTestSupport {

    private static int counter;

    public void testOk() throws Exception {
        counter = 0;

        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    public void testTransientAndPersistentError() throws Exception {
        counter = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);

        String out = template.requestBody("direct:start", "Boom", String.class);
        assertEquals("Persistent error", out);

        assertMockEndpointsSatisfied();
    }

    public void testTransientAndPersistentErrorWithExchange() throws Exception {
        counter = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Boom");
            }
        });
        assertTrue("Should be failed", out.isFailed());
        assertNull("No exception", out.getException());
        assertTrue(out.getOut() != null && out.getOut().isFault());
        assertEquals("Persistent error", out.getOut().getBody());

        assertMockEndpointsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(5));

                from("direct:start")

                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setHeader(Exchange.REDELIVERY_DELAY, 100);
                                counter++;
                                if (counter < 3) {
                                    throw new IllegalArgumentException("Try again");
                                }

                                if (exchange.getIn().getBody().equals("Boom")) {
                                    exchange.getOut().setFault(true);
                                    exchange.getOut().setBody("Persistent error");
                                } else {
                                    exchange.getOut().setBody("Bye World");
                                }
                            }
                        }).to("mock:result");
            }
        };
    }
}
