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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify that redelivery counters is working as expected.
 */
public class DeadLetterChannelRedeliveryTest extends ContextTestSupport {

    private static int counter;

    public void testRedeliveryTest() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery and 2 re-deliveries
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);
        mock.message(0).header("org.apache.camel.Redelivered").isEqualTo(Boolean.TRUE);
        mock.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(2);

        try {
            template.sendBody("direct:start", "Hello World");
        } catch (RuntimeCamelException e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        assertEquals(3, counter); // One call + 2 re-deliveries
    }

    public void testNoRedeliveriesTest() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery
        MockEndpoint mock = getMockEndpoint("mock:no");
        mock.expectedMessageCount(1);
        mock.message(0).header("org.apache.camel.Redelivered").isEqualTo(Boolean.FALSE);
        mock.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(0);

        try {
            template.sendBody("direct:no", "Hello World");
        } catch (RuntimeCamelException e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        assertEquals(1, counter); // One call
    }

    public void testOneRedeliveryTest() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery and 1 re delivery
        MockEndpoint mock = getMockEndpoint("mock:one");
        mock.expectedMessageCount(1);
        mock.message(0).header("org.apache.camel.Redelivered").isEqualTo(Boolean.TRUE);
        mock.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(1);

        try {
            template.sendBody("direct:one", "Hello World");
        } catch (RuntimeCamelException e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        assertEquals(2, counter); // One call + 1 re-delivery
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2))
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            counter++;
                            throw new Exception("Forced exception by unit test");
                        }
                    });

                from("direct:no")
                    .errorHandler(deadLetterChannel("mock:no").maximumRedeliveries(0))
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            counter++;
                            throw new Exception("Forced exception by unit test");
                        }
                    });

                from("direct:one")
                    .errorHandler(deadLetterChannel("mock:one").maximumRedeliveries(1))
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
