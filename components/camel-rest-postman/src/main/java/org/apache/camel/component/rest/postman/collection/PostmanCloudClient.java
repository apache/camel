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
package org.apache.camel.component.rest.postman.collection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLContext;

/**
 * Fetches a collection document from the Postman cloud API.
 * <p>
 * This is the only place in the component that sends the Postman API key anywhere, and it is deliberately strict about
 * where that key can go. Redirects are rejected outright rather than followed, because a redirect from a mistyped or
 * hijacked API URL would replay the credential to whatever host the {@code Location} names.
 */
public final class PostmanCloudClient {

    /**
     * The largest collection document that will be accepted, so a remote peer cannot exhaust the heap.
     */
    public static final long MAX_COLLECTION_BYTES = 8L * 1024 * 1024;

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final String apiUrl;
    private final String apiKey;
    private final String apiKeyHeader;
    private final Duration requestTimeout;
    private final HttpClient client;

    public PostmanCloudClient(String apiUrl, String apiKey, String apiKeyHeader,
                              Duration connectTimeout, Duration requestTimeout, SSLContext sslContext) {
        this.apiUrl = stripTrailingSlash(apiUrl);
        this.apiKey = apiKey;
        this.apiKeyHeader = apiKeyHeader;
        this.requestTimeout = requestTimeout;

        // built once: every HttpClient allocates its own selector and executor threads, so creating one per
        // fetch would leak threads across repeated cache misses
        HttpClient.Builder builder = HttpClient.newBuilder()
                // never follow a redirect: doing so would replay the API key to the redirect target
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.client = builder.build();
    }

    /**
     * Fetches the collection with the given uid.
     * <p>
     * Both the bare collection UUID and the {@code {ownerId}-{uuid}} form are accepted by the Postman API, so the uid
     * is passed through unchanged apart from path encoding.
     *
     * @param  uid the collection uid
     * @return     the raw JSON document, which the caller is expected to unwrap and parse
     */
    public String fetchCollection(String uid) throws IOException, InterruptedException, URISyntaxException {
        URI uri = new URI(apiUrl + "/collections/" + encodePathSegment(uid));

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .timeout(requestTimeout)
                .GET();
        if (apiKey != null && !apiKey.isEmpty()) {
            request.header(apiKeyHeader, apiKey);
        }

        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse("an unspecified location");
                throw new IOException(
                        "Fetching Postman collection " + uid + " redirected to " + location
                                      + " - redirects are blocked because following one would send the Postman API key"
                                      + " to the redirect target. Configure postmanApiUrl with the final URL instead.");
            }
            if (status != 200) {
                // the message deliberately carries only the uid and the status, never the key or the response body
                throw new IOException(
                        "Failed to fetch Postman collection " + uid + " from " + apiUrl + ": HTTP " + status);
            }
            byte[] content = BoundedInputStreamReader.readAtMost(body, MAX_COLLECTION_BYTES, "Postman collection " + uid);
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Rejects an API URL that would send the key over an unencrypted connection.
     * <p>
     * Plain HTTP is tolerated only for loopback hosts, which is what makes it possible to point the component at a
     * local stub server in tests.
     *
     * @throws IllegalArgumentException when the URL is malformed or is plain HTTP to a remote host
     */
    public static void validateApiUrl(String apiUrl) {
        URI uri;
        try {
            uri = new URI(apiUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("postmanApiUrl is not a valid URI: " + apiUrl, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("postmanApiUrl must be an absolute URL, was: " + apiUrl);
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return;
        }
        if (!"http".equals(scheme)) {
            throw new IllegalArgumentException("postmanApiUrl must use http or https, was: " + apiUrl);
        }
        String host = uri.getHost();
        if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "postmanApiUrl must use https, because plain http would send the Postman API key in clear text."
                                               + " Plain http is allowed only for localhost. Was: " + apiUrl);
        }
    }

    private static String encodePathSegment(String segment) throws URISyntaxException {
        // a uid is expected to be alphanumeric with dashes, so reject anything that could escape the path
        if (!segment.matches("[A-Za-z0-9._~-]+")) {
            throw new URISyntaxException(segment, "Postman collection uid contains illegal characters");
        }
        return segment;
    }

    private static String stripTrailingSlash(String url) {
        String answer = url;
        while (answer.endsWith("/")) {
            answer = answer.substring(0, answer.length() - 1);
        }
        return answer;
    }
}
