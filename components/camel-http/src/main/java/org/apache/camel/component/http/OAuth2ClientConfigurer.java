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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuth2ClientConfigurer extends ServiceSupport implements HttpClientConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2ClientConfigurer.class);

    private final String clientId;
    private final String clientSecret;
    private final String tokenEndpoint;
    private final String scope;
    private final boolean cacheTokens;
    private final Long cachedTokensDefaultExpirySeconds;
    private final Long cachedTokensExpirationMarginSeconds;
    private final static ConcurrentMap<OAuth2URIAndCredentials, TokenCache> tokenCache = new ConcurrentHashMap<>();
    private final boolean useBodyAuthentication;
    private final String resourceIndicator;
    private final URI targetUri;
    private HttpClient httpClient;

    public OAuth2ClientConfigurer(String clientId, String clientSecret, String tokenEndpoint, String resourceIndicator,
                                  String scope, boolean cacheTokens,
                                  long cachedTokensDefaultExpirySeconds, long cachedTokensExpirationMarginSeconds,
                                  boolean useBodyAuthentication) {
        this(clientId, clientSecret, tokenEndpoint, resourceIndicator, scope, cacheTokens,
             cachedTokensDefaultExpirySeconds, cachedTokensExpirationMarginSeconds, useBodyAuthentication, null);
    }

    /**
     * @param targetUri the URI the endpoint addresses. The bearer token is only attached to requests for the same
     *                  authority, so that a redirect chosen by the remote server cannot collect it. Null keeps the
     *                  previous behaviour of attaching it to whatever authority the request names.
     */
    OAuth2ClientConfigurer(String clientId, String clientSecret, String tokenEndpoint, String resourceIndicator,
                           String scope, boolean cacheTokens,
                           long cachedTokensDefaultExpirySeconds, long cachedTokensExpirationMarginSeconds,
                           boolean useBodyAuthentication, URI targetUri) {
        this.targetUri = targetUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenEndpoint = tokenEndpoint;
        this.resourceIndicator = resourceIndicator;
        this.scope = scope;
        this.cacheTokens = cacheTokens;
        this.cachedTokensDefaultExpirySeconds = cachedTokensDefaultExpirySeconds;
        this.cachedTokensExpirationMarginSeconds = cachedTokensExpirationMarginSeconds;
        this.useBodyAuthentication = useBodyAuthentication;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        // create a new http client only used for oauth token requests
        this.httpClient = clientBuilder.build();

        clientBuilder.addRequestInterceptorFirst((HttpRequest request, EntityDetails entity, HttpContext context) -> {
            URI requestUri = getUriFromRequest(request);
            if (!isTargetAuthority(requestUri)) {
                // HttpClient runs protocol-level request interceptors inside ProtocolExec, which sits below
                // RedirectExec, so this runs again for every redirect hop. Without this check the bearer token is
                // re-attached to whichever authority the Location header named.
                LOG.debug("Not attaching the OAuth2 bearer token to {}, which is not the endpoint's authority {}",
                        requestUri, targetUri);
                return;
            }
            OAuth2URIAndCredentials uriAndCredentials = new OAuth2URIAndCredentials(
                    requestUri, clientId, clientSecret, tokenEndpoint, scope, resourceIndicator);
            if (cacheTokens) {
                if (tokenCache.containsKey(uriAndCredentials)
                        && !tokenCache.get(uriAndCredentials).isExpiredWithMargin(cachedTokensExpirationMarginSeconds)) {
                    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCache.get(uriAndCredentials).getToken());
                } else {
                    JsonObject accessTokenResponse = getAccessTokenResponse(httpClient);
                    String accessToken = accessTokenResponse.getString("access_token");
                    String expiresIn = accessTokenResponse.getString("expires_in");
                    if (expiresIn != null && !expiresIn.isEmpty()) {
                        tokenCache.put(uriAndCredentials, new TokenCache(accessToken, expiresIn));
                    } else if (cachedTokensDefaultExpirySeconds > 0) {
                        tokenCache.put(uriAndCredentials, new TokenCache(accessToken, cachedTokensDefaultExpirySeconds));
                    }
                    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                }
            } else {
                JsonObject accessTokenResponse = getAccessTokenResponse(httpClient);
                String accessToken = accessTokenResponse.getString("access_token");
                request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }
        });
    }

    private boolean isTargetAuthority(URI requestUri) {
        if (targetUri == null) {
            return true;
        }
        if (targetUri.getScheme() == null || targetUri.getHost() == null
                || requestUri == null || requestUri.getScheme() == null || requestUri.getHost() == null) {
            return false;
        }
        return targetUri.getScheme().equalsIgnoreCase(requestUri.getScheme())
                && targetUri.getHost().equalsIgnoreCase(requestUri.getHost())
                && effectivePort(targetUri) == effectivePort(requestUri);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return -1;
    }

    private JsonObject getAccessTokenResponse(HttpClient httpClient) throws IOException {
        String bodyStr = "grant_type=client_credentials";
        if (scope != null) {
            bodyStr += "&scope=" + scope;
        }

        final HttpPost httpPost = new HttpPost(tokenEndpoint);
        if (useBodyAuthentication) {
            bodyStr += "&client_id=" + clientId;
            bodyStr += "&client_secret=" + clientSecret;
        } else {
            httpPost.addHeader(HttpHeaders.AUTHORIZATION,
                    HttpCredentialsHelper.generateBasicAuthHeader(clientId, clientSecret));
        }
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

        public TokenCache(String token, String expires_in) {
            this.token = token;
            setExpirationTimeSeconds(expires_in);
        }

        public TokenCache(String accessToken, Long seconds) {
            this.token = accessToken;
            this.expirationTime = Instant.now().plusSeconds(seconds);
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
    }

    /**
     * Cache key for a minted token.
     * <p>
     * Every field that shapes the token request has to be part of it. The map is static, so it is shared by every
     * configurer instance and every CamelContext in the JVM; a key that left out the scope, the token endpoint or the
     * resource indicator would let a route configured for a narrow scope be served a broad-scope token that another
     * route cached first, which defeats the scoping the operator asked for and makes the audit trail misleading.
     */
    private record OAuth2URIAndCredentials(URI uri, String clientId, String clientSecret, String tokenEndpoint,
            String scope, String resourceIndicator) {
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (httpClient instanceof Closeable closeable) {
            IOHelper.close(closeable);
            httpClient = null;
        }
    }
}
