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

public class JettySwitchingStatusCode204Test extends BaseJettyTest {

    @Test
    public void testSwitchNoBodyTo204ViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/bar");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {
            assertEquals(204, httpResponse.getCode());
            assertNull(httpResponse.getEntity());
        }
    }

    @Test
    public void testSwitchingNoBodyTo204HttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testSwitchingNoBodyTo204ViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foo");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {
            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("No Content", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingNoCodeHttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foobar");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(request)) {
            assertEquals(200, httpResponse.getCode());
            assertNotNull(httpResponse.getEntity());
            assertEquals("", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testNoSwitchingNoBodyHttpViaCamel() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaCamelRoute() {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:{{port}}/bar").setBody().constant("");

                from("direct:bar").to("http://localhost:{{port}}/bar");

                from("jetty:http://localhost:{{port}}/foo").setBody().constant("No Content");

                from("direct:foo").to("http://localhost:{{port}}/foo");

                from("jetty:http://localhost:{{port}}/foobar").setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200)).setBody()
                        .constant("");

                from("direct:foobar").to("http://localhost:{{port}}/foobar");

            }
        };
    }
}
