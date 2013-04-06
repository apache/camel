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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * @version 
 */
public class SedaInOutChainedWithOnCompletionTest extends ContextTestSupport {

    public void testInOutSedaChainedWithCustomOnCompletion() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("start");
        getMockEndpoint("mock:b").expectedBodiesReceived("start-a");
        // the onCustomCompletion should be send very last (as it will be handed over)
        getMockEndpoint("mock:c").expectedBodiesReceived("start-a-b", "onCustomCompletion");

        String reply = template.requestBody("seda:a", "start", String.class);
        assertEquals("start-a-b-c", reply);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // should come in last
                        exchange.addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                template.sendBody("mock:c", "onCustomCompletion");
                            }
                        });
                    }
                }).to("mock:a").transform(simple("${body}-a")).to("seda:b");

                from("seda:b").to("mock:b").transform(simple("${body}-b")).to("seda:c");

                from("seda:c").to("mock:c").transform(simple("${body}-c"));
            }
        };
    }
}