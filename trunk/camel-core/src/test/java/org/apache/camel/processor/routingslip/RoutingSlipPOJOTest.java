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
package org.apache.camel.processor.routingslip;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutingSlip;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RoutingSlipPOJOTest extends ContextTestSupport {
    
    public void testRoutingSlipPOJO() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        MockEndpoint result = getMockEndpoint("mock:result");
        
        foo.expectedBodiesReceived("Message");
        result.expectedBodiesReceived("Message is processed!");       

        template.sendBody("direct:a", "Message");

        assertMockEndpointsSatisfied();
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").bean(new MyRoutingSlipPOJO());
                
                from("direct:b").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(exchange.getIn().getBody() + " is processed!");                        
                    }
                });
            }
        };
    }
    
    public class MyRoutingSlipPOJO {
        @RoutingSlip(context = "camel-1")
        public String[] doSomething(String body) {
            return new String[]{"mock:foo", "direct:b", "mock:result"};
        }
    }

}
