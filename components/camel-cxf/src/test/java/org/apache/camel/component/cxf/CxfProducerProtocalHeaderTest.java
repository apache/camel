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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;

/**
 * 
 */
public class CxfProducerProtocalHeaderTest extends ContextTestSupport {
    private static final String RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
        + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
        + "</ns1:echoResponse></soap:Body></soap:Envelope>";
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:9008/user").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        assertNull("We should not get the this header", exchange.getOut().getHeader("CamelCxfTest"));
                       // check the headers
                        exchange.getOut().setHeader("Content-Type", "text/xml");                        
                       // send the response back 
                        exchange.getOut().setBody(RESPONSE);
                    }
                    
                });
            }
        };
    }
    
    private Exchange sendSimpleMessage(String endpointUri) {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add("Hello World!");
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "echo");
                // Test the CxfHeaderFilterStrategy
                exchange.getIn().setHeader("CamelCxfTest", "test");
            }
        });
        return exchange;

    }
        
    public void testSendMessage() {
        Exchange exchange = sendSimpleMessage("cxf://http://localhost:9008/user"
                                              + "?serviceClass=org.apache.camel.component.cxf.HelloService");
        org.apache.camel.Message out = exchange.getOut();
        String result = out.getBody(String.class);        
        assertEquals("reply body on Camel", "echo " + "Hello World!", result); 
    }

}
