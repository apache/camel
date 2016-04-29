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
package org.apache.camel.itest.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class Http4RouteTest extends CamelTestSupport {
    private int port1;
    private int port2;
    
    @Test
    public void sendHttpGetRequestTest() {
        String response = template.requestBody("http4://localhost:" + port1 
                         + "/test?aa=bb&httpClient.socketTimeout=10000&httpClient.connectTimeout=10000"
                         + "&bridgeEndpoint=true&throwExceptionOnFailure=false", null, String.class);
        assertEquals("Get a wrong response", "aa=bb", response);
        
        response = template.requestBodyAndHeader("direct:start1", null, Exchange.HTTP_QUERY, "aa=bb", String.class);
        
        assertEquals("Get a wrong response", "aa=bb", response);
        
        response = template.requestBodyAndHeader("direct:start2", null, Exchange.HTTP_QUERY, "aa=bb", String.class);
        assertEquals("Get a wrong response", "aa=bb&2", response);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port1 = AvailablePortFinder.getNextAvailable(8000);
        port2 = AvailablePortFinder.getNextAvailable(9000);
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                
                from("jetty:http://localhost:" + port1 + "/test").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        HttpMessage message = (HttpMessage)exchange.getIn();
                        assertNotNull(message.getRequest());
                        assertEquals("GET", message.getRequest().getMethod());
                        exchange.getOut().setBody(message.getRequest().getQueryString());
                        
                    }
                    
                });
                
                from("jetty:http://localhost:" + port2 + "/test").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        HttpMessage message = (HttpMessage)exchange.getIn();
                        assertNotNull(message.getRequest());
                        assertEquals("GET", message.getRequest().getMethod());
                        exchange.getOut().setBody(message.getRequest().getQueryString() + "&2");
                    }
                    
                });
                
                from("direct:start1").to("http4://localhost:" + port1 + "/test");
                
                from("direct:start2").to("http4://localhost:" + port2 + "/test");
                
                
                   
            }
        };
    }

}
