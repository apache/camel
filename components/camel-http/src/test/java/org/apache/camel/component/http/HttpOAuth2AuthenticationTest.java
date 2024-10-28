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
package org.apache.camel.component.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.handler.OAuth2TokenRequestHandler;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpOAuth2AuthenticationTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String clientId = "test-client";
    private static final String clientSecret = "test-secret";

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Authorization", "Bearer " + FAKE_TOKEN);

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", new OAuth2TokenRequestHandler(FAKE_TOKEN, clientId, clientSecret))
                .register("/post",
                        new HeaderValidationHandler(
                                "POST",
                                null,
                                null,
                                null,
                                expectedHeaders))
                .create();

        localServer.start();
    }

    @Test
    public void authorizationHeaderIsPresent() {

        String tokenEndpoint = "http://localhost:" + localServer.getLocalPort() + "/token";

        Exchange exchange
                = template.request("http://localhost:" + localServer.getLocalPort() + "/post?httpMethod=POST&oauth2ClientId="
                                   + clientId + "&oauth2ClientSecret=" + clientSecret + "&oauth2TokenEndpoint=" + tokenEndpoint,
                        exchange1 -> {
                        });

        assertExchange(exchange);

    }

    @Test
    public void toDauthorizationHeaderIsPresent() throws Exception {
        String tokenEndpoint = "http://localhost:" + localServer.getLocalPort() + "/token";

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setVariable("cid", constant(clientId))
                        .setVariable("cs", constant(clientSecret))
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/post?httpMethod=POST&oauth2ClientId=${variable.cid}"
                             + "&oauth2ClientSecret=${variable:cs}&oauth2TokenEndpoint=" + tokenEndpoint);
            }
        });

        Exchange exchange = template.send("direct:start", e -> {
        });

        assertExchange(exchange);

    }

    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
    }

    protected String getExpectedContent() {
        return "";
    }

}
