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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NettyHttpEndpointOptionsNotInQueryTest extends BaseNettyTestSupport {

    @BindToRegistry("myCookieHandler")
    private final InstanceCookieHandler cookieHandler = new InstanceCookieHandler();

    @Test
    public void testEndpointOptionsAreNotSentAsQueryParameters() {
        String uri = "netty-http:http://localhost:{{port}}/echo?bar=1&cookieHandler=#myCookieHandler"
                     + "&httpMethodRestrict=GET&traceEnabled=false&exchangePattern=InOut&lazyStartProducer=true"
                     + "&throwExceptionOnFailure=true";

        NettyHttpEndpoint endpoint = context.getEndpoint(uri, NettyHttpEndpoint.class);
        // the options are bound to the endpoint
        assertSame(cookieHandler, endpoint.getCookieHandler());
        assertEquals("GET", endpoint.getHttpMethodRestrict());

        // and only the query parameter that is not an endpoint option is in the address
        assertEquals("http://localhost:" + getPort() + "/echo?bar=1",
                NettyHttpHelper.createURL(new DefaultExchange(context), endpoint));

        String query = template.requestBody(uri, null, String.class);
        assertEquals("bar=1", query);
    }

    @Test
    public void testOnlyEndpointOptions() {
        String query = template.requestBody(
                "netty-http:http://localhost:{{port}}/echo?cookieHandler=#myCookieHandler&httpMethodRestrict=GET", null,
                String.class);
        // no query parameters at all
        assertEquals("none", query);
    }

    @Test
    public void testConsumersDifferingOnlyInHttpMethodRestrict() {
        List<NettyHttpEndpoint> endpoints = context.getEndpoints().stream()
                .filter(NettyHttpEndpoint.class::isInstance)
                .map(NettyHttpEndpoint.class::cast)
                .filter(e -> e.getConfiguration().getPath().equals("/orders"))
                .toList();
        // the rest consumers for GET and POST on the same path differ only in httpMethodRestrict
        assertEquals(2, endpoints.size());
        assertNotEquals(endpoints.get(0), endpoints.get(1));
        assertEquals(Set.of("GET", "POST"),
                endpoints.stream().map(NettyHttpEndpoint::getHttpMethodRestrict).collect(Collectors.toSet()));

        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/orders", null,
                Exchange.HTTP_METHOD, "GET", String.class);
        assertEquals("get", out);
        out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/orders", "Hello",
                Exchange.HTTP_METHOD, "POST", String.class);
        assertEquals("post", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:{{port}}/echo")
                        .process(e -> {
                            String query = e.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
                            e.getMessage().setBody(query != null ? query : "none");
                        });

                restConfiguration().component("netty-http").host("localhost").port(getPort());

                rest("/orders")
                        .get().to("direct:get")
                        .post().to("direct:post");

                from("direct:get").transform().constant("get");
                from("direct:post").transform().constant("post");
            }
        };
    }
}
