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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REST DSL consumers and plain undertow consumers on the same port share one server, and both kinds are served.
 */
class RestUndertowAndPlainConsumerSamePortTest extends BaseUndertowTest {

    @Test
    void testBothKindsAreServed() {
        assertResponse("/users/123", 200, "user 123");
        assertResponse("/plain", 200, "plain");
        assertResponse("/unknown", 404, null);
    }

    @Test
    void testPlainConsumerAddedAfterRestConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/late").setBody(constant("late"));
            }
        });

        assertResponse("/late", 200, "late");
        assertResponse("/users/123", 200, "user 123");
    }

    @Test
    void testServerKeepsServingWhenOneKindStops() throws Exception {
        context.getRouteController().stopRoute("rest-users");
        assertResponse("/plain", 200, "plain");

        context.getRouteController().startRoute("rest-users");
        context.getRouteController().stopRoute("plain");
        assertResponse("/users/123", 200, "user 123");
    }

    @Test
    void testPlainConsumerOnRootTakesThePathsUntilItStops() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/?matchOnUriPrefix=true").routeId("root")
                        .setBody(constant("root"));
            }
        });
        // the plain endpoints come first
        assertResponse("/users/123", 200, "root");

        context.getRouteController().stopRoute("root");
        assertResponse("/users/123", 200, "user 123");
        assertResponse("/unknown", 404, null);
    }

    @Test
    void testRestPathUnderTheBaseOfAPlainTemplatePath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/api/{name}").routeId("template")
                        .setBody(simple("plain ${header.name}"));
            }
        });

        assertResponse("/api/x", 200, "plain x");
        // the template /api/{name} does not match: the request goes on to the rest-dsl consumers
        assertResponse("/api/orders/1", 200, "order 1");
        assertResponse("/api/x/unknown", 404, null);
    }

    @Test
    void testPlainTemplatePathOnRootKeepsTheRestConsumers() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/{file}").routeId("root-template")
                        .setBody(simple("root ${header.file}"));
            }
        });

        assertResponse("/index", 200, "root index");
        assertResponse("/users/123", 200, "user 123");
        assertResponse("/unknown/path", 404, null);
    }

    private void assertResponse(String path, int expectedCode, String expectedBody) {
        Exchange out = template.request("undertow:http://localhost:" + getPort() + path + "?throwExceptionOnFailure=false",
                exchange -> exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET"));
        assertEquals(expectedCode, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class), path);
        if (expectedBody != null) {
            assertEquals(expectedBody, out.getMessage().getBody(String.class), path);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("undertow").host("localhost").port(getPort());

                rest("/users").get("{id}").id("rest-users").to("direct:user");
                rest("/api").get("/orders/{id}").to("direct:order");

                from("direct:user").setBody(simple("user ${header.id}"));
                from("direct:order").setBody(simple("order ${header.id}"));

                from("undertow:http://localhost:{{port}}/plain").routeId("plain").setBody(constant("plain"));
            }
        };
    }
}
