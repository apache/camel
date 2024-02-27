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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.cookie.CookieConfiguration;
import org.apache.camel.component.platform.http.cookie.CookieHandler;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class VertxPlatformHttpCookieTest {

    // add a cookie using the default cookie configuration
    @Test
    public void testAddCookie() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/add?useCookieHandler=true")
                            .process(exchange -> {
                                getCookieHandler(exchange).addCookie("foo", "bar");
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
                                    .path(CookieConfiguration.DEFAULT_PATH)
                                    .domain((String) null)
                                    .sameSite(CookieConfiguration.DEFAULT_SAME_SITE.getValue()))
                    .body(equalTo("add"));
        } finally {
            context.stop();
        }
    }

    // add a cookie with specified CookieConfiguration properties
    @Test
    public void testAddCookieCustomConfiguration() throws Exception {
        CamelContext context = createCamelContext();
        long cookieMaxAge = 60L * 60 * 24 * 7; // 1 week
        context.getRegistry().bind("cookieConfiguration",
                new CookieConfiguration.Builder()
                        .setPath("/testpath")
                        .setDomain("apache.org")
                        .setHttpOnly(true)
                        .setSecure(true)
                        .setMaxAge(cookieMaxAge)
                        .setSameSite(CookieConfiguration.CookieSameSite.STRICT)
                        .build());

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/add/custom?useCookieHandler=true&cookieConfiguration=#cookieConfiguration")
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
                                    .sameSite("Strict"))
                    .body(equalTo("add-custom"));
        } finally {
            context.stop();
        }
    }

    // get a cookie value from the cookieJar
    @Test
    public void testGetCookieValue() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/get?useCookieHandler=true")
                            .process(exchange -> {
                                // write cookie name/value as a header so we can verify it was read
                                String cookieName = "foo";
                                String cookieVal = getCookieHandler(exchange).getCookieValue(cookieName);
                                exchange.getMessage().setHeader(
                                        "cookie_name_val", String.format("%s=%s", cookieName, cookieVal));
                            })
                            .setBody().constant("get");
                }
            });
            context.start();

            given()
                    .header("cookie", "foo=bar")
                    .when()
                    .get("/get")
                    .then()
                    .statusCode(200)
                    .header("cookie_name_val", "foo=bar") // verify cookie read
                    .body(equalTo("get"));
        } finally {
            context.stop();
        }
    }

    // expire a cookie in the user-agent/producers cookieJar
    @Test
    public void testRemoveCookie() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/remove?useCookieHandler=true")
                            .process(exchange -> {
                                getCookieHandler(exchange).removeCookie("foo");
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
                    .body(equalTo("remove"));
        } finally {
            context.stop();
        }
    }

    // attempt to expire a cookie that is not held in the cookieJar
    @Test
    public void testRemoveNoCookie() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/remove?useCookieHandler=true")
                            .process(exchange -> {
                                getCookieHandler(exchange).removeCookie("foo");
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

    // replace a cookie held in the cookieJar
    @Test
    public void testReplaceCookie() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/replace?useCookieHandler=true")
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
                                    .path(CookieConfiguration.DEFAULT_PATH)
                                    .sameSite(CookieConfiguration.DEFAULT_SAME_SITE.getValue()))
                    .body(equalTo("replace"));
        } finally {
            context.stop();
        }
    }

    private CookieHandler getCookieHandler(Exchange exchange) {
        return exchange.getProperty(Exchange.COOKIE_HANDLER, CookieHandler.class);
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
