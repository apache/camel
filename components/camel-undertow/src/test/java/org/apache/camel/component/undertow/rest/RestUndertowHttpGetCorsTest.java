/**
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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.spi.RestConfiguration;
import org.junit.Test;

public class RestUndertowHttpGetCorsTest extends BaseUndertowTest {

    @Test
    public void testCorsGet() throws Exception {
        // send OPTIONS first which should not be routed
        getMockEndpoint("mock:inputGet").expectedMessageCount(0);

        Exchange out = template.request("http://localhost:" + getPort() + "/users/123/basic", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN, out.getOut().getHeader("Access-Control-Allow-Origin"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS, out.getOut().getHeader("Access-Control-Allow-Methods"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS, out.getOut().getHeader("Access-Control-Allow-Headers"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE, out.getOut().getHeader("Access-Control-Max-Age"));

        assertMockEndpointsSatisfied();

        resetMocks();
        getMockEndpoint("mock:inputGet").expectedMessageCount(1);

        // send GET request which should be routed

        String out2 = fluentTemplate.to("http://localhost:" + getPort() + "/users/123/basic")
                .withHeader(Exchange.HTTP_METHOD, "GET")
                .request(String.class);
        assertEquals("123;Donald Duck", out2);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCorsPut() throws Exception {
        // send OPTIONS first which should not be routed
        getMockEndpoint("mock:inputPut").expectedMessageCount(0);

        Exchange out = template.request("http://localhost:" + getPort() + "/users/123/basic", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN, out.getOut().getHeader("Access-Control-Allow-Origin"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS, out.getOut().getHeader("Access-Control-Allow-Methods"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS, out.getOut().getHeader("Access-Control-Allow-Headers"));
        assertEquals(RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE, out.getOut().getHeader("Access-Control-Max-Age"));

        assertMockEndpointsSatisfied();

        resetMocks();
        getMockEndpoint("mock:inputPut").expectedMessageCount(1);

        // send PUT request which should be routed

        String out2 = fluentTemplate.to("http://localhost:" + getPort() + "/users/123/basic")
                .withHeader(Exchange.HTTP_METHOD, "PUT")
                .request(String.class);
        assertEquals("123;Donald Duck", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use undertow on localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort()).enableCORS(true);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("{id}/basic")
                        .route()
                        .to("mock:inputGet")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);
                                exchange.getOut().setBody(id + ";Donald Duck");
                            }
                        }).endRest()
                    .put("{id}/basic")
                        .route()
                        .to("mock:inputPut")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);
                                exchange.getOut().setBody(id + ";Donald Duck");
                            }
                        });
            }
        };
    }

}
