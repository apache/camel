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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.util.json.Jsoner;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.WWWFormCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The token request of the OAuth2 client credentials flow with a resource indicator (RFC 8707) has the resource
 * parameter besides the other parameters.
 */
public class HttpOAuth2ResourceIndicatorTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";
    private static final String SCOPE = "test-scope";
    private static final String RESOURCE = "https://api.example.com/";

    // the parameters of the token requests the token endpoint received
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();

    @Override
    public void setupResources() throws Exception {
    }

    @Test
    public void resourceIsAddedToTheTokenRequest() throws Exception {
        Map<String, String> tokenRequest = requestWithToken(false);

        assertEquals("client_credentials", tokenRequest.get("grant_type"));
        assertEquals(SCOPE, tokenRequest.get("scope"));
        assertEquals(RESOURCE, tokenRequest.get("resource"));
    }

    @Test
    public void resourceIsAddedToTheTokenRequestWithBodyAuthentication() throws Exception {
        Map<String, String> tokenRequest = requestWithToken(true);

        assertEquals("client_credentials", tokenRequest.get("grant_type"));
        assertEquals(SCOPE, tokenRequest.get("scope"));
        assertEquals(CLIENT_ID, tokenRequest.get("client_id"));
        assertEquals(CLIENT_SECRET, tokenRequest.get("client_secret"));
        assertEquals(RESOURCE, tokenRequest.get("resource"));
    }

    // Sends a request that needs a token, and returns the parameters of the token request
    private Map<String, String> requestWithToken(boolean bodyAuthentication) throws Exception {
        tokenRequests.clear();
        try (HttpServer localServer = createLocalServer()) {
            String tokenEndpoint = "http://localhost:" + localServer.getLocalPort() + "/token";
            String requestUrl = "http://localhost:" + localServer.getLocalPort() + "/post?httpMethod=POST&oauth2ClientId="
                                + CLIENT_ID + "&oauth2ClientSecret=" + CLIENT_SECRET + "&oauth2TokenEndpoint=" + tokenEndpoint
                                + "&oauth2Scope=" + SCOPE + "&oauth2ResourceIndicator=" + RESOURCE
                                + "&oauth2BodyAuthentication=" + bodyAuthentication;

            Exchange exchange = template.request(requestUrl, e -> {
            });

            assertExchange(exchange);
        }
        assertEquals(1, tokenRequests.size());
        return tokenRequests.get(0);
    }

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected String getExpectedContent() {
        return "";
    }

    private HttpServer createLocalServer() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Authorization", "Bearer " + FAKE_TOKEN);

        HttpServer localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", (request, response, context) -> {
                    Map<String, String> parameters = new HashMap<>();
                    WWWFormCodec.parse(EntityUtils.toString(request.getEntity()), StandardCharsets.UTF_8)
                            .forEach(pair -> parameters.put(pair.getName(), pair.getValue()));
                    tokenRequests.add(parameters);
                    response.setEntity(new StringEntity(
                            Jsoner.serialize(Map.of("access_token", FAKE_TOKEN)), ContentType.APPLICATION_JSON));
                })
                .register("/post", new HeaderValidationHandler("POST", null, null, null, expectedHeaders))
                .create();

        localServer.start();
        return localServer;
    }
}
