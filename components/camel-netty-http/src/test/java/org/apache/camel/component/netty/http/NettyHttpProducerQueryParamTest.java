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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpProducerQueryParamTest extends BaseNettyTest {

    private String url = "netty-http:http://localhost:" + getPort() + "/cheese?urlDecodeHeaders=true";

    @Test
    public void testQueryParameters() throws Exception {
        Exchange exchange = template.request(url + "&quote=Camel%20rocks", null);
        assertNotNull(exchange);

        String body = exchange.getMessage().getBody(String.class);
        Map<?, ?> headers = exchange.getMessage().getHeaders();

        assertEquals("Bye World", body);
        assertEquals("Carlsberg", headers.get("beer"));
    }

    @Test
    public void testQueryParametersWithHeader() throws Exception {
        Exchange exchange = template.request(url, exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_QUERY, "quote=Camel rocks"));
        assertNotNull(exchange);

        String body = exchange.getMessage().getBody(String.class);
        Map<?, ?> headers = exchange.getMessage().getHeaders();

        assertEquals("Bye World", body);
        assertEquals("Carlsberg", headers.get("beer"));
    }

    @Test
    public void testQueryParametersWithDynamicPath() throws Exception {
        // remove "/cheese" from the endpoint URL and place it in the Exchange.HTTP_PATH header
        Exchange exchange = template.request(url.replace("/cheese", ""), exchange1 -> {
            exchange1.getIn().setHeader(Exchange.HTTP_PATH, "/cheese");
            exchange1.getIn().setHeader(Exchange.HTTP_QUERY, "quote=Camel rocks");
        });
        assertNotNull(exchange);

        String body = exchange.getMessage().getBody(String.class);
        Map<?, ?> headers = exchange.getMessage().getHeaders();

        assertEquals("Bye World", body);
        assertEquals("Carlsberg", headers.get("beer"));
    }

    @Test
    public void testQueryParametersInUriWithDynamicPath() throws Exception {
        // remove "/cheese" from the endpoint URL and place it in the Exchange.HTTP_PATH header
        Exchange exchange = template.request((url + "&quote=Camel%20rocks").replace("/cheese", ""), exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_PATH, "/cheese"));
        assertNotNull(exchange);

        String body = exchange.getMessage().getBody(String.class);
        Map<?, ?> headers = exchange.getMessage().getHeaders();

        assertEquals("Bye World", body);
        assertEquals("Carlsberg", headers.get("beer"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(url).process(exchange -> {
                    String quote = exchange.getIn().getHeader("quote", String.class);
                    assertEquals("Camel rocks", quote);

                    exchange.getMessage().setBody("Bye World");
                    exchange.getMessage().setHeader("beer", "Carlsberg");
                });
            }
        };
    }
}
