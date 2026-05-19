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
package org.apache.camel.component.a2a.card;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.apache.camel.component.a2a.util.BoundedInputStreamReader;

/**
 * Loads agent cards from multiple sources: classpath, file, or HTTP(S) URL.
 */
public class AgentCardLoader {

    private static final ObjectMapper MAPPER = A2AJsonMapper.instance();

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final long MAX_AGENT_CARD_BYTES = 1024 * 1024;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public AgentCardLoader() {
        this(null, DEFAULT_REQUEST_TIMEOUT);
    }

    public AgentCardLoader(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
    }

    /**
     * Loads an agent card from the given source.
     *
     * @param  source    the source location (classpath:, file:, http://, https://, or plain name)
     * @return           the loaded agent card, or an empty card if source is null/blank or a plain name
     * @throws Exception if loading or parsing fails
     */
    public AgentCard load(String source) throws Exception {
        if (source == null || source.isBlank()) {
            return AgentCard.builder().build();
        }

        if (source.startsWith(CLASSPATH_PREFIX)) {
            return loadFromClasspath(source.substring(CLASSPATH_PREFIX.length()));
        } else if (source.startsWith(FILE_PREFIX)) {
            return loadFromFile(source.substring(FILE_PREFIX.length()));
        } else if (source.startsWith(HTTP_PREFIX) || source.startsWith(HTTPS_PREFIX)) {
            return loadFromUrl(source);
        } else {
            return AgentCard.builder().build();
        }
    }

    /**
     * Expands a URL to include the well-known agent card path if needed.
     *
     * @param  url the URL to expand
     * @return     the expanded URL
     */
    public static String expandWellKnownUrl(String url) {
        if (url.endsWith(".json")) {
            return url;
        } else if (url.endsWith("/")) {
            return url + ".well-known/agent-card.json";
        } else {
            return url + A2AConstants.WELL_KNOWN_PATH;
        }
    }

    private AgentCard loadFromClasspath(String path) throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return MAPPER.readValue(is, AgentCard.class);
        }
    }

    private AgentCard loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        try (InputStream is = new FileInputStream(file)) {
            return MAPPER.readValue(is, AgentCard.class);
        }
    }

    private AgentCard loadFromUrl(String url) throws Exception {
        String expandedUrl = expandWellKnownUrl(url);
        HttpClient client = httpClient != null
                ? httpClient
                : HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(expandedUrl))
                .header("Accept", "application/json, " + A2AConstants.CONTENT_TYPE)
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream body = response.body()) {
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("Location").orElse("unknown");
                throw new IllegalStateException(
                        "Agent card URL redirected to " + location + " — redirects are blocked for security. "
                                                + "Use the direct URL instead.");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to fetch agent card from " + url + ": HTTP " + response.statusCode());
            }

            byte[] cardJson = BoundedInputStreamReader.readAtMost(
                    body, MAX_AGENT_CARD_BYTES, "Agent card response");
            return MAPPER.readValue(cardJson, AgentCard.class);
        }
    }
}
