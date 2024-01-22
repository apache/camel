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
package org.apache.camel.component.platform.http.vertx;

import io.restassured.RestAssured;
import io.vertx.core.http.Cookie;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.cookie.CookieSameSite;
import org.apache.camel.component.platform.http.cookie.PlatformHttpCookieConfiguration;
import org.apache.camel.component.platform.http.cookie.PlatformHttpCookieHandler;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class VertxPlatformHttpCookieTest {

    @Test
    public void testAddCookie() throws Exception {
        CamelContext context = createCamelContext();
        context.getRegistry().bind("cookieHandlerCreator", new VertxCookieHandlerCreator());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/add?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                getCookieHandler(exchange)
                                        .addCookie("foo", "bar");
                            })
                            .setBody().constant("add");
                }
            });
            context.start();

            given()
                    .when()
                    .get("/add")
                    .then()
                    .statusCode(200)
                    .cookie("foo",
                            detailedCookie()
                                    .value("bar")
                                    .path(PlatformHttpCookieConfiguration.DEFAULT_PATH)
                                    .domain((String) null)
                                    .maxAge(PlatformHttpCookieConfiguration.DEFAULT_MAX_AGE)
                                    .sameSite(PlatformHttpCookieConfiguration.DEFAULT_SAME_SITE.getValue()))
                    .body(equalTo("add"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testAddCookieCustomHandler() throws Exception {
        CamelContext context = createCamelContext();
        long cookieMaxAge = 60L * 60 * 24 * 7; // 1 week
        context.getRegistry().bind("cookieHandlerCreator",
                new VertxCookieHandlerCreator(
                        new PlatformHttpCookieConfiguration.Builder()
                                .setPath("/testpath")
                                .setDomain("apache.org")
                                .setHttpOnly(true)
                                .setSecure(true)
                                .setMaxAge(cookieMaxAge)
                                .setSameSite(CookieSameSite.LAX)
                                .build()));

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/add/custom?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                getCookieHandler(exchange).addCookie("foo", "bar");
                            })
                            .setBody().constant("add-custom");
                }
            });
            context.start();

            given()
                    .when()
                    .get("/add/custom")
                    .then()
                    .statusCode(200)
                    .cookie("foo",
                            detailedCookie()
                                    .value("bar")
                                    .path("/testpath")
                                    .domain("apache.org")
                                    .secured(true)
                                    .httpOnly(true)
                                    .maxAge(cookieMaxAge)
                                    .sameSite("Lax"))
                    .body(equalTo("add-custom"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testGetCookie() throws Exception {
        CamelContext context = createCamelContext();
        context.getRegistry().bind("cookieHandlerCreator", new VertxCookieHandlerCreator());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/get?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                cookieToHeader(exchange,
                                        getCookieHandler(exchange).getCookie("foo"));
                            })
                            .setBody().constant("get");
                }
            });
            context.start();

            given()
                    .header("Cookie", "foo=bar")
                    .when()
                    .get("/get")
                    .then()
                    .statusCode(200)
                    .header("foo-cookie", "foo=bar")
                    .body(equalTo("get"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRemoveCookie() throws Exception {
        CamelContext context = createCamelContext();
        context.getRegistry().bind("cookieHandlerCreator", new VertxCookieHandlerCreator());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/remove?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                cookieToHeader(exchange,
                                        getCookieHandler(exchange).removeCookie("foo"));
                            })
                            .setBody().constant("remove");
                }
            });
            context.start();

            given()
                    .header("cookie", "foo=bar")
                    .when()
                    .get("/remove")
                    .then()
                    .statusCode(200)
                    .cookie("foo",
                            detailedCookie()
                                    .maxAge(0)
                                    .expiryDate(notNullValue()))
                    .header("foo-cookie", "foo")
                    .body(equalTo("remove"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRemoveNoCookie() throws Exception {
        CamelContext context = createCamelContext();
        context.getRegistry().bind("cookieHandlerCreator", new VertxCookieHandlerCreator());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/remove?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                cookieToHeader(exchange,
                                        getCookieHandler(exchange).removeCookie("foo"));
                            })
                            .setBody().constant("remove");
                }
            });
            context.start();

            given()
                    .when()
                    .get("/remove")
                    .then()
                    .statusCode(200)
                    .header("foo-cookie", (String) null)
                    .body(equalTo("remove"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testReplaceCookie() throws Exception {
        CamelContext context = createCamelContext();
        context.getRegistry().bind("cookieHandlerCreator", new VertxCookieHandlerCreator());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/replace?cookieHandlerCreator=#cookieHandlerCreator")
                            .process(exchange -> {
                                getCookieHandler(exchange)
                                        .addCookie("XSRF-TOKEN", "88533580000c314");
                            })
                            .setBody().constant("replace");
                }
            });
            context.start();

            given()
                    .header("XSRF-TOKEN", "c359b44aef83415")
                    .when()
                    .get("/replace")
                    .then()
                    .statusCode(200)
                    .cookie("XSRF-TOKEN",
                            detailedCookie()
                                    .value("88533580000c314")
                                    .path(PlatformHttpCookieConfiguration.DEFAULT_PATH)
                                    .maxAge(PlatformHttpCookieConfiguration.DEFAULT_MAX_AGE)
                                    .sameSite(PlatformHttpCookieConfiguration.DEFAULT_SAME_SITE.getValue()))
                    .body(equalTo("replace"));
        } finally {
            context.stop();
        }
    }

    private PlatformHttpCookieHandler<Cookie> getCookieHandler(Exchange exchange) {
        return PlatformHttpCookieHandler.getCookieHandler(exchange);
    }

    private void cookieToHeader(Exchange exchange, Cookie cookie) {
        if (cookie != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName());
            if (StringUtils.isNotBlank(cookie.getValue())) {
                sb.append("=").append(cookie.getValue());
            }
            exchange.getMessage().setHeader(cookie.getName() + "-cookie", sb.toString());
        }
    }

    static CamelContext createCamelContext() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);

        RestAssured.port = port;

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }
}
