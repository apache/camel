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
package org.apache.camel.component.netty.http.rest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.http.BaseNettyTestSupport;
import org.apache.camel.spi.RestConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestNettyHttpGetCorsTest extends BaseNettyTestSupport {

    @Test
    public void testCors() throws Exception {
        // send OPTIONS first which should not be routed
        getMockEndpoint("mock:input").expectedMessageCount(0);

        Exchange out = template.request("http://localhost:" + getPort() + "/users/123/basic",
                exchange -> exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS"));

        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN,
                out.getMessage().getHeader("Access-Control-Allow-Origin"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS,
                out.getMessage().getHeader("Access-Control-Allow-Methods"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS,
                out.getMessage().getHeader("Access-Control-Allow-Headers"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE, out.getMessage().getHeader("Access-Control-Max-Age"));

        MockEndpoint.assertIsSatisfied(context);

        MockEndpoint.resetMocks(context);
        getMockEndpoint("mock:input").expectedMessageCount(1);

        // send GET request which should be routed

        String out2 = template.requestBody("http://localhost:" + getPort() + "/users/123/basic", null, String.class);
        assertEquals("123;Donald Duck", out2);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use netty-http on localhost with the given port
                restConfiguration().component("netty-http").host("localhost").port(getPort()).enableCORS(true);

                // use the rest DSL to define the rest services
                rest("/users/")
                        .get("{id}/basic").to("direct:basic");

                from("direct:basic")
                        .to("mock:input")
                        .process(exchange -> {
                            String id = exchange.getIn().getHeader("id", String.class);
                            exchange.getMessage().setBody(id + ";Donald Duck");
                        });
            }
        };
    }

}
