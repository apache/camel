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

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
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
    public void createEndpointFailure() throws Exception {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            KeyVaultComponent component = context.getComponent("azure-key-vault", KeyVaultComponent.class);
            KeyVaultEndpoint endpoint = (KeyVaultEndpoint) component
                    .createEndpoint("azure-key-valut://MyVault");
        });
    }
}
