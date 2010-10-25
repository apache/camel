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

import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.JettyContentTypeTest.MyBookService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.junit.Test;

public class HttpHeaderTest extends CamelTestSupport {

    @Test
    public void testHttpHeaders() throws Exception {
        String result = template.requestBody("direct:start", "hello", String.class);
        assertEquals("Should send a right http header to the server.", "Find the key!", result);
       
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").setHeader("SOAPAction", constant("http://xxx.com/interfaces/ticket"))
                    .setHeader("Content-Type", constant("text/xml; charset=utf-8"))
                    .setHeader(Exchange.HTTP_PROTOCOL_VERSION, constant("HTTP/1.0"))
                    .to("http://localhost:9080/myapp/mytest");

                from("jetty:http://localhost:9080/myapp/mytest").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Map<String, Object> headers = exchange.getIn().getHeaders();
                        ServletRequest request = exchange.getIn().getHeader(Exchange.HTTP_SERVLET_REQUEST, ServletRequest.class);
                        assertNotNull(request);
                        assertEquals("Get a wong http protocol version", request.getProtocol(), "HTTP/1.0");
                        for (Entry<String, Object> entry : headers.entrySet()) {
                            if ("SOAPAction".equals(entry.getKey()) && "http://xxx.com/interfaces/ticket".equals(entry.getValue())) {
                                exchange.getOut().setBody("Find the key!");
                                return;
                            }
                        }
                        exchange.getOut().setBody("Cannot find the key!");
                    }

                });

            }
        };
    }

}
