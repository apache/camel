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

import java.io.InputStream;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpConverter;
import org.apache.camel.http.common.HttpMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpConverterTest extends BaseJettyTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testToServletRequestAndResponse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/test")
                        // add this node to make sure the convert can work within
                        // DefaultMessageImpl
                        .convertBodyTo(String.class).process(new Processor() {
                            public void process(Exchange exchange) {
                                HttpServletRequest request = exchange.getIn(HttpServletRequest.class);
                                assertNotNull(request, "We should get request object here");
                                HttpServletResponse response = exchange.getIn(HttpServletResponse.class);
                                assertNotNull(response, "We should get response object here");
                                String s = exchange.getIn().getBody(String.class);
                                assertEquals("Hello World", s);
                            }
                        }).transform(constant("Bye World"));
            }
        });
        context.start();

        String out = template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testToServletInputStreamWithStreamCaching() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/test").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpMessage msg = exchange.getIn(HttpMessage.class);

                        // The ServletInputStream should be cached, and you can't read message here
                        ServletInputStream sis = HttpConverter.toServletInputStream(msg);
                        assertNotNull(sis);
                        assertEquals(0, sis.available());
                        String s = msg.getBody(String.class);

                        assertEquals("Hello World", s);
                    }
                }).transform(constant("Bye World"));
            }
        });
        context.start();

        String out = template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testToServletInputDisableStreamStreamCaching() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/test?disableStreamCache=true").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpMessage msg = exchange.getIn(HttpMessage.class);

                        // The ServletInputStream should not be cached
                        ServletInputStream sis = HttpConverter.toServletInputStream(msg);
                        assertNotNull(sis);
                        assertEquals(11, sis.available());
                        String s = context.getTypeConverter().convertTo(String.class, sis);

                        assertEquals("Hello World", s);
                    }
                }).transform(constant("Bye World"));
            }
        });
        context.setStreamCaching(false); // this test requires stream caching disabled to work with raw servlet
        context.start();

        String out = template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testToInputStream() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty://http://localhost:{{port}}/test").process(new Processor() {
                    public void process(Exchange exchange) {
                        HttpMessage msg = exchange.getIn(HttpMessage.class);

                        InputStream sis = msg.getBody(InputStream.class);
                        assertNotNull(sis);
                        String s = exchange.getContext().getTypeConverter().convertTo(String.class, sis);

                        assertEquals("Hello World", s);
                    }
                }).transform(constant("Bye World"));
            }
        });
        context.start();

        String out = template.requestBody("http://localhost:{{port}}/test", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testNulls() throws Exception {
        HttpMessage msg = null;
        assertNull(HttpConverter.toInputStream(msg, null));
        assertNull(HttpConverter.toServletInputStream(msg));
        assertNull(HttpConverter.toServletRequest(msg));
        assertNull(HttpConverter.toServletResponse(msg));

        HttpServletRequest req = null;
        assertNull(HttpConverter.toInputStream(req, null));
    }

}
