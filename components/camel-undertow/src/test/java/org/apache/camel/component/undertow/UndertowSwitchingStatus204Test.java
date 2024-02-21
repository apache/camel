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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
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

public class UndertowSwitchingStatus204Test extends BaseUndertowTest {

    @Test
    public void testSwitchNoBodyTo204ViaHttpEmptyBody() throws Exception {
        HttpGet request = new HttpGet("http://localhost:" + getPort() + "/foo");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {

            assertEquals(204, httpResponse.getCode());
            assertNull(httpResponse.getEntity());
        }
    }

    @Test
    public void testSwitchingNoBodyTo204NettyHttpViaCamelEmptyBody() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("undertow:http://localhost:{{port}}/foo", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(204, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", msg.getBody(String.class));
    }

    @Test
    public void testSwitchingNoBodyTo204ViaCamelRouteEmptyBody() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foo", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(204, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", msg.getBody(String.class));
    }

    @Test
    public void testNoSwitchingHasBodyViaHttpNoContent() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/bar");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {

            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("No Content", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingHasBodyNettyHttpViaCamelNoContent() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("undertow:http://localhost:{{port}}/bar", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(200, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", msg.getBody(String.class));
    }

    @Test
    public void testNoSwitchingHasBodyViaCamelRouteNoContent() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:bar", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(200, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", msg.getBody(String.class));
    }

    @Test
    public void testNoSwitchingHasCodeViaHttpNoContent() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foobar");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {

            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingHasCodeNettyHttpViaCamelNoContent() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("undertow:http://localhost:{{port}}/foobar", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(200, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", msg.getBody(String.class));
    }

    @Test
    public void testNoSwitchingHasCodeViaCamelRouteNoContent() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foobar", inExchange);

        Message msg = outExchange.getMessage();
        assertEquals(200, msg.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", msg.getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("undertow:http://localhost:{{port}}/foo")
                        .setBody().constant("");

                from("direct:foo")
                        .to("undertow:http://localhost:{{port}}/foo");

                from("undertow:http://localhost:{{port}}/bar")
                        .setBody().constant("No Content");

                from("direct:bar")
                        .to("undertow:http://localhost:{{port}}/bar");

                from("undertow:http://localhost:{{port}}/foobar")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                        .setBody().constant("");

                from("direct:foobar")
                        .to("undertow:http://localhost:{{port}}/foobar");

            }
        };
    }
}
