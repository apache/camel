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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class OAuth2ClientConfigurer implements HttpClientConfigurer {

    private static final String BEARER = "Bearer ";

    private final String clientId;
    private final String clientSecret;
    private final String tokenEndpoint;
    private final String scope;
    private final boolean cacheTokens;
    private final Long cachedTokensDefaultExpirySeconds;
    private final Long cachedTokensExpirationMarginSeconds;
    private final static ConcurrentMap<OAuth2URIAndCredentials, TokenCache> tokenCache = new ConcurrentHashMap<>();
    private final String resourceIndicator;

    public OAuth2ClientConfigurer(String clientId, String clientSecret, String tokenEndpoint, String resourceIndicator,
                                  String scope, boolean cacheTokens,
                                  long cachedTokensDefaultExpirySeconds, long cachedTokensExpirationMarginSeconds) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenEndpoint = tokenEndpoint;
        this.resourceIndicator = resourceIndicator;
        this.scope = scope;
        this.cacheTokens = cacheTokens;
        this.cachedTokensDefaultExpirySeconds = cachedTokensDefaultExpirySeconds;
        this.cachedTokensExpirationMarginSeconds = cachedTokensExpirationMarginSeconds;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            clientBuilder.addRequestInterceptorFirst((HttpRequest request, EntityDetails entity, HttpContext context) -> {
                URI requestUri = getUriFromRequest(request);
                OAuth2URIAndCredentials uriAndCredentials = new OAuth2URIAndCredentials(requestUri, clientId, clientSecret);
                if (cacheTokens) {
                    if (tokenCache.containsKey(uriAndCredentials)
                            && !tokenCache.get(uriAndCredentials).isExpiredWithMargin(cachedTokensExpirationMarginSeconds)) {
                        request.setHeader(HttpHeaders.AUTHORIZATION, BEARER + tokenCache.get(uriAndCredentials).getToken());
                    } else {
                        JsonObject accessTokenResponse = getAccessTokenResponse(httpClient);
                        String accessToken = accessTokenResponse.getString("access_token");
                        String expiresIn = accessTokenResponse.getString("expires_in");
                        if (expiresIn != null && !expiresIn.isEmpty()) {
                            tokenCache.put(uriAndCredentials, new TokenCache(accessToken, expiresIn));
                        } else if (cachedTokensDefaultExpirySeconds > 0) {
                            tokenCache.put(uriAndCredentials, new TokenCache(accessToken, cachedTokensDefaultExpirySeconds));
                        }
                        request.setHeader(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
                    }
                } else {
                    JsonObject accessTokenResponse = getAccessTokenResponse(httpClient);
                    String accessToken = accessTokenResponse.getString("access_token");
                    request.setHeader(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject getAccessTokenResponse(HttpClient httpClient) throws IOException {
        String bodyStr = "grant_type=client_credentials";
        String url = tokenEndpoint;
        if (scope != null) {
            String sep = "?";
            if (url.contains("?")) {
                sep = "&";
            }
            url = url + sep + "scope=" + scope;
        }

        final HttpPost httpPost = new HttpPost(url);

        httpPost.addHeader(HttpHeaders.AUTHORIZATION,
                HttpCredentialsHelper.generateBasicAuthHeader(clientId, clientSecret));
        if (null != resourceIndicator) {
            bodyStr = String.join(bodyStr, "&resource=" + resourceIndicator);
        }
        httpPost.setEntity(new StringEntity(bodyStr, ContentType.APPLICATION_FORM_URLENCODED));

        AtomicReference<JsonObject> result = new AtomicReference<>();
        httpClient.execute(httpPost, response -> {
            try {
                String responseString = EntityUtils.toString(response.getEntity());

                if (response.getCode() == 200) {
                    result.set((JsonObject) Jsoner.deserialize(responseString));
                } else {
                    throw new HttpException(
                            "Received error response from token request with Status Code: " + response.getCode());
                }
            } catch (DeserializationException e) {
                throw new HttpException("Something went wrong when reading token request response", e);
            }
            return null;
        });
        return result.get();
    }

    private URI getUriFromRequest(HttpRequest request) {
        URI result;
        try {
            result = request.getUri();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private static class TokenCache {
        private String token;
        private Instant expirationTime;

        public TokenCache() {
        }

        public TokenCache(String token, String expires_in) {
            this.token = token;
            setExpirationTimeSeconds(expires_in);
        }

        public TokenCache(String accessToken, Long seconds) {
            this.token = accessToken;
            this.expirationTime = Instant.now().plusSeconds(seconds);
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expirationTime);
        }

        public boolean isExpiredWithMargin(Long marginSeconds) {
            return Instant.now().isAfter(expirationTime.minusSeconds(marginSeconds));
        }

        public void setExpirationTimeSeconds(String expires_in) {
            this.expirationTime = Instant.now().plusSeconds(Long.parseLong(expires_in));
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Instant getExpirationTime() {
            return expirationTime;
        }

        public void setExpirationTime(Instant expirationTime) {
            this.expirationTime = expirationTime;
        }
    }

    private record OAuth2URIAndCredentials(URI uri, String clientId, String clientSecret) {
    }

}
