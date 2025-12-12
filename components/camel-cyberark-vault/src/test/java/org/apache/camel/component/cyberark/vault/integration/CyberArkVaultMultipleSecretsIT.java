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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test demonstrating multiple secret retrieval in a single route
 */
// Must be manually tested. Provide CyberArk Conjur connection details using system properties:
// -Dcamel.cyberark.url=http://localhost:8080
// -Dcamel.cyberark.account=myAccount
// -Dcamel.cyberark.username=admin
// -Dcamel.cyberark.apiKey=your-api-key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.cyberark.url", matches = ".*",
                                 disabledReason = "CyberArk Conjur URL not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.account", matches = ".*",
                                 disabledReason = "CyberArk Conjur account not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.username", matches = ".*",
                                 disabledReason = "CyberArk Conjur username not provided"),
        @EnabledIfSystemProperty(named = "camel.cyberark.apiKey", matches = ".*",
                                 disabledReason = "CyberArk Conjur API key not provided")
})
public class CyberArkVaultMultipleSecretsIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CyberArkVaultMultipleSecretsIT.class);

    private static HttpClient httpClient;
    private static String authToken;

    @BeforeAll
    public static void setupSecrets() throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Authenticate
        authToken = authenticate();

        // Create multiple secrets for testing
        createSecret("app/config", "{\"port\":\"8080\",\"host\":\"localhost\",\"protocol\":\"https\"}");
        createSecret("db/primary", "{\"host\":\"db1.example.com\",\"port\":\"5432\",\"username\":\"dbadmin\"}");
        createSecret("db/replica", "{\"host\":\"db2.example.com\",\"port\":\"5432\",\"username\":\"readonly\"}");
        createSecret("cache/redis", "{\"host\":\"redis.example.com\",\"port\":\"6379\"}");
    }

    private static String authenticate() throws Exception {
        String url = String.format("%s/authn/%s/%s/authenticate",
                System.getProperty("camel.cyberark.url"),
                System.getProperty("camel.cyberark.account"),
                System.getProperty("camel.cyberark.username"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(System.getProperty("camel.cyberark.apiKey")))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static void createSecret(String secretId, String secretValue) {
        try {
            String url = String.format("%s/secrets/%s/variable/%s",
                    System.getProperty("camel.cyberark.url"),
                    System.getProperty("camel.cyberark.account"),
                    secretId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token token=\"" + Base64.getEncoder()
                            .encodeToString(authToken.getBytes(StandardCharsets.UTF_8)) + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(secretValue))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info("Created secret '{}': HTTP {}", secretId, response.statusCode());
        } catch (Exception e) {
            LOG.warn("Could not create secret '{}': {}", secretId, e.getMessage());
        }
    }

    @Test
    public void testMultipleSecretsInSingleRoute() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader("appHost", simple("{{cyberark:app/config#host}}"))
                        .setHeader("appPort", simple("{{cyberark:app/config#port}}"))
                        .setHeader("dbHost", simple("{{cyberark:db/primary#host}}"))
                        .setHeader("dbPort", simple("{{cyberark:db/primary#port}}"))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("appHost", "localhost");
        mock.expectedHeaderReceived("appPort", "8080");
        mock.expectedHeaderReceived("dbHost", "db1.example.com");
        mock.expectedHeaderReceived("dbPort", "5432");

        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMultipleDatabaseConfigs() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:primary")
                        .setBody(simple("{{cyberark:db/primary#username}}"))
                        .to("mock:result");

                from("direct:replica")
                        .setBody(simple("{{cyberark:db/replica#username}}"))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("dbadmin", "readonly");

        template.sendBody("direct:primary", "test");
        template.sendBody("direct:replica", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMixedSecretTypes() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader("protocol", simple("{{cyberark:app/config#protocol}}"))
                        .setHeader("redisHost", simple("{{cyberark:cache/redis#host}}"))
                        .setHeader("redisPort", simple("{{cyberark:cache/redis#port}}"))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("protocol", "https");
        mock.expectedHeaderReceived("redisHost", "redis.example.com");
        mock.expectedHeaderReceived("redisPort", "6379");

        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConnectionStringConstruction() throws Exception {
        context.getVaultConfiguration().cyberark().setUrl(System.getProperty("camel.cyberark.url"));
        context.getVaultConfiguration().cyberark().setAccount(System.getProperty("camel.cyberark.account"));
        context.getVaultConfiguration().cyberark().setUsername(System.getProperty("camel.cyberark.username"));
        context.getVaultConfiguration().cyberark().setApiKey(System.getProperty("camel.cyberark.apiKey"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // Build a connection string from multiple secret fields
                        .setBody(simple("${body}{{cyberark:db/primary#host}}:{{cyberark:db/primary#port}}"))
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("testdb1.example.com:5432");

        template.sendBody("direct:start", "test");
        MockEndpoint.assertIsSatisfied(context);
    }
}
