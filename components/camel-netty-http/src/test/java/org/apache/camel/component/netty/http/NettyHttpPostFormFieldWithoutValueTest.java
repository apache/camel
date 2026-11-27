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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A text sent as application/x-www-form-urlencoded, as curl -d "some text" does, is a form field without =: it is
 * mapped to a header with an empty value instead of failing the request, and that header, whose name is not a valid
 * HTTP header name, is not written to the response.
 */
public class NettyHttpPostFormFieldWithoutValueTest extends BaseNettyTestSupport {

    private static final String TEXT = "Hi, I'm alice@example.com. My order is late, can you refund it?";

    @Test
    public void testFormFieldWithoutValue() {
        Exchange exchange = template.request("netty-http:http://localhost:{{port}}/echo", e -> {
            e.getIn().setBody(TEXT + "&a=1");
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded");
            e.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
        });

        assertNull(exchange.getException());
        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text=[] a=1", exchange.getMessage().getBody(String.class));
        assertEquals("1", exchange.getMessage().getHeader("a"));
        assertNull(exchange.getMessage().getHeader(TEXT));
    }

    @Test
    public void testFieldWithEmptyName() {
        // a field such as =x has an empty name, which the WHATWG parser keeps, and so does the binding; the
        // response does not write it, as it is not a valid header name
        Exchange exchange = template.request("netty-http:http://localhost:{{port}}/empty", e -> {
            e.getIn().setBody("=x&a=1");
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded");
            e.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
        });

        assertNull(exchange.getException());
        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("empty=[x] a=1", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader(""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/echo")
                        .process(e -> e.getMessage().setBody(
                                "text=[" + e.getIn().getHeader(TEXT) + "] a=" + e.getIn().getHeader("a")));
                from("netty-http:http://localhost:{{port}}/empty")
                        .process(e -> e.getMessage().setBody(
                                "empty=[" + e.getIn().getHeader("") + "] a=" + e.getIn().getHeader("a")));
            }
        };
    }
}
