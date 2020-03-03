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
package org.apache.camel.component.disruptor;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DisruptorInOutChainedWithOnCompletionTest extends CamelTestSupport {
    @Test
    public void testInOutDisruptorChainedWithCustomOnCompletion() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("start");
        getMockEndpoint("mock:b").expectedBodiesReceived("start-a");
        // the onCustomCompletion should be send very last (as it will be handed over)
        getMockEndpoint("mock:c").expectedBodiesReceived("start-a-b", "onCustomCompletion");

        final String reply = template.requestBody("disruptor:a", "start", String.class);
        assertEquals("start-a-b-c", reply);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:a").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        // should come in last
                        exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(final Exchange exchange) {
                                template.sendBody("mock:c", "onCustomCompletion");
                            }
                        });
                    }
                }).to("mock:a").transform(simple("${body}-a")).to("disruptor:b");

                from("disruptor:b").to("mock:b").transform(simple("${body}-b")).to("disruptor:c");

                from("disruptor:c").to("mock:c").transform(simple("${body}-c"));
            }
        };
    }
}
