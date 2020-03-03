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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

public class SedaWaitForTaskNewerOnCompletionTest extends ContextTestSupport {

    private static String done = "";
    private final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testNever() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // B should be first because we do not wait
        assertEquals("BCA", done);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:start").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                done = done + "A";
                                latch.countDown();
                            }
                        });
                    }
                }).to("seda:foo?waitForTaskToComplete=Never").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        done = done + "B";
                    }
                }).to("mock:result");

                from("seda:foo").errorHandler(noErrorHandler()).delay(1000).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        done = done + "C";
                    }
                }).throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
