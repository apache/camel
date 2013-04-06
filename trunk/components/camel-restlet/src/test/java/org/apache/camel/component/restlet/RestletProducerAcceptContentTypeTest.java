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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for sending "Accept" HTTP header
 */
public class RestletProducerAcceptContentTypeTest extends RestletPostXmlRouteAndJSONAsReturnTest {
    private String url = "restlet:http://localhost:" + portNum + "/users?restletMethod=POST";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {                
                
                from("jetty://http://localhost:" + portNum + "/users")
                    .process(new Processor() {                    
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            exchange.getOut().setBody("{OK}");
                            exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                            assertEquals("application/json", exchange.getIn().getHeader("Accept", String.class));
                            
                        }
                    });

                // route to restlet
                from("direct:start").to(url);
            }
        };
    }
    
    @Override
    protected void postRequestMessage(final String message) throws Exception {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(message);
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getIn().setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
                
            }
        });

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        String s = exchange.getOut().getBody(String.class);
        assertEquals("{OK}", s);
    }
}
