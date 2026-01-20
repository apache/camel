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

import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NettyHttpSwitchingStatus204Test extends BaseNettyTestSupport {

    @Test
    public void testSwitchNoBodyTo204ViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/bar");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = client.execute(request)) {

            assertEquals(204, httpResponse.getCode());
            assertNull(httpResponse.getEntity());
        }
    }

    @Test
    public void testSwitchingNoBodyTo204NettyHttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("netty-http:http://localhost:{{port}}/bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(204, response.status().code());
        assertEquals("", message.getBody(String.class));
    }

    @Test
    public void testSwitchingNoBodyTo204ViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(204, response.status().code());
        assertEquals("", message.getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foo");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = client.execute(request)) {

            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("No Content", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingNoCodeNettyHttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("netty-http:http://localhost:{{port}}/foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(200, response.status().code());
        assertEquals("No Content", message.getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(200, response.status().code());
        assertEquals("No Content", message.getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foobar");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = client.execute(request)) {

            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingNoBodyNettyHttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("netty-http:http://localhost:{{port}}/foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(200, response.status().code());
        assertEquals("", message.getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));

        NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
        FullHttpResponse response = message.getHttpResponse();

        assertEquals(200, response.status().code());
        assertEquals("", message.getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:{{port}}/bar")
                        .setBody().constant("");

                from("direct:bar")
                        .to("netty-http:http://localhost:{{port}}/bar");

                from("netty-http:http://localhost:{{port}}/foo")
                        .setBody().constant("No Content");

                from("direct:foo")
                        .to("netty-http:http://localhost:{{port}}/foo");

                from("netty-http:http://localhost:{{port}}/foobar")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                        .setBody().constant("");

                from("direct:foobar")
                        .to("netty-http:http://localhost:{{port}}/foobar");

            }
        };
    }
}
