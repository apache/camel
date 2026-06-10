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
package org.apache.camel.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utility for making OAuth 2.0 token requests using the client_credentials grant.
 * <p/>
 * Used by both {@link OAuthClientCredentialsProcessor} (SPI path) and
 * {@link org.apache.camel.oauth.jakarta.ServletOAuth} (full OIDC path) to avoid duplicating HTTP POST logic.
 */
public final class OAuthTokenRequest {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthTokenRequest.class);
    private static final int MAX_INTROSPECTION_RESPONSE_SIZE_BYTES = 64 * 1024;
    private static final long MIN_INTROSPECTION_RETRY_INTERVAL_MILLIS = 30_000L;
    private static final ConcurrentMap<String, FailureRecord> INTROSPECTION_FAILURES = new ConcurrentHashMap<>();
    private static volatile LongSupplier currentTimeMillis = System::currentTimeMillis;

    private OAuthTokenRequest() {
    }

    /**
     * Acquires an access token using the OAuth 2.0 Client Credentials grant.
     *
     * @param  tokenEndpoint  the token endpoint URL
     * @param  clientId       the client identifier
     * @param  clientSecret   the client secret
     * @param  scope          the requested scope (may be null)
     * @return                the parsed JSON response containing access_token, expires_in, etc.
     * @throws OAuthException if the token request fails
     */
    public static JsonObject clientCredentialsGrant(
            String tokenEndpoint, String clientId, String clientSecret, String scope) {
        try {
            Request request = Request.post(tokenEndpoint)
                    .connectTimeout(Timeout.ofSeconds(5))
                    .responseTimeout(Timeout.ofSeconds(10))
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", basicAuthorization(clientId, clientSecret));

            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            if (scope != null && !scope.isBlank()) {
                params.add(new BasicNameValuePair("scope", scope));
            }
            request.bodyForm(params.toArray(new BasicNameValuePair[0]));

            String content = request.execute().returnContent().asString();
            LOG.debug("Token response from {}", tokenEndpoint);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new OAuthException("Failed to acquire OAuth token from " + tokenEndpoint, e);
        }
    }

    /**
     * Introspects a token per RFC 7662.
     *
     * @param  introspectionEndpoint the introspection endpoint URL
     * @param  clientId              the client identifier for Basic auth
     * @param  clientSecret          the client secret for Basic auth
     * @param  token                 the token to introspect
     * @return                       the parsed introspection response
     * @throws OAuthException        if the introspection request fails
     */
    public static JsonObject introspect(
            String introspectionEndpoint, String clientId, String clientSecret, String token) {
        return introspect(introspectionEndpoint, clientId, clientSecret, token, 5, 10);
    }

    /**
     * Introspects a token per RFC 7662.
     *
     * @param  introspectionEndpoint the introspection endpoint URL
     * @param  clientId              the client identifier for Basic auth
     * @param  clientSecret          the client secret for Basic auth
     * @param  token                 the token to introspect
     * @param  connectTimeoutSeconds the network connect timeout in seconds
     * @param  readTimeoutSeconds    the network read timeout in seconds
     * @return                       the parsed introspection response
     * @throws OAuthException        if the introspection request fails
     */
    public static JsonObject introspect(
            String introspectionEndpoint, String clientId, String clientSecret, String token,
            int connectTimeoutSeconds, int readTimeoutSeconds) {
        long now = now();
        assertCanAttemptIntrospection(introspectionEndpoint, now);

        HttpURLConnection connection = null;
        try {
            byte[] body = ("token=" + formEncode(token) + "&token_type_hint=access_token")
                    .getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) URI.create(introspectionEndpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutSeconds * 1000);
            connection.setReadTimeout(readTimeoutSeconds * 1000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Authorization", basicAuthorization(clientId, clientSecret));
            connection.setRequestProperty("Content-Length", Integer.toString(body.length));

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                OAuthException failure = new OAuthException("Introspection endpoint returned HTTP " + statusCode);
                if (statusCode >= 500) {
                    // server-side outage: enter the fail-fast window so the dead endpoint is not hammered
                    INTROSPECTION_FAILURES.put(introspectionEndpoint, new FailureRecord(now, failure));
                }
                throw failure;
            }

            String content = readBoundedStream(connection, MAX_INTROSPECTION_RESPONSE_SIZE_BYTES);
            if (content.isBlank()) {
                throw new OAuthException("Introspection endpoint returned an empty response body");
            }

            LOG.debug("Introspection response from {}", introspectionEndpoint);
            JsonElement json = JsonParser.parseString(content);
            if (!json.isJsonObject()) {
                throw new OAuthException("Introspection endpoint returned a non-object JSON response");
            }
            INTROSPECTION_FAILURES.remove(introspectionEndpoint);
            return json.getAsJsonObject();
        } catch (IOException e) {
            // transport-level failure (connect/read timeout, connection refused): enter the fail-fast window;
            // response-shape failures do not, so one malformed response cannot block all requests for 30s
            INTROSPECTION_FAILURES.put(introspectionEndpoint, new FailureRecord(now, e));
            throw new OAuthException("Failed to introspect token at " + introspectionEndpoint, e);
        } catch (Exception e) {
            throw new OAuthException("Failed to introspect token at " + introspectionEndpoint, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static void clearIntrospectionFailures() {
        INTROSPECTION_FAILURES.clear();
        currentTimeMillis = System::currentTimeMillis;
    }

    static void setCurrentTimeMillisSupplier(LongSupplier currentTimeMillis) {
        OAuthTokenRequest.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    private static void assertCanAttemptIntrospection(String introspectionEndpoint, long now) {
        FailureRecord failure = INTROSPECTION_FAILURES.get(introspectionEndpoint);
        if (failure != null && now - failure.failedAtMillis < MIN_INTROSPECTION_RETRY_INTERVAL_MILLIS) {
            throw new OAuthException(
                    "Introspection at " + introspectionEndpoint + " was attempted recently", failure.cause);
        }
    }

    private static final class FailureRecord {
        final long failedAtMillis;
        final Throwable cause;

        FailureRecord(long failedAtMillis, Throwable cause) {
            this.failedAtMillis = failedAtMillis;
            this.cause = cause;
        }
    }

    private static long now() {
        return currentTimeMillis.getAsLong();
    }

    private static String readBoundedStream(HttpURLConnection connection, int maxResponseSizeBytes) throws IOException {
        long contentLength = connection.getContentLengthLong();
        if (contentLength > maxResponseSizeBytes) {
            throw new OAuthException("Introspection response exceeds " + maxResponseSizeBytes + " bytes");
        }
        try (InputStream inputStream = responseStream(connection)) {
            if (inputStream == null) {
                return "";
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(
                    contentLength > 0 ? (int) contentLength : 1024);
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxResponseSizeBytes) {
                    throw new OAuthException("Introspection response exceeds " + maxResponseSizeBytes + " bytes");
                }
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream responseStream(HttpURLConnection connection) throws IOException {
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                return errorStream;
            }
            throw e;
        }
    }

    private static String basicAuthorization(String clientId, String clientSecret) {
        String auth = formEncode(clientId) + ":" + formEncode(clientSecret);
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Refreshes an access token using the refresh_token grant.
     *
     * @param  tokenEndpoint  the token endpoint URL
     * @param  clientId       the client identifier
     * @param  clientSecret   the client secret
     * @param  refreshToken   the refresh token
     * @return                the parsed JSON response containing the new access_token
     * @throws OAuthException if the refresh request fails
     */
    public static JsonObject refreshTokenGrant(
            String tokenEndpoint, String clientId, String clientSecret, String refreshToken) {
        try {
            String content = Request.post(tokenEndpoint)
                    .connectTimeout(Timeout.ofSeconds(5))
                    .responseTimeout(Timeout.ofSeconds(10))
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", basicAuthorization(clientId, clientSecret))
                    .bodyForm(
                            new BasicNameValuePair("grant_type", "refresh_token"),
                            new BasicNameValuePair("refresh_token", refreshToken))
                    .execute().returnContent().asString();

            LOG.debug("Refresh token response from {}", tokenEndpoint);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new OAuthException("Failed to refresh OAuth token from " + tokenEndpoint, e);
        }
    }
}
