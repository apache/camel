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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class ProcessorMutateExchangeRedeliverTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testRedeliverA() throws Exception {
        counter = 0;

        try {
            template.sendBody("direct:a", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // first call + 3 redeliveries
        assertEquals(1 + 3, counter);
    }

    @Test
    public void testRedeliverB() throws Exception {
        counter = 0;

        try {
            template.sendBody("direct:b", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // first call + 3 redeliveries
        assertEquals(1 + 3, counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // try to redeliver up till 3 times
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:a").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should be same input body
                        assertEquals("Hello World", exchange.getIn().getBody());
                        assertFalse(exchange.hasOut(), "Should not have OUT");
                        assertNull(exchange.getException());

                        // mutate IN body
                        exchange.getIn().setBody("Bye World");

                        counter++;
                        throw new IllegalArgumentException("Forced");
                    }
                });

                from("direct:b").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should be same input body
                        assertEquals("Hello World", exchange.getIn().getBody());
                        assertFalse(exchange.hasOut(), "Should not have OUT");
                        assertNull(exchange.getException());

                        // mutate OUT body
                        exchange.getMessage().setBody("Bye World");

                        counter++;
                        throw new IllegalArgumentException("Forced");
                    }
                });
            }
        };
    }

}
