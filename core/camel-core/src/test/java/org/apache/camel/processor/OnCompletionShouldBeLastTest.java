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
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

public class OnCompletionShouldBeLastTest extends ContextTestSupport {

    @Test
    public void testOnCompletionShouldBeLast() throws Exception {
        getMockEndpoint("mock:sync").expectedBodiesReceived("C", "B", "A", "Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().to("mock:sync");

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                template.sendBody("mock:sync", "A");
                            }

                            @Override
                            public String toString() {
                                return "A";
                            }
                        });

                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                template.sendBody("mock:sync", "B");
                            }

                            @Override
                            public String toString() {
                                return "B";
                            }
                        });

                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                template.sendBody("mock:sync", "C");
                            }

                            @Override
                            public String toString() {
                                return "C";
                            }
                        });
                    }
                }).to("mock:result");
            }
        };
    }

}
