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
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class JettySwitchingStatusCode204Test extends BaseJettyTest {

    @Test
    public void testSwitchNoBodyTo204ViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/bar");
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpResponse httpResponse = httpClient.execute(request);

        assertEquals(204, httpResponse.getStatusLine().getStatusCode());
        assertNull(httpResponse.getEntity());
    }

    @Test
    public void testSwitchingNoBodyTo204HttpViaCamel() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(null, outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testSwitchingNoBodyTo204ViaCamelRoute() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:bar", inExchange);

        assertEquals(204, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(null, outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foo");
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpResponse httpResponse = httpClient.execute(request);

        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        assertNotNull(httpResponse.getEntity());
        assertEquals("No Content", EntityUtils.toString(httpResponse.getEntity()));
    }

    @Test
    public void testNoSwitchingNoCodeHttpViaCamel() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoCodeViaCamelRoute() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foo", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("No Content", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaHttp() throws Exception {
        HttpUriRequest request = new HttpGet("http://localhost:" + getPort() + "/foobar");
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpResponse httpResponse = httpClient.execute(request);

        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        assertNotNull(httpResponse.getEntity());
        assertEquals("", EntityUtils.toString(httpResponse.getEntity()));
    }

    @Test
    public void testNoSwitchingNoBodyHttpViaCamel() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("http://localhost:{{port}}/foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));
    }

    @Test
    public void testNoSwitchingNoBodyViaCamelRoute() throws Exception {
        Exchange inExchange = this.createExchangeWithBody("Hello World");
        Exchange outExchange = template.send("direct:foobar", inExchange);

        assertEquals(200, outExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("", outExchange.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/bar").setBody().constant("");

                from("direct:bar").to("http://localhost:{{port}}/bar");

                from("jetty:http://localhost:{{port}}/foo").setBody().constant("No Content");

                from("direct:foo").to("http://localhost:{{port}}/foo");

                from("jetty:http://localhost:{{port}}/foobar").setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200)).setBody().constant("");

                from("direct:foobar").to("http://localhost:{{port}}/foobar");

            }
        };
    }
}
