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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RoutingSlipWithNonStandardExchangeTest extends ContextTestSupport {

    protected static final String ANSWER = "answer";
    protected static final String ROUTING_SLIP_HEADER = "routingSlipHeader";
    
    public void testRoutingSlipPreservesDifferentExchange()
        throws Exception {
        MockEndpoint end = getMockEndpoint("mock:z");
        end.expectedMessageCount(1);

        sendBody("direct:a", ROUTING_SLIP_HEADER, ",");

        assertMockEndpointsSatisfied();
        
        List<Exchange> exchanges = end.getExchanges();
        Exchange exchange = exchanges.get(0);
        assertIsInstanceOf(DummyExchange.class, exchange);
    }
   
    protected void sendBody(String endpoint, String header, String delimiter) {
        DummyExchange exchange = new DummyExchange(context, ExchangePattern.InOut);
        Message in = exchange.getIn();
        in.setHeader(header, "mock:y" + delimiter + "mock:z");
        in.setBody(ANSWER);        
        
        template.send(endpoint, exchange);
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {    
                // START SNIPPET: e1
                from("direct:a").routingSlip();
                // END SNIPPET: e1
            }
        };
    }
}
