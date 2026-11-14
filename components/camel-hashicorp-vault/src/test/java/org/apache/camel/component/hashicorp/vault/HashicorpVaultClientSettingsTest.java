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

import java.util.Map;

import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AUTHENTICATION_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AUTH_MOUNT_PATH_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AWS_IAM_SERVER_ID_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AWS_REGION_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AWS_STS_ENDPOINT_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AZURE_CLIENT_ID_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.AZURE_RESOURCE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.CLOUD_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.HOST_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.JWT_FILE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.KEY_STORE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.KEY_STORE_PASSWORD_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.NAMESPACE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.PORT_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.ROLE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.ROLE_ID_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.SCHEME_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.SECRET_ID_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.TRUST_STORE_ENV;
import static org.apache.camel.component.hashicorp.vault.HashicorpVaultClientSettings.TRUST_STORE_PASSWORD_ENV;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashicorpVaultClientSettingsTest {

    @Test
    void takesTheEnvironmentVariablesWhenOneIsSet() {
        Map<String, String> environment = Map.of(
                HOST_ENV, "env-host", PORT_ENV, "8201", SCHEME_ENV, "http", AUTHENTICATION_ENV, "kubernetes",
                ROLE_ENV, "camel", JWT_FILE_ENV, "/var/run/token", AUTH_MOUNT_PATH_ENV, "k8s");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration(), environment::get);

        assertEquals("env-host", settings.getHost());
        assertEquals("8201", settings.getPort());
        assertEquals("http", settings.getScheme());
        assertEquals("kubernetes", settings.getAuthentication());
        assertEquals("camel", settings.getRole());
        assertEquals("/var/run/token", settings.getJwtFile());
        assertEquals("k8s", settings.getAuthMountPath());
        // nothing of the configuration is mixed in
        assertNull(settings.getRoleId());
        assertNull(settings.getSecretId());
    }

    @Test
    void aLoginEnvironmentVariableAloneSelectsTheEnvironmentVariables() {
        Map<String, String> environment = Map.of(ROLE_ID_ENV, "env-role-id", SECRET_ID_ENV, "env-secret-id");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration(), environment::get);

        assertEquals("env-role-id", settings.getRoleId());
        assertEquals("env-secret-id", settings.getSecretId());
        assertNull(settings.getHost());
    }

    @Test
    void aTlsEnvironmentVariableSelectsTheEnvironmentVariables() {
        Map<String, String> environment = Map.of(
                TRUST_STORE_ENV, "/etc/vault/ca.pem", TRUST_STORE_PASSWORD_ENV, "trust-password",
                KEY_STORE_ENV, "/etc/vault/client.p12", KEY_STORE_PASSWORD_ENV, "key-password");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration(), environment::get);

        assertEquals("/etc/vault/ca.pem", settings.getTrustStore());
        assertEquals("trust-password", settings.getTrustStorePassword());
        assertEquals("/etc/vault/client.p12", settings.getKeyStore());
        assertEquals("key-password", settings.getKeyStorePassword());
        assertNull(settings.getHost());
    }

    @Test
    void takesTheTlsSettingsOfTheConfiguration() {
        HashicorpVaultConfiguration configuration = configuration();
        configuration.setTrustStore("/etc/vault/ca.pem");
        configuration.setTrustStorePassword("trust-password");
        configuration.setKeyStore("/etc/vault/client.p12");
        configuration.setKeyStorePassword("key-password");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration, name -> null);

        assertEquals("/etc/vault/ca.pem", settings.getTrustStore());
        assertEquals("trust-password", settings.getTrustStorePassword());
        assertEquals("/etc/vault/client.p12", settings.getKeyStore());
        assertEquals("key-password", settings.getKeyStorePassword());
    }

    @Test
    void takesTheCloudSettingsOfTheEnvironmentVariables() {
        Map<String, String> environment = Map.of(
                AWS_IAM_SERVER_ID_ENV, "vault.example.com", AWS_REGION_ENV, "eu-west-1",
                AWS_STS_ENDPOINT_ENV, "https://sts.eu-west-1.amazonaws.com", AZURE_RESOURCE_ENV, "https://vault.hashicorp.com",
                AZURE_CLIENT_ID_ENV, "1b2c3d4e");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration(), environment::get);

        assertEquals("vault.example.com", settings.getAwsIamServerId());
        assertEquals("eu-west-1", settings.getAwsRegion());
        assertEquals("https://sts.eu-west-1.amazonaws.com", settings.getAwsStsEndpoint());
        assertEquals("https://vault.hashicorp.com", settings.getAzureResource());
        assertEquals("1b2c3d4e", settings.getAzureClientId());
        assertNull(settings.getHost());
    }

    @Test
    void takesTheCloudSettingsOfTheConfiguration() {
        HashicorpVaultConfiguration configuration = configuration();
        configuration.setAwsIamServerId("vault.example.com");
        configuration.setAwsRegion("eu-west-1");
        configuration.setAwsStsEndpoint("https://sts.eu-west-1.amazonaws.com");
        configuration.setAzureClientId("1b2c3d4e");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration, name -> null);

        assertEquals("vault.example.com", settings.getAwsIamServerId());
        assertEquals("eu-west-1", settings.getAwsRegion());
        assertEquals("https://sts.eu-west-1.amazonaws.com", settings.getAwsStsEndpoint());
        // the default resource of the configuration
        assertEquals("https://management.azure.com/", settings.getAzureResource());
        assertEquals("1b2c3d4e", settings.getAzureClientId());
    }

    @Test
    void takesTheSettingsOfTheConfigurationAlone() {
        HashicorpVaultConfiguration configuration = configuration();
        configuration.setTrustStore("/etc/vault/ca.pem");
        configuration.setAwsRegion("eu-west-1");

        HashicorpVaultClientSettings settings = HashicorpVaultClientSettings.fromConfiguration(configuration);

        assertEquals("config-host", settings.getHost());
        assertEquals("8200", settings.getPort());
        assertEquals("https", settings.getScheme());
        assertEquals("approle", settings.getAuthentication());
        assertEquals("config-role-id", settings.getRoleId());
        assertEquals("config-secret-id", settings.getSecretId());
        assertEquals("/etc/vault/ca.pem", settings.getTrustStore());
        assertEquals("eu-west-1", settings.getAwsRegion());
        assertEquals("https://management.azure.com/", settings.getAzureResource());
    }

    @Test
    void takesTheConfigurationWhenNoEnvironmentVariableIsSet() {
        Map<String, String> environment = Map.of(CLOUD_ENV, "true");

        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(configuration(), environment::get);

        assertEquals("config-host", settings.getHost());
        assertEquals("8200", settings.getPort());
        assertEquals("https", settings.getScheme());
        assertEquals("approle", settings.getAuthentication());
        assertEquals("config-role-id", settings.getRoleId());
        assertEquals("config-secret-id", settings.getSecretId());
        assertFalse(settings.isCloud(), "the cloud variable alone does not select the environment variables");
    }

    @Test
    void hasNoSettingsWithoutEnvironmentVariablesNorConfiguration() {
        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(null, name -> null);

        assertNull(settings.getHost());
        assertNull(settings.getAuthentication());
    }

    @Test
    void hasACloudNamespaceOnlyOnHashicorpCloud() {
        Map<String, String> environment = Map.of(NAMESPACE_ENV, "admin");
        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(null, environment::get);
        assertEquals("admin", settings.getNamespace());
        assertNull(settings.getCloudNamespace());

        environment = Map.of(NAMESPACE_ENV, "admin", CLOUD_ENV, "true");
        settings = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(null, environment::get);
        assertTrue(settings.isCloud());
        assertEquals("admin", settings.getCloudNamespace());
    }

    private static HashicorpVaultConfiguration configuration() {
        HashicorpVaultConfiguration configuration = new HashicorpVaultConfiguration();
        configuration.setHost("config-host");
        configuration.setPort("8200");
        configuration.setScheme("https");
        configuration.setAuthentication("approle");
        configuration.setRoleId("config-role-id");
        configuration.setSecretId("config-secret-id");
        return configuration;
    }
}
