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

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Manage secrets and keys in Azure Key Vault Service
 */
@UriEndpoint(firstVersion = "3.17.0", scheme = "azure-key-vault", title = "Azure Key Vault",
             syntax = "azure-key-vault:vaultName", category = {
                     Category.CLOUD, Category.CLOUD },
             producerOnly = true,
             headersClass = KeyVaultConstants.class)
@Metadata(annotations = {
        "vault=azure-key-vault",
})
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "getSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "updateSecretProperties",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteSecret", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "purgeDeletedSecret",
                                                         carriers = Carrier.ANY) },
                                  description = "The value of the secret for createSecret, the default, read as text: a Map or"
                                                + " a POJO is stored as its toString(), and no body fails. The name of the secret"
                                                + " is in the CamelAzureKeyVaultSecretName header, and updateSecretProperties"
                                                + " takes the SecretProperties from the CamelAzureKeyVaultSecretProperties header:"
                                                + " the other operations do not use the body"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "com.azure.security.keyvault.secrets.models.KeyVaultSecret",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getSecret", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "updateSecretProperties",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.azure.security.keyvault.secrets.models.SecretProperties"),
                                           @BodyType.When(option = "operation", value = "deleteSecret",
                                                          carriers = Carrier.NULL),
                                           @BodyType.When(option = "operation", value = "purgeDeletedSecret",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "The KeyVaultSecret of the SDK for createSecret, the default; the value of the"
                                                 + " secret as text for getSecret; the SecretProperties for"
                                                 + " updateSecretProperties. No body after deleteSecret, which waits until the"
                                                 + " secret is deleted"))
public class KeyVaultEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private SecretClient secretClient;

    @UriParam
    private KeyVaultConfiguration configuration;

    public KeyVaultEndpoint(final String uri, final Component component, final KeyVaultConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        secretClient = configuration.getSecretClient() != null
                ? configuration.getSecretClient() : createSecretClient();
    }

    private SecretClient createSecretClient() {
        SecretClient localClient;
        // Build key vault URI
        String keyVaultUri = "https://" + getConfiguration().getVaultName() + ".vault.azure.net";

        TokenCredential credential = null;
        // Credential
        if (configuration.getCredentialType().equals(CredentialType.CLIENT_SECRET)) {
            credential = new ClientSecretCredentialBuilder()
                    .tenantId(getConfiguration().getTenantId())
                    .clientId(getConfiguration().getClientId())
                    .clientSecret(getConfiguration().getClientSecret())
                    .build();
        } else if (configuration.getCredentialType().equals(CredentialType.AZURE_IDENTITY)) {
            credential = new DefaultAzureCredentialBuilder().build();
        }

        // Build Client
        localClient = new SecretClientBuilder()
                .vaultUrl(keyVaultUri)
                .credential(credential)
                .buildClient();

        return localClient;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KeyVaultProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    /**
     * The component configurations
     */
    public KeyVaultConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KeyVaultConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The secret Client
     */
    public SecretClient getSecretClient() {
        return secretClient;
    }

    public void setSecretClient(SecretClient secretClient) {
        this.secretClient = secretClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getTenantId())) {
            return configuration.getTenantId();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "keyvault";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getVaultName() != null) {
            return Map.of("vault", configuration.getVaultName());
        }
        return null;
    }
}
