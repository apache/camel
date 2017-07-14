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
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HttpRouteContentLengthTest extends CamelTestSupport {

    private int port1;
    private int port2;

    @Test
    public void testHttpClientContentLength() throws Exception {
        invokeService(port1);
    }
    
    @Test
    public void testHttpRouteContentLength() throws Exception {
        invokeService(port2);
    }
    
    private void invokeService(int port) {
        Exchange out = template.request("http://localhost:" + port + "/test", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Camel request.");
            }
        });

        assertNotNull(out);
        assertEquals("Bye Camel request.: 14", out.getOut().getBody(String.class));
    }
    
    protected String getHttpEndpointScheme() {
        return "http4://localhost:";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = AvailablePortFinder.getNextAvailable(8000);
                port2 = AvailablePortFinder.getNextAvailable(9000);

                // use jetty as server as it supports sending response as chunked encoding
                from("jetty:http://localhost:" + port1 + "/test")
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String contentLength = exchange.getIn().getHeader(Exchange.CONTENT_LENGTH, String.class);
                            String request = exchange.getIn().getBody(String.class);
                            exchange.getOut().setBody(request + ": " + contentLength);
                        }
                        
                    })
                    .transform().simple("Bye ${body}");
                
                // set up a netty http proxy
                from("jetty:http://localhost:" + port2 + "/test")
                    .to(getHttpEndpointScheme() + port1 + "/test?bridgeEndpoint=true&throwExceptionOnFailure=false");
          
            }
        };
    }
}
