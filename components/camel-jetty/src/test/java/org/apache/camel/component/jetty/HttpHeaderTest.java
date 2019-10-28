/*
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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HttpHeaderTest extends BaseJettyTest {

    @Test
    public void testHttpHeaders() throws Exception {
        String result = template.requestBody("direct:start", "hello", String.class);
        assertEquals("Should send a right http header to the server.", "Find the key!", result);
    }

    @Test
    public void testServerHeader() throws Exception {
        Exchange ex = template.request("http://localhost:{{port}}/server/mytest", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                // Do nothing here
            }
        });

        assertNotNull(ex.getOut().getHeader("Server"));
        assertNull(ex.getOut().getHeader("Date"));

        ex = template.request("http://localhost:{{port2}}/server/mytest", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                // Do nothing here
            }
        });

        assertNull(ex.getOut().getHeader("Server"));
        assertNotNull(ex.getOut().getHeader("Date"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").setHeader("SOAPAction", constant("http://xxx.com/interfaces/ticket")).setHeader("Content-Type", constant("text/xml; charset=utf-8"))
                    .setHeader(Exchange.HTTP_PROTOCOL_VERSION, constant("HTTP/1.0")).to("http://localhost:{{port}}/myapp/mytest");

                from("jetty:http://localhost:{{port}}/myapp/mytest").process(new Processor() {
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

                from("jetty:http://localhost:{{port}}/server/mytest").transform(constant("Response!"));

                // The setting only effect on a new server endpoint
                from("jetty:http://localhost:{{port2}}/server/mytest?sendServerVersion=false&sendDateHeader=true").transform(constant("Response!"));

            }
        };
    }

}
