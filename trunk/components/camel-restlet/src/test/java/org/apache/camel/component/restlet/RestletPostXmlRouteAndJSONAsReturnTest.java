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
import org.junit.Test;

/**
 * @version 
 */
public class RestletPostXmlRouteAndJSONAsReturnTest extends RestletTestSupport {
    
    private static final String REQUEST_MESSAGE = 
        "<mail><body>HelloWorld!</body><subject>test</subject><to>x@y.net</to></mail>";
    private static final String REQUEST_MESSAGE_WITH_XML_TAG = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + REQUEST_MESSAGE;

    private String url = "restlet:http://localhost:" + portNum + "/users?restletMethod=POST";
    

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable POST support
                from(url)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            assertTrue("Get a wrong request message", body.indexOf(REQUEST_MESSAGE) >= 0);
                            exchange.getOut().setBody("{OK}");
                            exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
                        }
                    });

                // route to restlet
                from("direct:start").to(url);
            }
        };
    }

    @Test
    public void testPostXml() throws Exception {
        postRequestMessage(REQUEST_MESSAGE);
    }
    
    @Test
    public void testPostXmlWithXmlTag() throws Exception {
        postRequestMessage(REQUEST_MESSAGE_WITH_XML_TAG);
    }
    
    protected void postRequestMessage(final String message) throws Exception {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(message);
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            }
        });

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        String s = exchange.getOut().getBody(String.class);
        assertEquals("{OK}", s);
    }

}
