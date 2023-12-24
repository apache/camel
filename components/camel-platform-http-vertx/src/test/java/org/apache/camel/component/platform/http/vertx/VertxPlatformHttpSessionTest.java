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

import java.util.Arrays;

import io.restassured.RestAssured;
import io.vertx.core.Handler;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.http.base.cookie.InstanceCookieHandler;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;

public class VertxPlatformHttpSessionTest {

    @Test
    public void testSessionDisabled() throws Exception {
        CamelContext context = createCamelContext(sessionConfig -> {
            // session handling disabled by default
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/disabled")
                        .setBody().constant("disabled");
            }
        });

        try {
            context.start();

            given()
                    .when()
                    .get("/disabled")
                    .then()
                    .statusCode(200)
                    .header("set-cookie", nullValue())
                    .header("cookie", nullValue())
                    .body(equalTo("disabled"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCookeForDefaultSessionConfig() throws Exception {
        CamelContext context = createCamelContext(sessionConfig -> {
            sessionConfig.setEnabled(true);
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/session")
                        .setBody().constant("session");
            }
        });

        try {
            context.start();

            String sessionCookieValue = given()
                    .when()
                    .get("/session")
                    .then()
                    .statusCode(200)
                    .cookie("vertx-web.session",
                            detailedCookie()
                                    .path("/").value(notNullValue())
                                    .httpOnly(false)
                                    .secured(false)
                                    .sameSite("Strict"))
                    .header("cookie", nullValue())
                    .body(equalTo("session"))
                    .extract().cookie("vertx-web.session");

            assertTrue(sessionCookieValue.length() >= SessionHandler.DEFAULT_SESSIONID_MIN_LENGTH);

        } finally {
            context.stop();
        }
    }

    @Test
    public void testCookieForModifiedSessionConfig() throws Exception {
        CamelContext context = createCamelContext(sessionConfig -> {
            sessionConfig.setSessionCookieName("vertx-session");
            sessionConfig.setEnabled(true);
            sessionConfig.setSessionCookiePath("/session");
            sessionConfig.setCookieSecure(true);
            sessionConfig.setCookieHttpOnly(true);
            sessionConfig.setCookieSameSite(CookieSameSite.LAX);
            sessionConfig.setSessionIdMinLength(64);
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/session")
                        .setBody().constant("session");
            }
        });

        try {
            context.start();

            String sessionCookieValue = given()
                    .when()
                    .get("/session")
                    .then()
                    .statusCode(200)
                    .cookie("vertx-session",
                            detailedCookie()
                                    .path("/session").value(notNullValue())
                                    .httpOnly(true)
                                    .secured(true)
                                    .sameSite("Lax"))
                    .header("cookie", nullValue())
                    .body(equalTo("session"))
                    .extract().cookie("vertx-session");

            assertTrue(sessionCookieValue.length() >= 64);

        } finally {
            context.stop();
        }
    }

    @Test
    public void testSessionHandling() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        CamelContext context = createCamelContext(port,
                sessionConfig -> {
                    sessionConfig.setEnabled(true);
                });
        addPlatformHttpEngineHandler(context, new HitCountHandler());
        context.getRegistry().bind("instanceCookieHander", new InstanceCookieHandler());

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/session")
                            .setBody().constant("session");

                    from("direct:session")
                            .toF("http://localhost:%d/session?cookieHandler=#instanceCookieHander",
                                    port);
                }
            });

            context.start();

            // initial call establishes session
            ProducerTemplate template = context.createProducerTemplate();
            Exchange exchange = template.request("direct:session", null);
            // 'set-cookie' header for new session, e.g. 'vertx-web.session=735944d69685aaf63421fb5b3c116b84; Path=/; SameSite=Strict'
            String sessionCookie = getHeader("set-cookie", exchange);
            assertNotNull(getHeader("set-cookie", exchange));
            assertEquals(getHeader("hitcount", exchange), "1");

            // subsequent call reuses session
            exchange = template.request("direct:session", null);
            // 'cookie' header for existing session, e.g. 'vertx-web.session=735944d69685aaf63421fb5b3c116b84'
            String cookieHeader = getHeader("cookie", exchange);
            assertEquals(cookieHeader, sessionCookie.substring(0, sessionCookie.indexOf(';')));
            assertNull(getHeader("set-cookie", exchange));
            assertEquals(getHeader("hitcount", exchange), "2");

        } finally {
            context.stop();
        }
    }

    private String getHeader(String header, Exchange exchange) {
        return (String) exchange.getMessage().getHeader(header);
    }

    private CamelContext createCamelContext(SessionConfigCustomizer customizer)
            throws Exception {
        int bindPort = AvailablePortFinder.getNextAvailable();
        RestAssured.port = bindPort;
        return createCamelContext(bindPort, customizer);
    }

    private CamelContext createCamelContext(int bindPort, SessionConfigCustomizer customizer)
            throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(bindPort);

        VertxPlatformHttpServerConfiguration.SessionConfig sessionConfig
                = new VertxPlatformHttpServerConfiguration.SessionConfig();
        customizer.customize(sessionConfig);
        conf.setSessionConfig(sessionConfig);

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addService(new VertxPlatformHttpServer(conf));
        return camelContext;
    }

    private void addPlatformHttpEngineHandler(CamelContext camelContext, Handler<RoutingContext> handler) {
        VertxPlatformHttpEngine platformEngine = new VertxPlatformHttpEngine();
        platformEngine.setHandlers(Arrays.asList(handler));
        PlatformHttpComponent component = new PlatformHttpComponent(camelContext);
        component.setEngine(platformEngine);
        camelContext.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME, component);
    }

    private class HitCountHandler implements Handler<RoutingContext> {
        @Override
        public void handle(RoutingContext routingContext) {
            Session session = routingContext.session();
            Integer cnt = session.get("hitcount");
            cnt = (cnt == null ? 0 : cnt) + 1;
            session.put("hitcount", cnt);
            routingContext.response().putHeader("hitcount", Integer.toString(cnt));
            routingContext.next();
        }
    }

    interface SessionConfigCustomizer {
        void customize(VertxPlatformHttpServerConfiguration.SessionConfig sessionConfig);
    }
}
