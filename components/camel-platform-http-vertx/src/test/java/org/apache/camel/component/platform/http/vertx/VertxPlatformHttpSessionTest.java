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

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.codec.http.cookie.Cookie;
import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.spi.CookieStore;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxPlatformHttpSessionTest extends CamelTestSupport {

    @Test
    public void testSessionCreation() throws Exception {
        Vertx vertx = Vertx.vertx();
        CamelContext context = createCamelContext(configuration -> {
            VertxPlatformHttpServerConfiguration.SessionConfig sessionConfig
                    = new VertxPlatformHttpServerConfiguration.SessionConfig();
            sessionConfig.setEnabled(true);
            configuration.setSessionConfig(sessionConfig);
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/session")
                        .routeId("session")
                        .setBody().constant("session");
            }
        });

        context.getRegistry().bind("vertx", vertx);

        try {
            context.start();

            // returns set-cookie header for created session
            String sessionCookieValue = given()
                    .when()
                    .get("/session")
                    .then()
                    .statusCode(200)
                    .header("set-cookie", startsWith("vertx-web.session=")) // new vertx-web session created
                    // details of the set session cookie
                    .cookie("vertx-web.session", detailedCookie().path("/").value(notNullValue()))
                    .header("cookie", nullValue())
                    .body(equalTo("session"))
                    .extract().cookie("vertx-web.session");

            // pass session cookie back on subsequent call
            given()
                    .header("cookie", "vertx-web.session=" + sessionCookieValue)
                    .when()
                    .get("/session")
                    .then()
                    .statusCode(200)
                    .header("set-cookie", nullValue())  // session already established.
                    .header("cookie", "vertx-web.session=" + sessionCookieValue)
                    .body(equalTo("session"));

        } finally {
            context.stop();
            vertx.close();
        }
    }

    @Test
    public void testNewSession() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        final CamelContext context = createCamelContext(configuration -> {
            VertxPlatformHttpServerConfiguration.SessionConfig sessionConfig
                    = new VertxPlatformHttpServerConfiguration.SessionConfig();
            sessionConfig.setEnabled(true);

            configuration.setSessionConfig(sessionConfig);
        }, port);

        CookieStore customCookieStore = new CustomCookieStore();
        context.getRegistry().bind("customCookieStore", customCookieStore);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/session")
                            .routeId("session")
                            .setBody().constant("session");

                    from("direct:session")
                            .toF("vertx-http:http://localhost:%d/session?sessionManagement=true&cookieStore=#customCookieStore",
                                    port);
                }
            });

            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            Exchange exchange = template.request("direct:session", ex -> {
            });

            // session created by session handler
            String sessionCookie = (String) exchange.getMessage().getHeader("set-cookie");
            assertNotNull(sessionCookie);
            assertTrue(sessionCookie.startsWith("vertx-web.session="));

            // returned session saved in vertx-http cookie store
            Cookie savedCookie = customCookieStore.get(false, null, null).iterator().next();
            assertNotNull(savedCookie);
            assertEquals("vertx-web.session", savedCookie.name());
            assertTrue(sessionCookie.startsWith(savedCookie.name() + '=' + savedCookie.value()));

            // call again, headers added from cookie store
            exchange = template.request("direct:session", ex -> {
            });
            // cookie for established session
            Object cookieHeader = exchange.getMessage().getHeader("cookie");
            assertNotNull(cookieHeader);
            assert ((savedCookie.name() + '=' + savedCookie.value()).equals(cookieHeader));
            // session already established, no new cookie set
            sessionCookie = (String) exchange.getMessage().getHeader("set-cookie");
            assertNull(sessionCookie);
        } finally {
            context.stop();
        }
    }

    static CamelContext createCamelContext(ServerConfigurationCustomizer customizer)
            throws Exception {
        return createCamelContext(customizer, AvailablePortFinder.getNextAvailable());
    }

    static CamelContext createCamelContext(ServerConfigurationCustomizer customizer, int bindPort)
            throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(bindPort);

        RestAssured.port = bindPort;

        if (customizer != null) {
            customizer.customize(conf);
        }

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

    interface ServerConfigurationCustomizer {
        void customize(VertxPlatformHttpServerConfiguration configuration);
    }

    // Cookie store for the producer
    private static final class CustomCookieStore implements CookieStore {
        private final List<io.netty.handler.codec.http.cookie.Cookie> cookies = new ArrayList<>();

        @Override
        public Iterable<io.netty.handler.codec.http.cookie.Cookie> get(Boolean ssl, String domain, String path) {
            return cookies;
        }

        @Override
        public CookieStore put(io.netty.handler.codec.http.cookie.Cookie cookie) {
            cookies.add(cookie);
            return this;
        }

        @Override
        public CookieStore remove(io.netty.handler.codec.http.cookie.Cookie cookie) {
            cookies.remove(cookie);
            return this;
        }
    }
}
