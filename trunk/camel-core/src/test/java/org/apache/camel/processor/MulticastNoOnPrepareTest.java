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
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class MulticastNoOnPrepareTest extends ContextTestSupport {

    public void testMulticastNoOnPrepare() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:a").message(0).body(String.class).isEqualTo("1 Tony the Tiger");
        getMockEndpoint("mock:b").expectedMessageCount(1);
        // adjustment of processor a causes effects in processor b as its not a deep clone
        getMockEndpoint("mock:b").message(0).body(String.class).isEqualTo("1 Tony the Tiger");

        template.sendBody("direct:start", new Animal(1, "Tiger"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast().to("direct:a").to("direct:b");

                from("direct:a").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Animal body = exchange.getIn().getBody(Animal.class);
                        assertEquals(1, body.getId());
                        assertEquals("Tiger", body.getName());

                        // adjust the name
                        body.setName("Tony the Tiger");
                    }
                }).to("mock:a");

                from("direct:b").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Animal body = exchange.getIn().getBody(Animal.class);
                        assertEquals(1, body.getId());
                        assertEquals("Tony the Tiger", body.getName());
                    }
                }).to("mock:b");
            }
        };
    }

}
