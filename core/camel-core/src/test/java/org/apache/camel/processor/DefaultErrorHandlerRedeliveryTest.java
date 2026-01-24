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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test to verify that redelivery counters is working as expected.
 */
public class DefaultErrorHandlerRedeliveryTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testRedeliveryTest() {
        counter = 0;

        assertThrows(RuntimeCamelException.class, () -> template.sendBody("direct:start", "Hello World"));

        // One call + 2 re-deliveries
        assertEquals(3, counter);
    }

    @Test
    public void testNoRedeliveriesTest() {
        counter = 0;

        assertThrows(RuntimeCamelException.class, () -> template.sendBody("direct:no", "Hello World"));

        // One call
        assertEquals(1, counter);
    }

    @Test
    public void testOneRedeliveryTest() {
        counter = 0;
        assertThrows(RuntimeCamelException.class, () -> template.sendBody("direct:one", "Hello World"));

        // One call + 1 re-delivery
        assertEquals(2, counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").errorHandler(defaultErrorHandler().redeliveryDelay(0).maximumRedeliveries(2))
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                counter++;
                                throw new Exception("Forced exception by unit test");
                            }
                        });

                from("direct:no").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        counter++;
                        throw new Exception("Forced exception by unit test");
                    }
                });

                from("direct:one").errorHandler(defaultErrorHandler().redeliveryDelay(0).maximumRedeliveries(1))
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
