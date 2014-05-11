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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpBridgeRouteUsingHttpClientTest extends BaseNettyTest {

    private int port1;
    private int port2;

    @Test
    public void testBridge() throws Exception {
        String response = template.requestBodyAndHeader("http://localhost:" + port2 + "/test/hello",
                new ByteArrayInputStream("This is a test".getBytes()), "Content-Type", "application/xml", String.class);
        assertEquals("Get a wrong response", "/", response);

        response = template.requestBody("http://localhost:" + port1 + "/hello/world", "hello", String.class);
        assertEquals("Get a wrong response", "/hello/world", response);

        try {
            template.requestBody("http://localhost:" + port2 + "/hello/world", "hello", String.class);
            fail("Expect exception here!");
        } catch (Exception ex) {
            assertTrue("We should get a RuntimeCamelException", ex instanceof RuntimeCamelException);
        }
    }
    
    @Test
    public void testSendFormRequestMessage() throws Exception {
        String out = template.requestBodyAndHeader("http://localhost:" + port2 + "/form", "username=abc&pass=password", Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded", String.class);
        assertEquals("Get a wrong response message", "username=abc&pass=password", out);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();

                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // get the request URL and copy it to the request body
                        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                        exchange.getOut().setBody(uri);
                    }
                };
                from("netty-http:http://localhost:" + port2 + "/test/hello")
                        .to("http://localhost:" + port1 + "?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("netty-http:http://localhost:" + port1 + "?matchOnUriPrefix=true").process(serviceProc);
                
                // check the from request
                from("netty-http:http://localhost:" + port2 + "/form?bridgeEndpoint=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // just take out the message body and send it back
                            Message in = exchange.getIn();
                            String request = in.getBody(String.class);
                            exchange.getOut().setBody(request);
                        }
                        
                    });
            }
        };
    }

}
