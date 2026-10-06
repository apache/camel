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

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.handler.OAuth2TokenRequestHandler;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The options that decide which requests share a cached OAuth2 token (CAMEL-22080). The default ({@code FULL_URI}) and
 * {@code HOST_ONLY} are also tested in {@link HttpOAuth2TokenCachingTest}.
 * <p>
 * Same approach as {@link HttpOAuth2TokenCachingTest}: the first request caches a token, then the token endpoint is
 * closed. A second request that reuses the cached token succeeds; one that has to request a new token fails to connect.
 */
public class HttpOAuth2CachedTokensKeyTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    @BindToRegistry("onePerTarget")
    private final OAuth2CachedTokensKeyResolver onePerTarget = uri -> "target";

    @BindToRegistry("neverCache")
    private final OAuth2CachedTokensKeyResolver neverCache = uri -> null;

    @Override
    public void setupResources() throws Exception {
    }

    @Test
    public void aDifferentQueryReusesTheTokenWithHostAndPath() throws Exception {
        assertSecondRequestReusesToken("/post?eventId=1", "/post?eventId=2", "&oauth2CachedTokensKey=HOST_AND_PATH");
    }

    @Test
    public void aDifferentPathRequestsANewTokenWithHostAndPath() throws Exception {
        assertSecondRequestRequestsNewToken("/post", "/other", "&oauth2CachedTokensKey=HOST_AND_PATH");
    }

    @Test
    public void aDifferentQueryRequestsANewTokenWithFullUri() throws Exception {
        assertSecondRequestRequestsNewToken("/post?eventId=1", "/post?eventId=2", "&oauth2CachedTokensKey=FULL_URI");
    }

    @Test
    public void theSameUriReusesTheTokenWithFullUri() throws Exception {
        assertSecondRequestReusesToken("/post?eventId=1", "/post?eventId=1", "&oauth2CachedTokensKey=FULL_URI");
    }

    @Test
    public void aCustomResolverDecidesTheKey() throws Exception {
        // the resolver takes precedence over oauth2CachedTokensKey
        assertSecondRequestReusesToken("/post", "/other",
                "&oauth2CachedTokensKey=FULL_URI&oauth2CachedTokensKeyResolver=#onePerTarget");
    }

    @Test
    public void aCustomResolverReturningNullDoesNotCache() throws Exception {
        assertSecondRequestRequestsNewToken("/post", "/post", "&oauth2CachedTokensKeyResolver=#neverCache");
    }

    @Test
    public void theEndpointHasTheOptions() throws Exception {
        try (HttpServer localServer = createLocalServer()) {
            HttpEndpoint endpoint = context.getEndpoint("http://localhost:" + localServer.getLocalPort()
                                                        + "/post?oauth2CachedTokensKey=HOST_AND_PATH",
                    HttpEndpoint.class);
            assertEquals(OAuth2CachedTokensKey.HOST_AND_PATH, endpoint.getOauth2CachedTokensKey());
            assertNull(endpoint.getOauth2CachedTokensKeyResolver());

            HttpEndpoint other = context.getEndpoint("http://localhost:" + localServer.getLocalPort() + "/post",
                    HttpEndpoint.class);
            assertEquals(OAuth2CachedTokensKey.FULL_URI, other.getOauth2CachedTokensKey());

            HttpEndpoint custom = context.getEndpoint("http://localhost:" + localServer.getLocalPort()
                                                      + "/post?oauth2CachedTokensKeyResolver=#onePerTarget",
                    HttpEndpoint.class);
            assertSame(onePerTarget, custom.getOauth2CachedTokensKeyResolver());
        }
    }

    private void assertSecondRequestReusesToken(String first, String second, String options) throws Exception {
        try (HttpServer localServer = createLocalServer(); HttpServer oauth2Server = createLocalOAuth2Server()) {
            assertOk(template.request(uri(localServer, oauth2Server, first, options), e -> {
            }));
            oauth2Server.close();
            assertOk(template.request(uri(localServer, oauth2Server, second, options), e -> {
            }));
        }
    }

    private void assertSecondRequestRequestsNewToken(String first, String second, String options) throws Exception {
        try (HttpServer localServer = createLocalServer(); HttpServer oauth2Server = createLocalOAuth2Server()) {
            assertOk(template.request(uri(localServer, oauth2Server, first, options), e -> {
            }));
            oauth2Server.close();
            Exchange exchange = template.request(uri(localServer, oauth2Server, second, options), e -> {
            });
            assertInstanceOf(HttpHostConnectException.class, exchange.getException());
        }
    }

    private static String uri(HttpServer localServer, HttpServer oauth2Server, String pathAndQuery, String options) {
        String tokenEndpoint = "http://localhost:" + oauth2Server.getLocalPort() + "/token";
        String separator = pathAndQuery.contains("?") ? "&" : "?";
        return "http://localhost:" + localServer.getLocalPort() + pathAndQuery + separator
               + "httpMethod=POST&oauth2ClientId=" + CLIENT_ID + "&oauth2ClientSecret=" + CLIENT_SECRET
               + "&oauth2TokenEndpoint=" + tokenEndpoint + "&oauth2CacheTokens=true" + options;
    }

    private static void assertOk(Exchange exchange) {
        assertNotNull(exchange);
        assertNull(exchange.getException());
        assertEquals(HttpStatus.SC_OK, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    private HttpServer createLocalServer() throws Exception {
        Map<String, String> expectedHeaders = Map.of("Authorization", "Bearer " + FAKE_TOKEN);
        HttpServer localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/post", new HeaderValidationHandler("POST", null, null, null, expectedHeaders))
                .register("/other", new HeaderValidationHandler("POST", null, null, null, expectedHeaders))
                .create();
        localServer.start();
        return localServer;
    }

    private HttpServer createLocalOAuth2Server() throws Exception {
        HttpServer oauth2Server = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", new OAuth2TokenRequestHandler(FAKE_TOKEN, CLIENT_ID, CLIENT_SECRET))
                .create();
        oauth2Server.start();
        return oauth2Server;
    }
}
