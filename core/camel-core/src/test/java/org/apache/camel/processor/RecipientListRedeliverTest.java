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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class RecipientListRedeliverTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "mySlip", "mock:a,mock:b");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testThrowExceptionAtA() throws Exception {
        counter = 0;

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "mySlip", "mock:a,direct:a,mock:b");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertEquals("Forced", e.getCause().getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        assertEquals(1 + 3, counter); // first call + 3 redeliveries
    }

    @Test
    public void testThrowExceptionAtB() throws Exception {
        counter = 0;

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "mySlip", "mock:a,mock:b,direct:b,mock:c");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertEquals("Forced", e.getCause().getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        assertEquals(1 + 3, counter); // first call + 3 redeliveries
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // try to redeliver up till 3 times
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").recipientList(header("mySlip")).stopOnException();

                from("direct:a").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should be same input body
                        assertEquals("Hello World", exchange.getIn().getBody());
                        assertFalse("Should not have OUT", exchange.hasOut());
                        assertNull(exchange.getException());

                        counter++;
                        throw new IllegalArgumentException("Forced");
                    }
                });

                from("direct:b").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should be same input body
                        assertEquals("Hello World", exchange.getIn().getBody());
                        assertFalse("Should not have OUT", exchange.hasOut());
                        assertNull(exchange.getException());

                        // mutate IN body
                        exchange.getIn().setBody("Bye World");

                        counter++;
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }
}
