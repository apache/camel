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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A path template (such as /orders/{id}) and a static path of the same base path (/orders, exact or with
 * matchOnUriPrefix), served by different routes on one server: the static path answers the base path itself, and
 * stopping the route of the template leaves the static path served (and the server running), whichever of the two
 * routes started first.
 */
class UndertowTemplatePathAndStaticPathTest extends BaseUndertowTest {

    @RegisterExtension
    AvailablePortFinder.Port port3 = AvailablePortFinder.find();

    @Test
    void staticPathAnswersTheBasePathWhenTheTemplateStartedFirst() {
        assertResponse(getPort(), "/orders", 200, "all orders");
        assertResponse(getPort(), "/orders/", 200, "all orders");
        assertResponse(getPort(), "/orders/7", 200, "order 7");
    }

    @Test
    void stopTemplateRouteStartedBeforeTheStaticRoute() throws Exception {
        context.getRouteController().stopRoute("order");

        // the static path is served as before, and the template path is gone
        assertResponse(getPort(), "/orders", 200, "all orders");
        assertResponse(getPort(), "/orders/7", 404, null);

        // and the template path is served again after a restart
        context.getRouteController().startRoute("order");
        assertResponse(getPort(), "/orders/7", 200, "order 7");
        assertResponse(getPort(), "/orders", 200, "all orders");
    }

    @Test
    void stopStaticRouteThenTemplateRoute() throws Exception {
        context.getRouteController().stopRoute("orders");
        assertResponse(getPort(), "/orders/7", 200, "order 7");

        context.getRouteController().stopRoute("order");
        assertResponse(getPort(), "/orders/7", 404, null);
        assertResponse(getPort(), "/orders", 404, null);

        context.getRouteController().startRoute("orders");
        assertResponse(getPort(), "/orders", 200, "all orders");
        assertResponse(getPort(), "/orders/7", 404, null);
    }

    @Test
    void prefixPathAnswersTheBasePathAndWhatNoTemplateMatches() {
        assertResponse(getPort(), "/items", 200, "items");
        assertResponse(getPort(), "/items/", 200, "items");
        assertResponse(getPort(), "/items/a/b", 200, "items");
        assertResponse(getPort(), "/items/7", 200, "item 7");
    }

    @Test
    void staticRouteWithMethodRestrictAnswersTheBasePath() {
        // the template route starts first; the static route of the base path only accepts POST
        assertResponse(getPort(), "/m", 405, null);
        Exchange out = template.request("undertow:http://localhost:" + getPort() + "/m?throwExceptionOnFailure=false",
                e -> {
                    e.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
                    e.getMessage().setBody("x");
                });
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("post m", out.getMessage().getBody(String.class));
        assertResponse(getPort(), "/m/7", 200, "m 7");
    }

    @Test
    void rootPrefixPathAnswersTheRoot() {
        // on the third port, a matchOnUriPrefix route of / and a template /{id}: Undertow serves the prefix path / as
        // its default handler, which keeps the whole path as the rest
        assertResponse(port3.getPort(), "/", 200, "root");
        assertResponse(port3.getPort(), "/7", 200, "root id 7");
        assertResponse(port3.getPort(), "/a/b", 200, "root");
    }

    @Test
    void stopTemplateRouteStartedAfterThePrefixRoute() throws Exception {
        context.getRouteController().stopRoute("item");

        assertResponse(getPort(), "/items/a/b", 200, "items");
        assertResponse(getPort(), "/items/7", 200, "items");
        assertResponse(getPort(), "/items", 200, "items");

        context.getRouteController().startRoute("item");
        assertResponse(getPort(), "/items/7", 200, "item 7");
        assertResponse(getPort(), "/items/a/b", 200, "items");
    }

    @Test
    void stopTemplateRouteKeepsTheServerOfTheStaticRouteRunning() throws Exception {
        // on the second port, the static path and the template are the only paths of the server
        assertResponse(getPort2(), "/shop", 200, "shop");
        assertResponse(getPort2(), "/shop/7", 200, "article 7");

        context.getRouteController().stopRoute("article");

        assertResponse(getPort2(), "/shop", 200, "shop");
        assertResponse(getPort2(), "/shop/7", 404, null);

        // the static route stops too, and starts again
        context.getRouteController().stopRoute("shop");
        context.getRouteController().startRoute("shop");
        assertResponse(getPort2(), "/shop", 200, "shop");
    }

    private void assertResponse(int port, String path, int code, String body) {
        Exchange out = template.request("undertow:http://localhost:" + port + path + "?throwExceptionOnFailure=false", null);
        assertEquals(code, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class), path);
        if (body != null) {
            assertEquals(body, out.getMessage().getBody(String.class), path);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // the template path starts first
                from("undertow:http://localhost:{{port}}/orders/{id}").routeId("order")
                        .setBody(simple("order ${header.id}"));
                from("undertow:http://localhost:{{port}}/orders").routeId("orders")
                        .setBody(constant("all orders"));

                // the prefix path starts first
                from("undertow:http://localhost:{{port}}/items?matchOnUriPrefix=true").routeId("items")
                        .setBody(constant("items"));
                from("undertow:http://localhost:{{port}}/items/{id}").routeId("item")
                        .setBody(simple("item ${header.id}"));

                // the template path starts first, then a static path that only accepts POST
                from("undertow:http://localhost:{{port}}/m/{id}").routeId("mId")
                        .setBody(simple("m ${header.id}"));
                from("undertow:http://localhost:{{port}}/m?httpMethodRestrict=POST").routeId("m")
                        .setBody(constant("post m"));

                // the static path starts first, on a server of its own
                from("undertow:http://localhost:{{port2}}/shop").routeId("shop")
                        .setBody(constant("shop"));
                from("undertow:http://localhost:{{port2}}/shop/{id}").routeId("article")
                        .setBody(simple("article ${header.id}"));

                // the root path
                from("undertow:http://localhost:" + port3.getPort() + "/?matchOnUriPrefix=true").routeId("root")
                        .setBody(constant("root"));
                from("undertow:http://localhost:" + port3.getPort() + "/{id}").routeId("rootId")
                        .setBody(simple("root id ${header.id}"));
            }
        };
    }
}
