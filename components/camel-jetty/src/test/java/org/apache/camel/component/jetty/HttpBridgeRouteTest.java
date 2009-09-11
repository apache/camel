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
package org.apache.camel.component.jetty;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HttpBridgeRouteTest extends CamelTestSupport {
    @Test
    public void testHttpClient() throws Exception {
       

        String response = template.requestBodyAndHeader("http://localhost:9090/test/hello", new ByteArrayInputStream("This is a test".getBytes()), "Content-Type", "application/xml", String.class);
        
        assertEquals("Get a wrong response", "/test/hello", response);
        
        response = template.requestBody("http://localhost:9080/hello/world", "hello", String.class);
        
        assertEquals("Get a wrong response", "/hello/world", response);
        
        try {
            template.requestBody("http://localhost:9090/hello/world", "hello", String.class);
            fail("Expect exception here!");
        } catch (Exception ex) {
            assertTrue("We should get a RuntimeCamelException", ex instanceof RuntimeCamelException);
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // get the request URL and copy it to the request body
                        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                        exchange.getOut().setBody(uri);
                    }
                };
                from("jetty:http://localhost:9090/test/hello")
                    .to("http://localhost:9080?throwExceptionOnFailure=false&bridgeEndpoint=true");
                
                from("jetty://http://localhost:9080?matchOnUriPrefix=true").process(serviceProc);
                              
                
            }
        };
    }    

}
