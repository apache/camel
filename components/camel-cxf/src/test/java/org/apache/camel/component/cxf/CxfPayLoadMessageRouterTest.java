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
package org.apache.camel.component.cxf;

import java.util.List;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class CxfPayLoadMessageRouterTest extends CxfSimpleRouterTest {
    private String routerEndpointURI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";
    private String serviceEndpointURI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: payload
                from(routerEndpointURI).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message inMessage = exchange.getIn();
                        if (inMessage instanceof CxfMessage) {
                            CxfMessage message = (CxfMessage) inMessage;
                            List<Element> elements = message.getMessage().get(List.class);
                            assertNotNull("We should get the elements here" , elements);
                            assertEquals("Get the wrong elements size" , elements.size(), 1);
                            assertEquals("Get the wrong namespace URI" , elements.get(0).getNamespaceURI(), "http://cxf.component.camel.apache.org/");
                        }                        
                    }
                    
                })
                .to(serviceEndpointURI);
                // END SNIPPET: payload
            }
        };
    }
}
