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
package org.apache.camel.component.azure.key.vault.integration.operations;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.azure.core.exception.HttpResponseException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.key.vault.KeyVaultConstants;
import org.apache.camel.test.infra.azure.key.vault.AzureKeyVaultConfigs;
import org.apache.camel.test.infra.azure.key.vault.clients.AzureKeyVaultClientUtils;
import org.apache.camel.test.infra.azure.key.vault.services.AzureKeyVaultService;
import org.apache.camel.test.infra.azure.key.vault.services.AzureKeyVaultServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The operations of the producer against the Key Vault of a Floci AZ container, or against a real vault when its name
 * and a service principal are given:
 * {@code mvn verify -DvaultName=myvault -DclientId=... -DclientSecret=... -DtenantId=...}. The test purges the secret
 * that it deletes: the real vault must not have purge protection, and the service principal must be allowed to purge.
 */
class KeyVaultOperationsIT extends CamelTestSupport {

    @RegisterExtension
    static AzureKeyVaultService service = createService();

    // a name of its own: a real vault keeps a deleted secret for days, and refuses to create another one with its name
    private final String secretName = "camel-it-" + UUID.randomUUID();

    private SecretClient secretClient;

    private static AzureKeyVaultService createService() {
        if (isSet(AzureKeyVaultConfigs.VAULT_NAME) && isSet(AzureKeyVaultConfigs.CLIENT_ID)
                && isSet(AzureKeyVaultConfigs.CLIENT_SECRET) && isSet(AzureKeyVaultConfigs.TENANT_ID)) {
            System.setProperty("azure.instance.type", "remote");
        }
        return AzureKeyVaultServiceFactory.createService();
    }

    private static boolean isSet(String property) {
        String value = System.getProperty(property);
        return value != null && !value.isBlank();
    }

    private static boolean isRemote() {
        return "remote".equals(System.getProperty("azure.instance.type"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        secretClient = AzureKeyVaultClientUtils.getClient();
        // on a real vault the endpoints build their client from the service principal: a client in the registry would
        // be autowired in its place
        if (!isRemote()) {
            context.getRegistry().bind("secretClient", secretClient);
        }
        return context;
    }

    @Test
    void createGetUpdateDeleteAndPurgeSecret() {
        Exchange created = template.send("direct:createSecret", exchange -> {
            exchange.getMessage().setHeader(KeyVaultConstants.SECRET_NAME, secretName);
            exchange.getMessage().setBody("TestValue");
        });
        assertNull(created.getException());
        KeyVaultSecret secret = assertInstanceOf(KeyVaultSecret.class, created.getMessage().getBody());
        assertEquals(secretName, secret.getName());
        assertEquals("TestValue", secret.getValue());

        Exchange read = template.send("direct:getSecret",
                exchange -> exchange.getMessage().setHeader(KeyVaultConstants.SECRET_NAME, secretName));
        assertNull(read.getException());
        assertEquals("TestValue", read.getMessage().getBody());

        SecretProperties properties = secret.getProperties()
                .setContentType("text/plain")
                .setTags(Map.of("owner", "camel"));
        Exchange updated = template.send("direct:updateSecretProperties",
                exchange -> exchange.getMessage().setHeader(KeyVaultConstants.SECRET_PROPERTIES, properties));
        assertNull(updated.getException());
        SecretProperties updatedProperties = assertInstanceOf(SecretProperties.class, updated.getMessage().getBody());
        assertEquals(secretName, updatedProperties.getName());
        assertEquals("text/plain", updatedProperties.getContentType());
        assertEquals(Map.of("owner", "camel"), updatedProperties.getTags());

        Exchange deleted = template.send("direct:deleteSecret",
                exchange -> exchange.getMessage().setHeader(KeyVaultConstants.SECRET_NAME, secretName));
        assertNull(deleted.getException());
        DeletedSecret deletedSecret = assertInstanceOf(DeletedSecret.class, deleted.getMessage().getBody());
        assertEquals(secretName, deletedSecret.getName());
        assertNotNull(deletedSecret.getRecoveryId());
        assertNotNull(deletedSecret.getDeletedOn());
        assertNotNull(deletedSecret.getScheduledPurgeDate());

        Exchange missing = template.send("direct:getSecret",
                exchange -> exchange.getMessage().setHeader(KeyVaultConstants.SECRET_NAME, secretName));
        assertEquals(404,
                assertInstanceOf(HttpResponseException.class, missing.getException()).getResponse().getStatusCode());

        Exchange purged = template.send("direct:purgeDeletedSecret",
                exchange -> exchange.getMessage().setHeader(KeyVaultConstants.SECRET_NAME, secretName));
        assertNull(purged.getException());
        // a real vault purges the deleted secret in the background
        await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> assertEquals(404,
                        assertThrows(HttpResponseException.class, () -> secretClient.getDeletedSecret(secretName))
                                .getResponse().getStatusCode()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret").to(keyVault("createSecret"));
                from("direct:getSecret").to(keyVault("getSecret"));
                from("direct:updateSecretProperties").to(keyVault("updateSecretProperties"));
                from("direct:deleteSecret").to(keyVault("deleteSecret"));
                from("direct:purgeDeletedSecret").to(keyVault("purgeDeletedSecret"));
            }
        };
    }

    /*
     * On Floci AZ the endpoints use the client of the test-infra, through the secretClient option: the client that the
     * component builds reaches only https://<vaultName>.vault.azure.net. On a real vault they use the service
     * principal, as before, so that the component builds its client.
     */
    private static String keyVault(String operation) {
        String client = isRemote()
                ? "clientId=RAW({{clientId}})&clientSecret=RAW({{clientSecret}})&tenantId=RAW({{tenantId}})"
                : "secretClient=#secretClient";
        return "azure-key-vault://" + service.vaultName() + "?" + client + "&operation=" + operation;
    }
}
