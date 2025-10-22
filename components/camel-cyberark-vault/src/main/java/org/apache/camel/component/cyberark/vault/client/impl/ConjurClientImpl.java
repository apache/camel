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
package org.apache.camel.component.cyberark.vault.client.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ConjurClient using Java HTTP Client to communicate with CyberArk Conjur REST API
 */
public class ConjurClientImpl implements ConjurClient {

    private static final Logger LOG = LoggerFactory.getLogger(ConjurClientImpl.class);

    private final String url;
    private final String account;
    private final String username;
    private final String password;
    private final String apiKey;
    private String authToken;
    private final HttpClient httpClient;

    public ConjurClientImpl(
                            String url, String account, String username,
                            String password, String apiKey, String authToken) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.account = account;
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
        this.authToken = authToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String retrieveSecret(String secretId) {
        return retrieveSecret(secretId, null);
    }

    @Override
    public String retrieveSecret(String secretId, String version) {
        try {
            // Ensure we have a valid token
            if (authToken == null) {
                authToken = authenticate();
            }

            // URL encode the secret ID (replace / with %2F)
            String encodedSecretId = URLEncoder.encode(secretId, StandardCharsets.UTF_8);

            // Build the secrets endpoint URL
            String secretsUrl = String.format("%s/secrets/%s/variable/%s",
                    url, account, encodedSecretId);

            if (version != null && !version.isEmpty()) {
                secretsUrl += "?version=" + version;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(secretsUrl))
                    .header("Authorization", "Token token=\"" + Base64.getEncoder()
                            .encodeToString(authToken.getBytes(StandardCharsets.UTF_8)) + "\"")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 401) {
                // Token expired, re-authenticate and retry
                LOG.debug("Token expired, re-authenticating");
                authToken = authenticate();
                return retrieveSecret(secretId, version);
            } else {
                throw new IOException(
                        "Failed to retrieve secret: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error retrieving secret from Conjur: " + secretId, e);
        }
    }

    @Override
    public String authenticate() {
        try {
            String login;
            String credential;

            if (apiKey != null) {
                // Authenticate with API key
                login = username;
                credential = apiKey;
            } else if (password != null) {
                // Authenticate with password
                login = username;
                credential = password;
            } else if (authToken != null) {
                // Already have a token
                return authToken;
            } else {
                throw new IllegalStateException("No authentication credentials available");
            }

            // URL encode the login
            String encodedLogin = URLEncoder.encode(login, StandardCharsets.UTF_8);

            String authUrl = String.format("%s/authn/%s/%s/authenticate",
                    url, account, encodedLogin);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authUrl))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(credential))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String token = response.body();
                LOG.debug("Successfully authenticated with Conjur");
                return token;
            } else {
                throw new IOException("Authentication failed: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error authenticating with Conjur", e);
        }
    }

    @Override
    public void close() throws Exception {
        authToken = null;
    }
}
