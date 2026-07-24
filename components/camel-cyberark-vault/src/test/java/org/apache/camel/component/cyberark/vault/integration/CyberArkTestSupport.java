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
package org.apache.camel.component.cyberark.vault.integration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.cyberark.vault.services.CyberArkVaultService;
import org.apache.camel.test.infra.cyberark.vault.services.CyberArkVaultServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.vault.CyberArkVaultConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class CyberArkTestSupport extends CamelTestSupport {

    @RegisterExtension
    static CyberArkVaultService service = CyberArkVaultServiceFactory.createService();

    static final Logger LOG = LoggerFactory.getLogger(CyberArkTestSupport.class);

    static HttpClient httpClient;
    static String authToken;

    @EndpointInject("mock:result")
    MockEndpoint mockResult;

    @BeforeAll
    public static void beforeAll() throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        authToken = authenticate();
    }

    @AfterAll
    public static void afterAll() throws Exception {
        authToken = null;
        httpClient = null;
    }

    /**
     * Points the context wide vault configuration at the Conjur instance started by the test infra.
     */
    void configureVault() {
        CyberArkVaultConfiguration cyberark = context.getVaultConfiguration().cyberark();
        cyberark.setUrl(service.url());
        cyberark.setAccount(service.account());
        cyberark.setUsername(service.username());
        cyberark.setApiKey(service.apiKey());
    }

    private static String authenticate() throws Exception {
        String url = String.format("%s/authn/%s/%s/authenticate",
                service.url(),
                service.account(),
                service.username());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "text/plain")
                .header("Accept-Encoding", "base64")
                .POST(HttpRequest.BodyPublishers.ofString(service.apiKey()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess("Authenticate", response);

        return response.body();
    }

    static void loadPolicy(String policy) throws Exception {

        String policyUrl = String.format("%s/policies/%s/policy/%s",
                service.url(),
                service.account(),
                URLEncoder.encode("root", StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(policyUrl))
                .header("Authorization", "Token token=\"" + authToken + "\"")
                .header("Content-Type", "text/plain")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(policy))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess("Load policy", response);
    }

    static void createSecret(String secretId, String secretValue) throws Exception {
        String url = String.format("%s/secrets/%s/variable/%s",
                service.url(),
                service.account(),
                URLEncoder.encode(secretId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token token=\"" + authToken + "\"")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString(secretValue))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess("Created secret", response);
    }

    static void requireSuccess(String op, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    String.format("%s failed: HTTP %d: %s", op, response.statusCode(), response.body()));
        }
        LOG.info("{} ok - HTTP {}", op, response.statusCode());
    }
}
