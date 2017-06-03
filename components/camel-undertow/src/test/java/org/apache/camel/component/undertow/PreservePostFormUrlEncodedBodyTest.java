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
package org.apache.camel.component.undertow;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.junit.Test;

public class PreservePostFormUrlEncodedBodyTest extends BaseUndertowTest {
    
    @Test
    public void testSendToUndertow() throws Exception {
        Exchange exchange = template.request("http://localhost:{{port}}/myapp/myservice?query1=a&query2=b", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("b1=x&b2=y");
                exchange.getIn().setHeader("content-type", "application/x-www-form-urlencoded");
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
            }
                                        
        });
        // convert the response to a String
        String body = exchange.getOut().getBody(String.class);
        assertEquals("Request message is OK", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}/myapp/myservice?map").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Map body = exchange.getIn().getBody(Map.class);
                        
                        // for unit testing make sure we got right message
                        assertNotNull(body);
                        assertEquals("x", body.get("b1"));
                        assertEquals("y", body.get("b2"));
                        assertEquals("a", exchange.getIn().getHeader("query1"));
                        assertEquals("b", exchange.getIn().getHeader("query2"));
                        assertEquals("x", exchange.getIn().getHeader("b1"));
                        assertEquals("y", exchange.getIn().getHeader("b2"));
                        
                        // send a response
                        exchange.getOut().setBody("Request message is OK");
                    }
                });
            }
        };
    }

}
