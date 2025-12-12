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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cyberark.vault.CyberArkVaultConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
public class CyberArkVaultProducerIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CyberArkVaultProducerIT.class);

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    private static HttpClient httpClient;
    private static String authToken;

    @BeforeAll
    public static void setupSecrets() throws Exception {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Authenticate
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
        authToken = response.body();

        // Create test secrets
        createSecret("test/secret", "mySecretValue");
        createSecret("production/database", "{\"username\":\"prod-user\",\"password\":\"prod-pass\"}");
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
    public void testRetrieveSecret() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:getSecret", "");

        mockResult.assertIsSatisfied();
        Exchange exchange = mockResult.getExchanges().get(0);
        assertNotNull(exchange);
        String secretValue = exchange.getMessage().getBody(String.class);
        assertEquals("mySecretValue", secretValue);
    }

    @Test
    public void testRetrieveSecretWithHeader() throws Exception {
        mockResult.expectedMessageCount(1);
        mockResult.reset();

        template.send("direct:getSecretDynamic", exchange -> {
            exchange.getMessage().setHeader(CyberArkVaultConstants.SECRET_ID, "production/database");
        });

        mockResult.assertIsSatisfied();
        Exchange exchange = mockResult.getExchanges().get(0);
        assertNotNull(exchange);
        String secretValue = exchange.getMessage().getBody(String.class);
        assertNotNull(secretValue);
        // Should contain JSON with username and password
        assert secretValue.contains("prod-user");
    }

    @Test
    public void testRetrieveSecretVerifyHeaders() throws Exception {
        mockResult.expectedMessageCount(1);
        mockResult.reset();

        template.sendBody("direct:getSecret", "");

        mockResult.assertIsSatisfied();
        Exchange exchange = mockResult.getExchanges().get(0);
        assertNotNull(exchange);

        String secretId = exchange.getMessage().getHeader(CyberArkVaultConstants.SECRET_ID, String.class);
        assertEquals("test/secret", secretId);

        String secretValue = exchange.getMessage().getHeader(CyberArkVaultConstants.SECRET_VALUE, String.class);
        assertEquals("mySecretValue", secretValue);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String url = System.getProperty("camel.cyberark.url");
                String account = System.getProperty("camel.cyberark.account");
                String username = System.getProperty("camel.cyberark.username");
                String apiKey = System.getProperty("camel.cyberark.apiKey");

                from("direct:getSecret")
                        .toF("cyberark-vault:secret?secretId=test/secret&url=%s&account=%s&username=%s&apiKey=%s",
                                url, account, username, apiKey)
                        .to("mock:result");

                from("direct:getSecretDynamic")
                        .toF("cyberark-vault:secret?url=%s&account=%s&username=%s&apiKey=%s",
                                url, account, username, apiKey)
                        .to("mock:result");
            }
        };
    }
}
