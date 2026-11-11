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
package org.apache.camel.component.hashicorp.vault;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.AzureMsiAuthenticationOptions;
import org.springframework.vault.authentication.GcpComputeAuthenticationOptions;
import org.springframework.vault.support.VaultResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cloud logins. The aws-iam login only signs its request, so it logs in against a fake Vault. The azure-msi and
 * gcp-gce logins need the metadata service of their cloud: their options are checked instead.
 */
class HashicorpVaultCloudLoginTest {

    // the AWS SDK default chain reads the credentials and the region from these system properties first
    private static final List<String> AWS_PROPERTIES = List.of("aws.accessKeyId", "aws.secretAccessKey", "aws.region");

    private final Map<String, String> savedProperties = new HashMap<>();
    private FakeVault vault;
    private CamelContext context;
    private HashicorpVaultClient client;

    @BeforeEach
    void startVault() throws Exception {
        for (String name : AWS_PROPERTIES) {
            savedProperties.put(name, System.getProperty(name));
        }
        System.setProperty("aws.accessKeyId", "AKIDCAMELTEST");
        System.setProperty("aws.secretAccessKey", "camel-test-secret");
        System.setProperty("aws.region", "us-east-1");

        vault = new FakeVault();
        vault.on("secret/data/api", request -> FakeVault.secret("pw"));
        vault.on("auth/token/revoke-self", request -> FakeVault.noContent());
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopVault() {
        if (client != null) {
            client.close();
        }
        context.stop();
        vault.close();
        savedProperties.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    @Test
    void awsIamLogsInWithARequestSignedForTheRegionAndEndpoint() {
        vault.on("auth/aws/login", request -> FakeVault.login("aws-token", 3600));
        HashicorpVaultClientSettings settings = settings("aws-iam");
        settings.setRole("camel");
        settings.setAwsRegion("eu-west-1");
        settings.setAwsStsEndpoint("https://sts.eu-west-1.amazonaws.com");
        settings.setAwsIamServerId("vault.example.com");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());

        Map<?, ?> login = vault.requests("auth/aws/login").get(0).json();
        assertEquals("camel", login.get("role"));
        assertEquals("POST", login.get("iam_http_request_method"));
        assertEquals("https://sts.eu-west-1.amazonaws.com", decode(login.get("iam_request_url")));
        assertEquals("Action=GetCallerIdentity&Version=2011-06-15", decode(login.get("iam_request_body")));
        Map<?, ?> headers = JsonMapper.builder().build().readValue(decode(login.get("iam_request_headers")), Map.class);
        assertEquals(List.of("vault.example.com"), headers.get("X-Vault-AWS-IAM-Server-ID"));
        String authorization = ((List<?>) headers.get("Authorization")).get(0).toString();
        assertTrue(authorization.contains("Credential=AKIDCAMELTEST/"), authorization);
        assertTrue(authorization.contains("/eu-west-1/sts/aws4_request"), authorization);
        assertEquals("aws-token", vault.requests("secret/data/api").get(0).token());
    }

    @Test
    void awsIamSignsForTheGlobalEndpointAndTheRegionOfTheAwsSdkByDefault() {
        vault.on("auth/my-aws/login", request -> FakeVault.login("aws-token", 3600));
        HashicorpVaultClientSettings settings = settings("aws-iam");
        settings.setRole("camel");
        settings.setAuthMountPath("my-aws");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());

        Map<?, ?> login = vault.requests("auth/my-aws/login").get(0).json();
        assertEquals("https://sts.amazonaws.com/", decode(login.get("iam_request_url")));
        Map<?, ?> headers = JsonMapper.builder().build().readValue(decode(login.get("iam_request_headers")), Map.class);
        assertNull(headers.get("X-Vault-AWS-IAM-Server-ID"));
        String authorization = ((List<?>) headers.get("Authorization")).get(0).toString();
        assertTrue(authorization.contains("/us-east-1/sts/aws4_request"), authorization);
    }

    @Test
    void azureMsiAsksForTheTokenOfTheResourceOfVault() {
        HashicorpVaultClientSettings settings = settings("azure-msi");
        settings.setRole("camel");

        AzureMsiAuthenticationOptions options = HashicorpVaultClient.azureMsiOptions(settings);
        assertEquals("camel", options.getRole());
        assertEquals("azure", options.getPath());
        assertEquals(URI.create("http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01"
                                + "&resource=https%3A%2F%2Fmanagement.azure.com%2F"),
                options.getIdentityTokenServiceUri());

        settings.setAzureResource("https://vault.hashicorp.com");
        settings.setAzureClientId("1b2c3d4e");
        settings.setAuthMountPath("my-azure");
        options = HashicorpVaultClient.azureMsiOptions(settings);
        assertEquals("my-azure", options.getPath());
        assertEquals(URI.create("http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01"
                                + "&resource=https%3A%2F%2Fvault.hashicorp.com&client_id=1b2c3d4e"),
                options.getIdentityTokenServiceUri());

        // the login happens at the first request: creating and closing the client does not reach the cloud
        HashicorpVaultClient.create(context, this, settings).close();
    }

    @Test
    void gcpGceLogsInAsTheRoleWithTheDefaultServiceAccount() {
        HashicorpVaultClientSettings settings = settings("gcp-gce");
        settings.setRole("camel");

        GcpComputeAuthenticationOptions options = HashicorpVaultClient.gcpComputeOptions(settings);
        assertEquals("camel", options.getRole());
        assertEquals("gcp", options.getPath());
        assertEquals("default", options.getServiceAccount());

        settings.setAuthMountPath("my-gcp");
        assertEquals("my-gcp", HashicorpVaultClient.gcpComputeOptions(settings).getPath());

        HashicorpVaultClient.create(context, this, settings).close();
    }

    @Test
    void theCloudLoginsNeedARole() {
        for (String login : List.of("aws-iam", "azure-msi", "gcp-gce")) {
            HashicorpVaultClientSettings settings = settings(login);
            IllegalArgumentException e
                    = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.validate(settings));
            assertEquals("The " + login + " login needs a role", e.getMessage());
        }
    }

    private HashicorpVaultClientSettings settings(String authentication) {
        HashicorpVaultClientSettings settings = new HashicorpVaultClientSettings();
        settings.setHost("localhost");
        settings.setPort(String.valueOf(vault.port()));
        settings.setScheme("http");
        settings.setAuthentication(authentication);
        return settings;
    }

    private String readPassword() {
        VaultResponse response = client.getVaultTemplate().read("secret/data/api");
        return ((Map<?, ?>) response.getData().get("data")).get("password").toString();
    }

    private static String decode(Object base64) {
        return new String(Base64.getDecoder().decode(base64.toString()), StandardCharsets.UTF_8);
    }
}
