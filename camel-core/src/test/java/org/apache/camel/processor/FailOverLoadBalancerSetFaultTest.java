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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class FailOverLoadBalancerSetFaultTest extends ContextTestSupport {
    
    public void testFailOverSetFault() throws Exception {
        getMockEndpoint("mock:failover1").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:failover2").expectedBodiesReceived("Hello World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye Camel", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .loadBalance().failover(1, false, false, IOException.class)
                        .to("seda:failover1", "seda:failover2")
                    .end();

                from("seda:failover1")
                        .to("mock:failover1")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // mutate the message
                                exchange.getOut().setBody("Hi Camel");
                                // and then set fault directly on OUT for example as camel-cxf would do
                                exchange.getOut().setFault(true);
                                exchange.setException(new IOException("Forced exception for test"));
                            }
                        });

                from("seda:failover2")
                        .to("mock:failover2")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody("Bye Camel");
                            }
                        });
            }
        };
    }
}
