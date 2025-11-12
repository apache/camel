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
package org.apache.camel.test.infra.ollama.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to check if an Ollama instance is available at a given URL.
 */
public final class OllamaConnectionChecker {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaConnectionChecker.class);
    private static final String OLLAMA_API_TAGS_PATH = "/api/tags";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private OllamaConnectionChecker() {
        // Utility class
    }

    /**
     * Checks if Ollama is available at the given base URL using the default timeout.
     *
     * @param  baseUrl the base URL of the Ollama instance (e.g., "http://localhost:11434")
     * @return         true if Ollama is available, false otherwise
     */
    public static boolean isAvailable(String baseUrl) {
        return isAvailable(baseUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Checks if Ollama is available at the given base URL with a custom timeout.
     *
     * @param  baseUrl the base URL of the Ollama instance (e.g., "http://localhost:11434")
     * @param  timeout the connection timeout duration
     * @return         true if Ollama is available, false otherwise
     */
    public static boolean isAvailable(String baseUrl, Duration timeout) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            LOG.debug("Base URL is null or empty, Ollama not available");
            return false;
        }

        try {
            String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            URI uri = URI.create(normalizedUrl + OLLAMA_API_TAGS_PATH);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            int responseCode = response.statusCode();
            boolean available = responseCode == 200;

            if (available) {
                LOG.debug("Ollama is available at {}", baseUrl);
            } else {
                LOG.debug("Ollama responded with HTTP {} at {}", responseCode, baseUrl);
            }

            return available;

        } catch (Exception e) {
            LOG.debug("Ollama is not available at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }
}
