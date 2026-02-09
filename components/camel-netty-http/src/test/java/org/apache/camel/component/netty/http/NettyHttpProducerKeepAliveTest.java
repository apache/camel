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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpProducerKeepAliveTest extends BaseNettyTestSupport {

    @Test
    public void testHttpKeepAlive() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World", "Hello Again");

        String out
                = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=true", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=true", "Hello Again", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpKeepAliveFalse() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World", "Hello Again");

        String out
                = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=false", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=false", "Hello Again", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConnectionClosed() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        Exchange ex = template.request("netty-http:http://localhost:{{port}}/bar?keepAlive=false",
                exchange -> exchange.getIn().setBody("Hello World"));

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(HttpHeaderValues.CLOSE.toString(), ex.getMessage().getHeader(HttpHeaderNames.CONNECTION.toString()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:{{port}}/foo")
                        .to("mock:input")
                        .transform().constant("Bye World");

                from("netty-http:http://localhost:{{port}}/bar").removeHeaders("*").to("mock:input").transform()
                        .constant("Bye World");
            }
        };
    }

}
