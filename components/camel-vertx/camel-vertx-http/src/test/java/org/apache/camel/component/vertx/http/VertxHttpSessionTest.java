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
package org.apache.camel.component.vertx.http;

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.ext.web.client.spi.CookieStore;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpSessionTest extends VertxHttpTestSupport {

    private static final String USERNAME = "apache";
    private static final String PASSWORD = "camel";
    private static final String SECRET_CONTENT = "Some secret content";
    private static final String SESSION_ID = "abc123";

    @BindToRegistry("cookieStore")
    private CookieStore cookieStore = CookieStore.build();

    @BindToRegistry("customCookieStore")
    private final CookieStore customCookieStore = new CustomCookieStore();

    @Test
    public void testSessionSupport() {
        Exchange result = template.request(getProducerUri() + "/secure?sessionManagement=true&cookieStore=#cookieStore", null);

        HttpOperationFailedException exception = result.getException(HttpOperationFailedException.class);
        assertEquals(403, exception.getStatusCode());

        result = template.request(getProducerUri() + "/login?sessionManagement=true&cookieStore=#cookieStore", exchange -> {
            exchange.getMessage().setHeader("username", USERNAME);
            exchange.getMessage().setHeader("password", PASSWORD);
        });
        assertEquals("sessionId=" + SESSION_ID + ";", result.getMessage().getHeader("Set-Cookie"));

        String content = template.requestBody(getProducerUri() + "/secure?sessionManagement=true&cookieStore=#cookieStore",
                null, String.class);
        assertEquals(SECRET_CONTENT, content);
    }

    @Test
    public void testCustomCookieStore() {
        template.request(getProducerUri() + "/login?sessionManagement=true&cookieStore=#customCookieStore", exchange -> {
            exchange.getMessage().setHeader("username", USERNAME);
            exchange.getMessage().setHeader("password", PASSWORD);
        });

        Cookie cookie = customCookieStore.get(false, null, null).iterator().next();
        assertEquals("sessionId", cookie.name());
        assertEquals(SESSION_ID, cookie.value());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Simulate session handling for an application with a 'secured' endpoint
                from(getTestServerUri() + "/secure")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Message message = exchange.getMessage();
                                String cookie = message.getHeader("Cookie", String.class);
                                if (cookie != null && cookie.equals("sessionId=" + SESSION_ID)) {
                                    message.setBody("Some secret content");
                                } else {
                                    message.setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                                }
                            }
                        });

                from(getTestServerUri() + "/login")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Message message = exchange.getMessage();
                                String username = message.getHeader("username", String.class);
                                String password = message.getHeader("password", String.class);
                                if (username.equals(USERNAME) && password.equals(PASSWORD)) {
                                    message.setHeader("Set-Cookie", "sessionId=" + SESSION_ID + ";");
                                }
                            }
                        });

            }
        };
    }

    private static final class CustomCookieStore implements CookieStore {
        private final List<Cookie> cookies = new ArrayList<>();

        @Override
        public Iterable<Cookie> get(Boolean ssl, String domain, String path) {
            return cookies;
        }

        @Override
        public CookieStore put(Cookie cookie) {
            cookies.add(cookie);
            return this;
        }

        @Override
        public CookieStore remove(Cookie cookie) {
            cookies.remove(cookie);
            return this;
        }
    }
}
