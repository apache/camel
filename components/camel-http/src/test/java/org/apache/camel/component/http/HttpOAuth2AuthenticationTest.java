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
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.handler.OAuth2TokenRequestHandler;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpOAuth2AuthenticationTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String clientId = "test-client";
    private static final String clientSecret = "test-secret";

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Authorization", FAKE_TOKEN);

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
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
        super.setUp();
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

    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
    }

    protected String getExpectedContent() {
        return "";
    }

}
