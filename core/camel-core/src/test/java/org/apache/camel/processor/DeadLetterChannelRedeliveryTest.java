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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test to verify that redelivery counters is working as expected.
 */
public class DeadLetterChannelRedeliveryTest extends ContextTestSupport {

    private static int counter;
    private static int redeliveryCounter;

    @Test
    public void testTwoRedeliveryTest() throws Exception {
        counter = 0;
        redeliveryCounter = 0;

        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:two", "Hello World");

        assertMockEndpointsSatisfied();

        // One call + 2 re-deliveries
        assertEquals(3, counter);
        // 2 re-deliveries
        assertEquals(2, redeliveryCounter);
    }

    @Test
    public void testNoRedeliveriesTest() throws Exception {
        counter = 0;
        redeliveryCounter = 0;

        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:no", "Hello World");

        assertMockEndpointsSatisfied();

        // One call
        assertEquals(1, counter);
        // no redeliveries
        assertEquals(0, redeliveryCounter);
    }

    @Test
    public void testOneRedeliveriesTest() throws Exception {
        counter = 0;
        redeliveryCounter = 0;

        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:one", "Hello World");

        assertMockEndpointsSatisfied();

        // One call + 1 redelivery
        assertEquals(2, counter);
        // 1 redelivery
        assertEquals(1, redeliveryCounter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                bindToRegistry("redeliveryProcessor", (Processor) (exchange -> {
                    redeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                }));
                from("direct:two")
                        .errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0)
                                .onRedeliveryRef("redeliveryProcessor"))
                        // route start here
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                counter++;
                                throw new Exception("Forced exception by unit test");
                            }
                        });

                from("direct:no")
                        .errorHandler(
                                deadLetterChannel("mock:error").maximumRedeliveries(0).onRedeliveryRef("redeliveryProcessor"))
                        // route start here
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                counter++;
                                throw new Exception("Forced exception by unit test");
                            }
                        });

                from("direct:one")
                        .errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(1).redeliveryDelay(0)
                                .onRedeliveryRef("redeliveryProcessor"))
                        // route start here
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                counter++;
                                throw new Exception("Forced exception by unit test");
                            }
                        });
            }
        };
    }

}
