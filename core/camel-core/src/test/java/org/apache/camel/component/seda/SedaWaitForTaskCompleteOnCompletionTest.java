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
package org.apache.camel.component.seda;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

public class SedaWaitForTaskCompleteOnCompletionTest extends ContextTestSupport {

    private static String done = "";

    @Test
    public void testAlways() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // 3 + 1 C and A should be last
        assertEquals("CCCCA", done);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                done = done + "A";
                            }
                        });
                    }
                }).to("seda:foo?waitForTaskToComplete=Always").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        done = done + "B";
                    }
                }).to("mock:result");

                from("seda:foo").errorHandler(noErrorHandler()).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        done = done + "C";
                    }
                }).throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
