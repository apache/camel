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
package org.apache.camel.component.azure.key.vault;

import java.util.Map;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeyVaultConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        SecretClient sc = new SecretClientBuilder().credential(new DefaultAzureCredentialBuilder().build())
                .vaultUrl("https://test.vault.azure.net").buildClient();

        context.getRegistry().bind("secretClient", sc);

        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                .createEndpoint("azure-key-valut://MyVault?secretClient=#secretClient");

        assertEquals("MyVault", endpoint.getConfiguration().getVaultName());
        assertNotNull(endpoint.getConfiguration().getSecretClient());
        assertNull(endpoint.getConfiguration().getClientId());
        assertNull(endpoint.getConfiguration().getClientSecret());
        assertNull(endpoint.getConfiguration().getTenantId());
    }

    @Test
    public void createEndpointWithMinimalIdentityConfiguration() throws Exception {
        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                .createEndpoint("azure-key-valut://MyVault?credentialType=AZURE_IDENTITY");

        assertNotNull(endpoint);
    }

    @Test
    public void createEndpointWithExplicitCredentials() throws Exception {

        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                .createEndpoint("azure-key-valut://MyVault?clientId=test&clientSecret=sec&tenantId=tenant");

        assertEquals("MyVault", endpoint.getConfiguration().getVaultName());
        assertNull(endpoint.getConfiguration().getSecretClient());
        assertEquals("test", endpoint.getConfiguration().getClientId());
        assertEquals("sec", endpoint.getConfiguration().getClientSecret());
        assertEquals("tenant", endpoint.getConfiguration().getTenantId());
    }

    @Test
    public void serviceUrlIsTheVaultUrl() throws Exception {
        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                .createEndpoint("azure-key-vault://MyVault?clientId=test&clientSecret=sec&tenantId=my-tenant-id");

        assertEquals("https://MyVault.vault.azure.net", endpoint.getServiceUrl());
        assertEquals("keyvault", endpoint.getServiceProtocol());
        assertEquals(Map.of("vault", "MyVault"), endpoint.getServiceMetadata());
        assertFalse(endpoint.getServiceUrl().contains("my-tenant-id"));
        assertFalse(endpoint.getServiceMetadata().containsValue("my-tenant-id"));
    }

    @Test
    public void serviceUrlFromSecretClient() throws Exception {
        SecretClient sc = new SecretClientBuilder().credential(new DefaultAzureCredentialBuilder().build())
                .vaultUrl("https://other.vault.azure.net").buildClient();
        context.getRegistry().bind("secretClient", sc);

        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                .createEndpoint("azure-key-vault://MyVault?secretClient=#secretClient");

        assertEquals("https://other.vault.azure.net", endpoint.getServiceUrl());
    }

    @Test
    public void createEndpointFailure() {
        Exception exception = assertThrows(Exception.class, () -> context.getEndpoint("azure-key-vault://MyVault"));
        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, exception);
        assertNotNull(cause, "IllegalArgumentException expected as cause of " + exception);
        assertTrue(cause.getMessage().contains("client Id, client secret and tenant Id must be specified"),
                cause.getMessage());
    }

    @Test
    public void autowiredSecretClient() {
        SecretClient sc = new SecretClientBuilder().credential(new DefaultAzureCredentialBuilder().build())
                .vaultUrl("https://autowired.vault.azure.net").buildClient();
        context.getRegistry().bind("mySecretClient", sc);

        KeyVaultEndpoint endpoint = context.getEndpoint("azure-key-vault://MyVault", KeyVaultEndpoint.class);

        assertSame(sc, endpoint.getConfiguration().getSecretClient());
        assertSame(sc, endpoint.getSecretClient());
        assertEquals("https://autowired.vault.azure.net", endpoint.getServiceUrl());
    }

    @Test
    public void componentConfiguration() {
        KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
        PropertyConfigurer configurer = component.getComponentPropertyConfigurer();
        assertTrue(configurer.configure(context, component, "clientId", "compClient", true));
        assertTrue(configurer.configure(context, component, "clientSecret", "compSecret", true));
        assertTrue(configurer.configure(context, component, "tenantId", "compTenant", true));
        assertTrue(configurer.configure(context, component, "operation", "getSecret", true));

        KeyVaultEndpoint endpoint = context.getEndpoint("azure-key-vault://MyVault?operation=deleteSecret",
                KeyVaultEndpoint.class);

        assertEquals("compClient", endpoint.getConfiguration().getClientId());
        assertEquals("compSecret", endpoint.getConfiguration().getClientSecret());
        assertEquals("compTenant", endpoint.getConfiguration().getTenantId());
        // the endpoint option replaces the component value, and the component keeps its own
        assertEquals(KeyVaultOperation.deleteSecret, endpoint.getConfiguration().getOperation());
        assertEquals(KeyVaultOperation.getSecret, component.getConfiguration().getOperation());
    }
}
